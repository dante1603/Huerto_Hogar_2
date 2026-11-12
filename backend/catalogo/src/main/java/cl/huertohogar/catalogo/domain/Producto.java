package cl.huertohogar.catalogo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

@Entity
@Table(name = "producto", uniqueConstraints = {
    @UniqueConstraint(name = "uk_producto_sku", columnNames = "sku")
})
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku", nullable = false, length = 40)
    private String sku;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "descripcion", nullable = false, length = 500)
    private String descripcion;

    @Column(name = "precio", nullable = false, precision = 12, scale = 0)
    private BigDecimal precio;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Column(name = "planeta_origen", nullable = false, length = 60)
    private String planetaOrigen;

    protected Producto() { }

    public Producto(String sku, String nombre, String descripcion,
                    BigDecimal precio, Integer stock, String planetaOrigen) {
        this.sku = sku;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.stock = stock;
        this.planetaOrigen = planetaOrigen;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getPrecio() { return precio; }
    public Integer getStock() { return stock; }
    public String getPlanetaOrigen() { return planetaOrigen; }
}
