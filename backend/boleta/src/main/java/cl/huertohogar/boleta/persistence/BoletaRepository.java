package cl.huertohogar.boleta.persistence;

import cl.huertohogar.boleta.domain.Boleta;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoletaRepository extends JpaRepository<Boleta, Long> {
    Optional<Boleta> findByPedidoId(Long pedidoId);
}
