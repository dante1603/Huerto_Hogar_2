package cl.huertohogar.detalleboleta.api;

import cl.huertohogar.detalleboleta.domain.DetalleBoleta;
import java.math.BigDecimal;
import java.time.Instant;

public record DetalleResponse(Long id, Long boletaId, Integer numeroLinea, Long productoId,
    String nombreProducto, Integer cantidadProducto, BigDecimal precioUnitario,
    BigDecimal totalVenta, Instant fechaHoraVenta) {

    public static DetalleResponse desde(DetalleBoleta d) {
        return new DetalleResponse(d.getId(), d.getBoletaId(), d.getNumeroLinea(),
            d.getProductoId(), d.getNombreProducto(), d.getCantidadProducto(),
            d.getPrecioUnitario(), d.getTotalVenta(), d.getFechaHoraVenta());
    }
}
