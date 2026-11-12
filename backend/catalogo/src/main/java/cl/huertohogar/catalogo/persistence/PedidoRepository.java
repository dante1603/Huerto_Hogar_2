package cl.huertohogar.catalogo.persistence;

import cl.huertohogar.catalogo.domain.EstadoPedido;
import cl.huertohogar.catalogo.domain.Pedido;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    Optional<Pedido> findByClienteIdAndClaveIdempotencia(Long clienteId, String claveIdempotencia);
    List<Pedido> findByClienteIdOrderByIdDesc(Long clienteId);
    List<Pedido> findByEstadoOrderByIdDesc(EstadoPedido estado);
}
