package cl.huertohogar.pedidos.persistence;
import cl.huertohogar.pedidos.domain.EstadoPedido;
import cl.huertohogar.pedidos.domain.Pedido;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido,Long> {
    Optional<Pedido> findByClienteIdAndClaveIdempotencia(Long clienteId,String claveIdempotencia);
    List<Pedido> findByClienteIdAndEstadoOrderByIdDesc(Long clienteId,EstadoPedido estado);
    List<Pedido> findByEstadoOrderByIdDesc(EstadoPedido estado);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id=:id")
    Optional<Pedido> findBloqueado(@Param("id") Long id);
}