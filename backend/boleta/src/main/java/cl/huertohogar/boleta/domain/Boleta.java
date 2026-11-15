package cl.huertohogar.boleta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "boleta", uniqueConstraints = @UniqueConstraint(name = "uk_boleta_pedido", columnNames = "pedido_id"))
public class Boleta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_boleta")
    private Long id;

    @Column(name = "pedido_id", nullable = false, updatable = false, unique = true)
    private Long pedidoId;

    @Column(name = "id_usuario_compra", nullable = false, updatable = false)
    private Long clienteId;

    @Column(name = "fecha_hora_boleta", nullable = false, updatable = false)
    private Instant emitidaEn;

    @Column(name = "total_venta", nullable = false, updatable = false, precision = 14, scale = 0)
    private BigDecimal total;

    protected Boleta() {}

    public Boleta(Long pedidoId, Long clienteId, BigDecimal total) {
        this.pedidoId = pedidoId;
        this.clienteId = clienteId;
        this.total = total;
        this.emitidaEn = Instant.now();
    }

    public Long getId() { return id; }
    public Long getPedidoId() { return pedidoId; }
    public Long getClienteId() { return clienteId; }
    public Instant getEmitidaEn() { return emitidaEn; }
    public BigDecimal getTotal() { return total; }
}
