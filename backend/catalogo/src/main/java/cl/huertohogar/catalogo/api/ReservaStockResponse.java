package cl.huertohogar.catalogo.api;

import cl.huertohogar.catalogo.domain.ReservaStock;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Snapshot financiero confiable; no acepta precios suministrados por React o Pedidos. */
public record ReservaStockResponse(
    String referencia, String estado, BigDecimal total, Instant creadaEn,
    List<Linea> productos
) {
    public record Linea(
        Long productoId, String nombreProducto, Integer cantidad,
        BigDecimal precioUnitario, BigDecimal subtotal
    ) { }

    public static ReservaStockResponse desde(ReservaStock reserva) {
        return new ReservaStockResponse(reserva.getReferencia(),
            reserva.getEstado().name(), reserva.getTotal(), reserva.getCreadaEn(),
            reserva.getLineas().stream().map(linea -> new Linea(
                linea.getProductoId(), linea.getNombreProducto(), linea.getCantidad(),
                linea.getPrecioUnitario(), linea.getSubtotal())).toList());
    }
}
