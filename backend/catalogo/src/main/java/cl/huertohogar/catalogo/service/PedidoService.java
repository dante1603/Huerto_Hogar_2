package cl.huertohogar.catalogo.service;

import cl.huertohogar.catalogo.api.CrearPedidoRequest;
import cl.huertohogar.catalogo.api.PedidoResponse;
import cl.huertohogar.catalogo.api.ResultadoCreacion;
import cl.huertohogar.catalogo.domain.EstadoPedido;
import cl.huertohogar.catalogo.domain.Pedido;
import cl.huertohogar.catalogo.domain.Producto;
import cl.huertohogar.catalogo.persistence.PedidoRepository;
import cl.huertohogar.catalogo.persistence.ProductoRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PedidoService {
    private final PedidoRepository pedidos;
    private final ProductoRepository productos;

    public PedidoService(PedidoRepository pedidos, ProductoRepository productos) {
        this.pedidos = pedidos;
        this.productos = productos;
    }

    @Transactional
    public ResultadoCreacion crear(Long clienteId, String clave, CrearPedidoRequest request) {
        var previo = pedidos.findByClienteIdAndClaveIdempotencia(clienteId, clave);
        if (previo.isPresent()) {
            return new ResultadoCreacion(repeticionValida(previo.get(), request), false);
        }
        List<CrearPedidoRequest.Item> lineas = new ArrayList<>(request.productos());
        lineas.sort(Comparator.comparing(CrearPedidoRequest.Item::productoId));
        for (int i = 1; i < lineas.size(); i++) {
            if (lineas.get(i - 1).productoId().equals(lineas.get(i).productoId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Producto repetido en el pedido");
            }
        }

        // Bloqueos pesimistas en orden fijo: evitan sobreventa y reducen deadlocks.
        var bloqueados = new ArrayList<Producto>();
        for (var linea : lineas) {
            Producto p = productos.findPorIdParaReserva(linea.productoId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Producto no existe: " + linea.productoId()));
            bloqueados.add(p);
        }

        // Tras esperar por bloqueos, otra solicitud podría haber confirmado
        // este mismo intento. Verificar nuevamente ANTES de descontar stock.
        var concurrente = pedidos.findByClienteIdAndClaveIdempotencia(clienteId, clave);
        if (concurrente.isPresent()) {
            return new ResultadoCreacion(repeticionValida(concurrente.get(), request), false);
        }

        // Solo despues de confirmar que el intento no fue registrado por otro hilo:
        for (int i = 0; i < lineas.size(); i++) {
            Producto producto = bloqueados.get(i);
            if (producto.getStock() < lineas.get(i).cantidad()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Inventario insuficiente para " + producto.getNombre());
            }
        }

        var pedido = new Pedido(clienteId, clave, request.planetaDestino());
        for (int i = 0; i < lineas.size(); i++) {
            Producto producto = bloqueados.get(i);
            int cantidad = lineas.get(i).cantidad();
            producto.reservar(cantidad);
            pedido.agregarLinea(producto, cantidad);
        }
        if (pedido.getTotal().precision() > 14) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El total del pedido excede el limite");
        }
        pedidos.saveAndFlush(pedido);
        return new ResultadoCreacion(PedidoResponse.desde(pedido), true);
    }

    @Transactional(readOnly = true)
    public PedidoResponse recuperarIntento(Long clienteId, String clave, CrearPedidoRequest solicitud) {
        var pedido = pedidos.findByClienteIdAndClaveIdempotencia(clienteId, clave)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                "No se pudo confirmar la reserva; vuelve a consultar tus pedidos"));
        return repeticionValida(pedido, solicitud);
    }

    private PedidoResponse repeticionValida(Pedido pedido, CrearPedidoRequest solicitud) {
        if (!pedido.getPlanetaDestino().equals(solicitud.planetaDestino()) ||
            pedido.getLineas().size() != solicitud.productos().size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La clave de idempotencia ya se usó con otro pedido");
        }
        Map<Long, Integer> original = new HashMap<>();
        pedido.getLineas().forEach(l -> original.put(l.getProductoId(), l.getCantidad()));
        for (var item : solicitud.productos()) {
            if (!item.cantidad().equals(original.get(item.productoId()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La clave de idempotencia ya se usó con otro pedido");
            }
        }
        return PedidoResponse.desde(pedido);
    }

    @Transactional(readOnly = true)
    public PedidoResponse detalle(Long pedidoId, Long actorId, String rol) {
        Pedido pedido = pedidos.findById(pedidoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no existe"));
        if ("CLIENTE".equals(rol) && !pedido.getClienteId().equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pedido de otro cliente");
        }
        return PedidoResponse.desde(pedido);
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> mios(Long clienteId) {
        return pedidos.findByClienteIdOrderByIdDesc(clienteId).stream()
            .map(PedidoResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> pendientes() {
        return pedidos.findByEstadoOrderByIdDesc(EstadoPedido.PENDIENTE).stream()
            .map(PedidoResponse::desde).toList();
    }
}
