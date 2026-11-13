package cl.huertohogar.catalogo.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedido", uniqueConstraints = @UniqueConstraint(
    name = "uk_pedido_cliente_intento", columnNames = {"cliente_id", "clave_idempotencia"}))
public class Pedido {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private Long clienteId;

    @Column(name = "clave_idempotencia", length = 36, nullable = false, updatable = false)
    private String claveIdempotencia;

    @Column(name = "planeta_destino", length = 40, nullable = false)
    private String planetaDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 32, nullable = false)
    private EstadoPedido estado = EstadoPedido.PENDIENTE;

    @Column(name = "total", precision = 14, scale = 0, nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL,
        orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LineaPedido> lineas = new ArrayList<>();

    protected Pedido() { }

    public Pedido(Long clienteId, String claveIdempotencia, String planetaDestino) {
        this.clienteId = clienteId;
        this.claveIdempotencia = claveIdempotencia;
        this.planetaDestino = planetaDestino;
        this.creadoEn = Instant.now();
    }

    public void agregarLinea(Producto producto, int cantidad) {
        var linea = new LineaPedido(this, producto, cantidad);
        lineas.add(linea);
        total = total.add(linea.getSubtotal());
    }

    public Long getId() { return id; }
    public Long getClienteId() { return clienteId; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public String getPlanetaDestino() { return planetaDestino; }
    public EstadoPedido getEstado() { return estado; }
    public BigDecimal getTotal() { return total; }
    public Instant getCreadoEn() { return creadoEn; }
    public List<LineaPedido> getLineas() { return lineas; }
}
