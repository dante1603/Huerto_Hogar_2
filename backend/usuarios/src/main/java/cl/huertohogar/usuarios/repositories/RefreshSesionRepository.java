package cl.huertohogar.usuarios.repositories;

import cl.huertohogar.usuarios.models.entities.RefreshSesion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSesionRepository extends JpaRepository<RefreshSesion, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSesion s where s.tokenHash = :hash")
    Optional<RefreshSesion> findPorHashBloqueada(@Param("hash") String hash);
}
