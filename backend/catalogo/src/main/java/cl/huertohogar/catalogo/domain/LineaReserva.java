package cl.huertohogar.catalogo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Copia inmutable del precio y nombre aceptados al reservar inventario. */
@Entity
@Table(name = "linea_reserva")
public class LineaReserva {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_referencia", nullable = false, updatable = false)
    private ReservaStock reserva;

    @Column(name = "producto_id", nullable = false, updatable = false)
    private Long productoId;

    @Column(name = "nombre_producto", length = 120, nullable = false, updatable = false)
    private String nombreProducto;

    @Column(name = "cantidad", nullable = false, updatable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", precision = 12, scale = 0,
        nullable = false, updatable = false)
    private BigDecimal precioUnitario;

    @Column(name = "subtotal", precision = 14, scale = 0, nullable = false, updatable = false)
    private BigDecimal subtotal;

    protected LineaReserva() { }

    LineaReserva(ReservaStock reserva, Producto producto, int cantidad) {
        this.reserva = reserva;
        this.productoId = producto.getId();
        this.nombreProducto = producto.getNombre();
        this.cantidad = cantidad;
        this.precioUnitario = producto.getPrecio();
        this.subtotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public Long getId() { return id; }
    public Long getProductoId() { return productoId; }
    public String getNombreProducto() { return nombreProducto; }
    public Integer getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public BigDecimal getSubtotal() { return subtotal; }
}
