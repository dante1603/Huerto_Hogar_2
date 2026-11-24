package cl.huertohogar.pedidos.service;
import cl.huertohogar.pedidos.api.PedidoResponse;
import cl.huertohogar.pedidos.domain.EstadoPedido;
import cl.huertohogar.pedidos.domain.Pedido;
import cl.huertohogar.pedidos.persistence.PedidoRepository;
import cl.huertohogar.pedidos.remote.ReservaRemota;
import java.util.List;
import java.time.Instant;
import java.util.stream.Stream;
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
        if (p.getEstado() == EstadoPedido.INICIADO) p.confirmar(snapshot);
        else if (p.getEstado() != EstadoPedido.PENDIENTE)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Intento en cancelacion; no puede confirmar una reserva");
        pedidos.flush();
        return PedidoResponse.desde(p);
    }
    // Se consultan referencias, no se mantienen locks SQL durante llamadas HTTP.
    @Transactional(readOnly=true)
    public List<Long> candidatas(Instant corte) {
        return Stream.concat(
                pedidos.findTop20ByEstadoAndCreadoEnBeforeOrderByIdAsc(EstadoPedido.INICIADO, corte)
                    .stream(),
                pedidos.findTop20ByEstadoOrderByIdAsc(EstadoPedido.CANCELANDO).stream())
            .map(Pedido::getId).distinct().toList();
    }

    // Commit de CANCELANDO ANTES de DELETE remoto: impide confirmacion tardia.
    @Transactional
    public String iniciarCancelacion(Long id, Instant corte) {
        Pedido p = pedidos.findBloqueado(id).orElse(null);
        if (p == null) return null;
        if (p.getEstado() == EstadoPedido.CANCELANDO) return p.getReferenciaReserva();
        if (p.getEstado() != EstadoPedido.INICIADO || !p.getCreadoEn().isBefore(corte))
            return null;
        p.iniciarCancelacion();
        pedidos.flush();
        return p.getReferenciaReserva();
    }

    @Transactional
    public void finalizarCancelacion(Long id, String ref) {
        Pedido p = pedidos.findBloqueado(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Intento inexistente"));
        if (!p.getReferenciaReserva().equals(ref))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Referencia ajena");
        if (p.getEstado() == EstadoPedido.CANCELADO) return;
        if (p.getEstado() != EstadoPedido.CANCELANDO)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Intento no cancelable");
        p.finalizarCancelacion();
        pedidos.flush();
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