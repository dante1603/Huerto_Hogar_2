package cl.huertohogar.pedidos.service;

import cl.huertohogar.pedidos.remote.ProductoReservaGateway;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Seguridad antes que disponibilidad: una intencion INICIADO abandonada se cancela
 * con tombstone remoto. El proceso desactivado por defecto hasta E2E real.
 */
@Service
public class ReconciliacionExpiradas {
    private final PedidoPersistencia persistencia;
    private final ProductoReservaGateway productos;
    private final boolean habilitada;
    private final long caducidadMinutos;

    public ReconciliacionExpiradas(PedidoPersistencia persistencia, ProductoReservaGateway productos,
            @Value("${reconciliacion.habilitada:false}") boolean habilitada,
            @Value("${reconciliacion.caducidad-minutos:60}") long caducidadMinutos) {
        this.persistencia = persistencia;
        this.productos = productos;
        this.habilitada = habilitada;
        this.caducidadMinutos = caducidadMinutos;
    }

    @Scheduled(fixedDelayString = "${reconciliacion.intervalo-ms:60000}")
    public void programada() {
        if (habilitada)
            revisar(Instant.now().minus(Duration.ofMinutes(Math.max(1, caducidadMinutos))));
    }

    // Invocable por test determinista con corte inyectado. NUNCA lleva un @Transactional.
    public int revisar(Instant corte) {
        int completadas = 0;
        for (Long id : persistencia.candidatas(corte)) {
            String ref = persistencia.iniciarCancelacion(id, corte);
            if (ref == null) continue; // Pedido confirmado/ausente, o aun no caduco.
            try {
                productos.cancelar(ref);
                persistencia.finalizarCancelacion(id, ref);
                completadas++;
            } catch (RuntimeException indeterminada) {
                // Se conserva CANCELANDO: DELETE es idempotente y se repetira despues.
                // No se devuelve stock en la BD de Pedidos ni se confirma PENDIENTE.
            }
        }
        return completadas;
    }
}
