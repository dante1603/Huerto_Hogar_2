package cl.huertohogar.detalleboleta.service;

import cl.huertohogar.detalleboleta.domain.DetalleBoleta;
import cl.huertohogar.detalleboleta.persistence.DetalleRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DetallePersistencia {
    private final DetalleRepository repository;
    public DetallePersistencia(DetalleRepository repository) { this.repository = repository; }

    // La lista completa se confirma o se revierte; jamás queda una boleta a medio detalle.
    @Transactional
    public void guardarTodas(List<DetalleBoleta> lineas) {
        repository.saveAllAndFlush(lineas);
    }
}
