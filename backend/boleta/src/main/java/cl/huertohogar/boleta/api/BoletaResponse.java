package cl.huertohogar.boleta.api;

import cl.huertohogar.boleta.domain.Boleta;
import java.math.BigDecimal;
import java.time.Instant;

public record BoletaResponse(Long id, Long pedidoId, Long clienteId, Instant emitidaEn,
                             BigDecimal total, String moneda, String tipoDocumento) {
    public static BoletaResponse desde(Boleta b) {
        return new BoletaResponse(b.getId(), b.getPedidoId(), b.getClienteId(), b.getEmitidaEn(),
                b.getTotal(), "GALATICOINS", "COMPROBANTE_ACADEMICO_NO_TRIBUTARIO");
    }
}
