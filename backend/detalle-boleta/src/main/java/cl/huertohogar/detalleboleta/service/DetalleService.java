package cl.huertohogar.detalleboleta.service;

import cl.huertohogar.detalleboleta.api.DetalleResponse;
import cl.huertohogar.detalleboleta.domain.DetalleBoleta;
import cl.huertohogar.detalleboleta.persistence.DetalleRepository;
import cl.huertohogar.detalleboleta.remote.BoletaGateway;
import cl.huertohogar.detalleboleta.remote.BoletaVerificada;
import cl.huertohogar.detalleboleta.remote.PedidoGateway;
import cl.huertohogar.detalleboleta.remote.PedidoVerificado;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DetalleService {
    private static final String TIPO = "COMPROBANTE_ACADEMICO_NO_TRIBUTARIO";
    private final DetalleRepository detalles;
    private final DetallePersistencia persistencia;
    private final BoletaGateway boletas;
    private final PedidoGateway pedidos;

    public DetalleService(DetalleRepository detalles, DetallePersistencia persistencia,
                          BoletaGateway boletas, PedidoGateway pedidos) {
        this.detalles = detalles;
        this.persistencia = persistencia;
        this.boletas = boletas;
        this.pedidos = pedidos;
    }

    public record Generacion(List<DetalleResponse> lineas, boolean nuevo) {}

    public Generacion generar(Long boletaId, Jwt jwt) {
        BoletaVerificada boleta = verificarBoleta(boletaId, jwt);
        var existentes = detalles.findByBoletaIdOrderByNumeroLineaAsc(boletaId);
        if (!existentes.isEmpty()) return new Generacion(validarGuardadas(existentes, boleta), false);

        // Única fuente de productos, cantidades y montos: snapshot propietario de Pedidos.
        PedidoVerificado pedido = pedidos.obtener(boleta.pedidoId(), jwt.getTokenValue());
        List<DetalleBoleta> lineas = construirLineas(boleta, pedido);
        try {
            // Una sola transacción JPA; UNIQUE(id_boleta, numero_linea) resuelve concurrencia.
            persistencia.guardarTodas(lineas);
            return new Generacion(validarGuardadas(
                detalles.findByBoletaIdOrderByNumeroLineaAsc(boletaId), boleta), true);
        } catch (DataIntegrityViolationException ex) {
            // Reintento simultáneo: la transacción perdedora ya hizo rollback.
            var ganadoras = detalles.findByBoletaIdOrderByNumeroLineaAsc(boletaId);
            if (ganadoras.isEmpty())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto al guardar detalles", ex);
            return new Generacion(validarGuardadas(ganadoras, boleta), false);
        }
    }

    public List<DetalleResponse> porBoleta(Long boletaId, Jwt jwt) {
        BoletaVerificada boleta = verificarBoleta(boletaId, jwt);
        return validarGuardadas(detalles.findByBoletaIdOrderByNumeroLineaAsc(boletaId), boleta);
    }

    public DetalleResponse porId(Long id, Jwt jwt) {
        var detalle = detalles.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Detalle inexistente"));
        BoletaVerificada boleta = verificarBoleta(detalle.getBoletaId(), jwt);
        validarGuardadas(detalles.findByBoletaIdOrderByNumeroLineaAsc(boleta.id()), boleta);
        return DetalleResponse.desde(detalle);
    }

    private BoletaVerificada verificarBoleta(Long id, Jwt jwt) {
        Long actor;
        try { actor = Long.valueOf(jwt.getSubject()); }
        catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT sin identidad valida");
        }
        BoletaVerificada boleta = boletas.obtener(id, jwt.getTokenValue());
        if (boleta == null || !Objects.equals(id, boleta.id())
                || boleta.pedidoId() == null || boleta.pedidoId() <= 0
                || boleta.clienteId() == null || boleta.clienteId() <= 0
                || boleta.total() == null || boleta.total().signum() < 0
                || !entero(boleta.total()) || !"GALATICOINS".equals(boleta.moneda())
                || !TIPO.equals(boleta.tipoDocumento())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Boleta remota inconsistente");
        }
        if ("CLIENTE".equals(jwt.getClaimAsString("rol"))
                && !Objects.equals(actor, boleta.clienteId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Boleta no accesible");
        return boleta;
    }

    private List<DetalleBoleta> construirLineas(BoletaVerificada boleta, PedidoVerificado pedido) {
        if (pedido == null || !Objects.equals(pedido.id(), boleta.pedidoId())
                || !Objects.equals(pedido.clienteId(), boleta.clienteId())
                || !"PENDIENTE".equals(pedido.estado())
                || pedido.total() == null || !entero(pedido.total())
                || pedido.total().compareTo(boleta.total()) != 0
                || pedido.productos() == null || pedido.productos().isEmpty()
                || pedido.productos().size() > 500) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Pedido remoto incompleto o inconsistente con Boleta");
        }

        BigDecimal suma = BigDecimal.ZERO;
        var lineas = new ArrayList<DetalleBoleta>();
        int indice = 1;
        for (PedidoVerificado.Linea linea : pedido.productos()) {
            if (linea == null || linea.productoId() == null || linea.productoId() <= 0
                || linea.nombreProducto() == null || linea.nombreProducto().isBlank()
                || linea.nombreProducto().length() > 255
                || linea.cantidad() == null || linea.cantidad() <= 0
                || linea.precioUnitario() == null || linea.precioUnitario().signum() < 0
                || !entero(linea.precioUnitario())
                || linea.subtotal() == null || linea.subtotal().signum() < 0
                || !entero(linea.subtotal())
                || linea.subtotal().compareTo(linea.precioUnitario()
                        .multiply(BigDecimal.valueOf(linea.cantidad()))) != 0) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Linea de pedido sin snapshot valido");
            }
            suma = suma.add(linea.subtotal());
            lineas.add(new DetalleBoleta(boleta.id(), indice++, linea.productoId(),
                linea.nombreProducto(), linea.cantidad(), linea.precioUnitario(), linea.subtotal()));
        }
        if (suma.compareTo(boleta.total()) != 0)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Total de lineas no coincide con Boleta");
        return lineas;
    }

    private List<DetalleResponse> validarGuardadas(List<DetalleBoleta> lineas, BoletaVerificada boleta) {
        if (lineas.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Detalle aun no generado");
        BigDecimal suma = BigDecimal.ZERO;
        int numero = 1;
        for (var linea : lineas) {
            if (!Objects.equals(linea.getBoletaId(), boleta.id())
                || !Objects.equals(linea.getNumeroLinea(), numero++)
                || linea.getCantidadProducto() == null || linea.getCantidadProducto() <= 0
                || linea.getPrecioUnitario() == null || linea.getTotalVenta() == null
                || linea.getTotalVenta().compareTo(linea.getPrecioUnitario()
                        .multiply(BigDecimal.valueOf(linea.getCantidadProducto()))) != 0) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Detalle almacenado inconsistente");
            }
            suma = suma.add(linea.getTotalVenta());
        }
        if (suma.compareTo(boleta.total()) != 0)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Detalle almacenado no cuadra con Boleta");
        return lineas.stream().map(DetalleResponse::desde).toList();
    }

    private static boolean entero(BigDecimal valor) {
        return valor.stripTrailingZeros().scale() <= 0;
    }
}
