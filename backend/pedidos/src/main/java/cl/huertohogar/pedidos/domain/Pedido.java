package cl.huertohogar.pedidos.domain;
import cl.huertohogar.pedidos.remote.ReservaRemota;
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
@Table(name="pedido",uniqueConstraints=@UniqueConstraint(
  name="uk_pedido_cliente_intento",columnNames={"cliente_id","clave_idempotencia"}))
public class Pedido {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="cliente_id",nullable=false,updatable=false) private Long clienteId;
    @Column(name="clave_idempotencia",nullable=false,length=36,updatable=false) private String claveIdempotencia;
    @Column(name="referencia_reserva",nullable=false,length=36,unique=true,updatable=false) private String referenciaReserva;
    @Column(name="huella_solicitud",nullable=false,length=64,updatable=false) private String huellaSolicitud;
    @Column(name="planeta_destino",nullable=false,length=40,updatable=false) private String planetaDestino;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EstadoPedido estado=EstadoPedido.INICIADO;
    @Column(nullable=false,precision=14,scale=0) private BigDecimal total=BigDecimal.ZERO;
    @Column(name="creado_en",nullable=false,updatable=false) private Instant creadoEn;
    @OneToMany(mappedBy="pedido",cascade=CascadeType.ALL,orphanRemoval=true,fetch=FetchType.LAZY)
    private List<LineaPedido> lineas=new ArrayList<>();
    protected Pedido() {}
    public Pedido(Long clienteId,String clave,String referencia,String huella,String destino) {
        this.clienteId=clienteId;claveIdempotencia=clave;referenciaReserva=referencia;
        huellaSolicitud=huella;planetaDestino=destino;creadoEn=Instant.now();
    }
    public void confirmar(ReservaRemota reserva) {
        if (estado==EstadoPedido.PENDIENTE) return;
        if (!referenciaReserva.equals(reserva.referencia()) || !"RESERVADA".equals(reserva.estado()))
            throw new IllegalStateException("Reserva ajena o cancelada");
        lineas.clear();
        for (var item : reserva.productos()) lineas.add(new LineaPedido(this,item));
        total=reserva.total();
        estado=EstadoPedido.PENDIENTE;
    }
    public Long getId() {return id;}
    public Long getClienteId() {return clienteId;}
    public String getClaveIdempotencia() {return claveIdempotencia;}
    public String getReferenciaReserva() {return referenciaReserva;}
    public String getHuellaSolicitud() {return huellaSolicitud;}
    public String getPlanetaDestino() {return planetaDestino;}
    public EstadoPedido getEstado() {return estado;}
    public BigDecimal getTotal() {return total;}
    public Instant getCreadoEn() {return creadoEn;}
    public List<LineaPedido> getLineas() {return lineas;}
}