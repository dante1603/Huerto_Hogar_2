package cl.huertohogar.catalogo.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro propiedad de Productos, NO de Pedidos.
 * La referencia es un UUID generado por el futuro servicio Pedidos.
 */
@Entity
@Table(name = "reserva_stock")
public class ReservaStock {
    @Id
    @Column(name = "referencia", length = 36, nullable = false, updatable = false)
    private String referencia;

    @Column(name = "huella_solicitud", length = 64, nullable = false, updatable = false)
    private String huellaSolicitud;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 16, nullable = false)
    private EstadoReserva estado;

    @Column(name = "total", precision = 14, scale = 0, nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL,
        orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LineaReserva> lineas = new ArrayList<>();

    protected ReservaStock() { }

    public ReservaStock(String referencia, String huellaSolicitud) {
        this.referencia = referencia;
        this.huellaSolicitud = huellaSolicitud;
        this.estado = EstadoReserva.RESERVADA;
        this.creadaEn = Instant.now();
    }

    /** Tombstone: evita reservar stock si la compensacion llegó primero. */
    public static ReservaStock canceladaSinReserva(String referencia) {
        var reserva = new ReservaStock(referencia, "CANCELADA");
        reserva.estado = EstadoReserva.CANCELADA;
        return reserva;
    }

    public void agregarLinea(Producto producto, int cantidad) {
        var linea = new LineaReserva(this, producto, cantidad);
        lineas.add(linea);
        total = total.add(linea.getSubtotal());
    }

    public void cancelar() {
        this.estado = EstadoReserva.CANCELADA;
    }

    public String getReferencia() { return referencia; }
    public String getHuellaSolicitud() { return huellaSolicitud; }
    public EstadoReserva getEstado() { return estado; }
    public BigDecimal getTotal() { return total; }
    public Instant getCreadaEn() { return creadaEn; }
    public List<LineaReserva> getLineas() { return lineas; }
}
