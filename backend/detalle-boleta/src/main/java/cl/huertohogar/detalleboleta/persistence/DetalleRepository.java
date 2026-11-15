package cl.huertohogar.detalleboleta.persistence;
import cl.huertohogar.detalleboleta.domain.DetalleBoleta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetalleRepository extends JpaRepository<DetalleBoleta, Long> {
    List<DetalleBoleta> findByBoletaIdOrderByNumeroLineaAsc(Long boletaId);
}
