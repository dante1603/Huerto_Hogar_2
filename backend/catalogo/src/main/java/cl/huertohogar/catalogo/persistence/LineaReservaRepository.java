package cl.huertohogar.catalogo.persistence;

import cl.huertohogar.catalogo.domain.EstadoReserva;
import cl.huertohogar.catalogo.domain.LineaReserva;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineaReservaRepository extends JpaRepository<LineaReserva, Long> {
    boolean existsByProductoIdAndReserva_Estado(Long productoId, EstadoReserva estado);
}
