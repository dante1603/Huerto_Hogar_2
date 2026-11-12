package cl.huertohogar.catalogo.api;

import cl.huertohogar.catalogo.domain.Pedido;
import cl.huertohogar.catalogo.domain.LineaPedido;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PedidoResponse(
    Long id,
    Long clienteId,
    String planetaDestino,
    String estado,
    Instant creadoEn,
    BigDecimal total,
    List<Item> productos
) {
    public record Item(
        Long productoId,
        String nombreProducto,
        int cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal
    ) {
        static Item desde(LineaPedido linea) {
            return new Item(linea.getProductoId(), linea.getNombreProducto(),
                linea.getCantidad(), linea.getPrecioUnitario(), linea.getSubtotal());
        }
    }

    public static PedidoResponse desde(Pedido pedido) {
        return new PedidoResponse(pedido.getId(), pedido.getClienteId(),
            pedido.getPlanetaDestino(), pedido.getEstado().name(),
            pedido.getCreadoEn(), pedido.getTotal(),
            pedido.getLineas().stream().map(Item::desde).toList());
    }
}
