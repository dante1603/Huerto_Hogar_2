package cl.huertohogar.boleta.service;

import cl.huertohogar.boleta.api.BoletaResponse;
import cl.huertohogar.boleta.domain.Boleta;
import cl.huertohogar.boleta.persistence.BoletaRepository;
import cl.huertohogar.boleta.remote.PedidoGateway;
import cl.huertohogar.boleta.remote.PedidoVerificado;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BoletaService {
    private final BoletaRepository repository;
    private final PedidoGateway pedidos;

    public BoletaService(BoletaRepository repository, PedidoGateway pedidos) {
        this.repository = repository;
        this.pedidos = pedidos;
    }

    public record Emision(BoletaResponse boleta, boolean nueva) {}

    public Emision emitir(Long pedidoId, Jwt jwt) {
        Long actor = actor(jwt);
        var existente = repository.findByPedidoId(pedidoId);
        if (existente.isPresent()) return new Emision(autorizar(existente.get(), actor, "CLIENTE"), false);

        PedidoVerificado pedido = pedidos.obtener(pedidoId, jwt.getTokenValue());
        validar(pedidoId, actor, pedido);

        try {
            Boleta guardada = repository.saveAndFlush(new Boleta(pedidoId, actor, pedido.total()));
            return new Emision(BoletaResponse.desde(guardada), true);
        } catch (DataIntegrityViolationException ex) {
            // UNIQUE(pedido_id) resuelve reintentos concurrentes sin emitir dos comprobantes.
            Boleta guardada = repository.findByPedidoId(pedidoId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "Conflicto concurrente al registrar comprobante"));
            return new Emision(autorizar(guardada, actor, "CLIENTE"), false);
        }
    }

    public BoletaResponse obtener(Long id, Jwt jwt) {
        var boleta = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Boleta inexistente"));
        return autorizar(boleta, actor(jwt), jwt.getClaimAsString("rol"));
    }

    private static BoletaResponse autorizar(Boleta boleta, Long actor, String rol) {
        if ("CLIENTE".equals(rol) && !Objects.equals(boleta.getClienteId(), actor))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Boleta inexistente");
        return BoletaResponse.desde(boleta);
    }

    private static Long actor(Jwt jwt) {
        try { return Long.valueOf(jwt.getSubject()); }
        catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT sin identidad valida");
        }
    }

    private static void validar(Long pedidoId, Long actor, PedidoVerificado pedido) {
        if (pedido == null || !Objects.equals(pedido.id(), pedidoId))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Respuesta de Pedidos inconsistente");
        if (!Objects.equals(pedido.clienteId(), actor))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pedido de otro cliente");
        if (!"PENDIENTE".equals(pedido.estado()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo pedidos confirmados pueden generar comprobante");
        if (pedido.total() == null || pedido.total().signum() < 0 ||
                pedido.productos() == null || pedido.productos().isEmpty())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Snapshot incompleto de Pedidos");

        BigDecimal suma = BigDecimal.ZERO;
        for (PedidoVerificado.Linea l : pedido.productos()) {
            if (l == null || l.productoId() == null || l.cantidad() == null ||
                    l.cantidad() <= 0 || l.precioUnitario() == null || l.precioUnitario().signum() < 0 ||
                    l.subtotal() == null || l.subtotal().compareTo(l.precioUnitario()
                    .multiply(BigDecimal.valueOf(l.cantidad()))) != 0)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Linea de pedido inconsistente");
            suma = suma.add(l.subtotal());
        }
        if (suma.compareTo(pedido.total()) != 0)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Total de pedido inconsistente");
    }
}
