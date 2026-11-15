package cl.huertohogar.pedidos.remote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Snapshot emitido por Productos; Pedidos no acepta precios del navegador. */
public record ReservaRemota(String referencia,String estado,BigDecimal total,Instant creadaEn,
    List<Linea> productos) {
    public record Linea(Long productoId,String nombreProducto,Integer cantidad,
        BigDecimal precioUnitario,BigDecimal subtotal) {}
}