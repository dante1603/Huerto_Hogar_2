package cl.huertohogar.detalleboleta.remote;
import java.math.BigDecimal;

public record BoletaVerificada(Long id, Long pedidoId, Long clienteId,
    BigDecimal total, String moneda, String tipoDocumento) {}
