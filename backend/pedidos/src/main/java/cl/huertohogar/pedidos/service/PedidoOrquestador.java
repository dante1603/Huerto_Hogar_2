package cl.huertohogar.pedidos.service;
import cl.huertohogar.pedidos.api.CrearPedidoRequest;
import cl.huertohogar.pedidos.api.PedidoResponse;
import cl.huertohogar.pedidos.domain.EstadoPedido;
import cl.huertohogar.pedidos.remote.ProductoReservaGateway;
import cl.huertohogar.pedidos.remote.ReservaIndeterminadaException;
import cl.huertohogar.pedidos.remote.ReservaRemota;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
/** Saga con intención local durable. Un timeout no autoriza descontar stock por segunda vez. */
@Service
public class PedidoOrquestador {
    private final PedidoPersistencia persistencia;
    private final ProductoReservaGateway productos;
    public PedidoOrquestador(PedidoPersistencia persistencia,ProductoReservaGateway productos) {
        this.persistencia=persistencia;this.productos=productos;
    }
    public record Resultado(PedidoResponse pedido,boolean nuevo) {}
    public Resultado crear(Long cliente,String clave,CrearPedidoRequest req) {
        var items=SolicitudCanonica.items(req);
        String huella=SolicitudCanonica.huella(req.planetaDestino(),items);
        String ref=UUID.nameUUIDFromBytes(("huertohogar-pedidos:"+cliente+":"+clave)
            .getBytes(StandardCharsets.UTF_8)).toString();
        PedidoPersistencia.Intento intento;
        try {intento=persistencia.iniciar(cliente,clave,ref,huella,req.planetaDestino());}
        catch(DataIntegrityViolationException carrera){
            intento=persistencia.iniciar(cliente,clave,ref,huella,req.planetaDestino());
        }
        if(intento.estado()==EstadoPedido.PENDIENTE)
            return new Resultado(persistencia.confirmado(intento.id()),false);
        if(intento.estado()==EstadoPedido.CANCELANDO)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Intento en compensacion; no crear una nueva reserva con esta clave");
        if(intento.estado()==EstadoPedido.CANCELADO)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Intento expirado y cancelado; use otra Idempotency-Key");
        ReservaRemota snapshot;
        try{snapshot=productos.reservar(intento.referencia(),items);}
        catch(ReservaIndeterminadaException timeout) {
            try{snapshot=productos.consultar(intento.referencia());}
            catch(RuntimeException falla) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Reserva indeterminada: reintentar el mismo Idempotency-Key");
            }
        }
        validar(snapshot,intento.referencia(),items);
        // Commit local termina en el proxy transaccional antes de responder.
        // Ante excepción no compensar a ciegas: el intento durable puede reconciliarse.
        return new Resultado(persistencia.confirmar(intento.id(),snapshot),intento.nuevo());
    }
    private static void validar(ReservaRemota snapshot,String ref,List<CrearPedidoRequest.Item> items) {
        if(snapshot==null||!ref.equals(snapshot.referencia())||
           !"RESERVADA".equals(snapshot.estado())||snapshot.total()==null||
           snapshot.productos()==null||snapshot.productos().size()!=items.size())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Reserva remota incompatible");
        BigDecimal suma=BigDecimal.ZERO;
        for(int i=0;i<items.size();i++){
            var a=items.get(i);var b=snapshot.productos().get(i);
            if(!a.productoId().equals(b.productoId())||!a.cantidad().equals(b.cantidad())||
               b.nombreProducto()==null||b.precioUnitario()==null||b.subtotal()==null||
               b.precioUnitario().signum()<0||b.subtotal().compareTo(
                    b.precioUnitario().multiply(BigDecimal.valueOf(b.cantidad())))!=0)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Detalle de reserva no coincide con la solicitud");
            suma=suma.add(b.subtotal());
        }
        if(suma.compareTo(snapshot.total())!=0||suma.precision()>14)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Total remoto inconsistente");
    }
}