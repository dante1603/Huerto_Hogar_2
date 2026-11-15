package cl.huertohogar.pedidos.remote;
import cl.huertohogar.pedidos.api.CrearPedidoRequest;
import java.util.List;

public interface ProductoReservaGateway {
    ReservaRemota reservar(String referencia,List<CrearPedidoRequest.Item> productos);
    ReservaRemota consultar(String referencia);
}