package cl.huertohogar.boleta.remote;

import java.math.BigDecimal;
import java.util.List;

public record PedidoVerificado(Long id, Long clienteId, String estado, BigDecimal total,
                               List<Linea> productos) {
    public record Linea(Long productoId, String nombreProducto, Integer cantidad,
                        BigDecimal precioUnitario, BigDecimal subtotal) {}
}
