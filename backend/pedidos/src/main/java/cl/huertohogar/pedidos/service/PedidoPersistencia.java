package cl.huertohogar.pedidos.service;
import cl.huertohogar.pedidos.api.PedidoResponse;
import cl.huertohogar.pedidos.domain.EstadoPedido;
import cl.huertohogar.pedidos.domain.Pedido;
import cl.huertohogar.pedidos.persistence.PedidoRepository;
import cl.huertohogar.pedidos.remote.ReservaRemota;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class PedidoPersistencia {
    private final PedidoRepository pedidos;
    public PedidoPersistencia(PedidoRepository pedidos){this.pedidos=pedidos;}
    public record Intento(Long id,String referencia,EstadoPedido estado,boolean nuevo) {}
    @Transactional
    public Intento iniciar(Long cliente,String clave,String ref,String huella,String destino){
        var anterior=pedidos.findByClienteIdAndClaveIdempotencia(cliente,clave);
        if(anterior.isPresent()){
            Pedido p=anterior.get();
            if(!p.getHuellaSolicitud().equals(huella))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Clave reutilizada con distinto pedido");
            return new Intento(p.getId(),p.getReferenciaReserva(),p.getEstado(),false);
        }
        Pedido p=pedidos.saveAndFlush(new Pedido(cliente,clave,ref,huella,destino));
        return new Intento(p.getId(),p.getReferenciaReserva(),p.getEstado(),true);
    }
    @Transactional
    public PedidoResponse confirmar(Long id,ReservaRemota snapshot){
        Pedido p=pedidos.findBloqueado(id)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Pedido inexistente"));
        if(p.getEstado()!=EstadoPedido.PENDIENTE)p.confirmar(snapshot);
        pedidos.flush();
        return PedidoResponse.desde(p);
    }
    @Transactional(readOnly=true)
    public PedidoResponse confirmado(Long id){
        Pedido p=pedidos.findById(id)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Pedido inexistente"));
        if(p.getEstado()!=EstadoPedido.PENDIENTE)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Pedido requiere reconciliacion");
        return PedidoResponse.desde(p);
    }
    @Transactional(readOnly=true)
    public PedidoResponse detalle(Long id,Long actor,String rol){
        Pedido p=pedidos.findById(id)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Pedido inexistente"));
        if(p.getEstado()!=EstadoPedido.PENDIENTE)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Pedido no confirmado");
        if("CLIENTE".equals(rol)&&!actor.equals(p.getClienteId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Pedido de otro cliente");
        if(!List.of("CLIENTE","VENDEDOR","ADMINISTRADOR").contains(rol))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Rol invalido");
        return PedidoResponse.desde(p);
    }
    @Transactional(readOnly=true)
    public List<PedidoResponse> mios(Long cliente){
        return pedidos.findByClienteIdAndEstadoOrderByIdDesc(cliente,EstadoPedido.PENDIENTE)
            .stream().map(PedidoResponse::desde).toList();
    }
    @Transactional(readOnly=true)
    public List<PedidoResponse> pendientes(){
        return pedidos.findByEstadoOrderByIdDesc(EstadoPedido.PENDIENTE)
            .stream().map(PedidoResponse::desde).toList();
    }
}