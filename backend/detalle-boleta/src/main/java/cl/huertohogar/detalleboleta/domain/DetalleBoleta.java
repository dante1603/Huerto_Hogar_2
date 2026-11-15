package cl.huertohogar.detalleboleta.domain;

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
@Table(name = "detalle_boleta",
    uniqueConstraints = @UniqueConstraint(name = "uk_detalle_boleta_linea",
        columnNames = {"id_boleta", "numero_linea"}))
public class DetalleBoleta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Long id;

    @Column(name = "id_boleta", nullable = false, updatable = false)
    private Long boletaId;

    @Column(name = "numero_linea", nullable = false, updatable = false)
    private Integer numeroLinea;

    @Column(name = "id_producto", nullable = false, updatable = false)
    private Long productoId;

    @Column(name = "nombre_producto", nullable = false, updatable = false, length = 255)
    private String nombreProducto;

    @Column(name = "cantidad_producto", nullable = false, updatable = false)
    private Integer cantidadProducto;

    @Column(name = "precio_unitario", nullable = false, updatable = false, precision = 14, scale = 0)
    private BigDecimal precioUnitario;

    @Column(name = "total_venta", nullable = false, updatable = false, precision = 14, scale = 0)
    private BigDecimal totalVenta;

    @Column(name = "fecha_hora_venta", nullable = false, updatable = false)
    private Instant fechaHoraVenta;

    protected DetalleBoleta() {}

    public DetalleBoleta(Long boletaId, int numeroLinea, Long productoId, String nombreProducto,
                         int cantidadProducto, BigDecimal precioUnitario, BigDecimal totalVenta) {
        this.boletaId = boletaId;
        this.numeroLinea = numeroLinea;
        this.productoId = productoId;
        this.nombreProducto = nombreProducto;
        this.cantidadProducto = cantidadProducto;
        this.precioUnitario = precioUnitario;
        this.totalVenta = totalVenta;
        this.fechaHoraVenta = Instant.now();
    }

    public Long getId() { return id; }
    public Long getBoletaId() { return boletaId; }
    public Integer getNumeroLinea() { return numeroLinea; }
    public Long getProductoId() { return productoId; }
    public String getNombreProducto() { return nombreProducto; }
    public Integer getCantidadProducto() { return cantidadProducto; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public BigDecimal getTotalVenta() { return totalVenta; }
    public Instant getFechaHoraVenta() { return fechaHoraVenta; }
}
