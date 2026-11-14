package cl.huertohogar.catalogo.persistence;

import cl.huertohogar.catalogo.domain.LineaPedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineaPedidoRepository extends JpaRepository<LineaPedido, Long> {
    boolean existsByProductoId(Long productoId);
}
