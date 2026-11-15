package cl.huertohogar.catalogo.persistence;

import cl.huertohogar.catalogo.domain.ReservaStock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaStockRepository extends JpaRepository<ReservaStock, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservaStock r where r.referencia = :referencia")
    Optional<ReservaStock> findBloqueada(@Param("referencia") String referencia);
}
