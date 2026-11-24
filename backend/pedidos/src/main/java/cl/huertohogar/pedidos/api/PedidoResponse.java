package cl.huertohogar.pedidos.api;
import cl.huertohogar.pedidos.domain.Pedido;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PedidoResponse(Long id, Long clienteId, String planetaDestino, String estado,
    Instant creadoEn, BigDecimal total,List<Item> productos) {
    public record Item(Long productoId,String nombreProducto,Integer cantidad,
        BigDecimal precioUnitario,BigDecimal subtotal) {}
    public static PedidoResponse desde(Pedido pedido) {
        return new PedidoResponse(pedido.getId(),pedido.getClienteId(),pedido.getPlanetaDestino(),
            pedido.getEstado().name(),pedido.getCreadoEn(),pedido.getTotal(),
            pedido.getLineas().stream().map(l -> new Item(l.getProductoId(),l.getNombreProducto(),
                l.getCantidad(),l.getPrecioUnitario(),l.getSubtotal())).toList());
    }
}