package cl.huertohogar.pedidos.domain;
import cl.huertohogar.pedidos.remote.ReservaRemota;
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

@Entity
@Table(name="linea_pedido")
public class LineaPedido {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="pedido_id",nullable=false) private Pedido pedido;
    @Column(name="producto_id",nullable=false,updatable=false) private Long productoId;
    @Column(name="nombre_producto",nullable=false,length=120,updatable=false) private String nombreProducto;
    @Column(nullable=false,updatable=false) private Integer cantidad;
    @Column(name="precio_unitario",nullable=false,precision=12,scale=0,updatable=false) private BigDecimal precioUnitario;
    @Column(nullable=false,precision=14,scale=0,updatable=false) private BigDecimal subtotal;
    protected LineaPedido() {}
    public LineaPedido(Pedido pedido, ReservaRemota.Linea linea) {
        this.pedido=pedido;
        productoId=linea.productoId();
        nombreProducto=linea.nombreProducto();
        cantidad=linea.cantidad();
        precioUnitario=linea.precioUnitario();
        subtotal=linea.subtotal();
    }
    public Long getProductoId() { return productoId; }
    public String getNombreProducto() { return nombreProducto; }
    public Integer getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public BigDecimal getSubtotal() { return subtotal; }
}