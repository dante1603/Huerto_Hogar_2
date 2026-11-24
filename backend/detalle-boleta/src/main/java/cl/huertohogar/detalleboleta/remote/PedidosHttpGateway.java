package cl.huertohogar.detalleboleta.remote;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PedidosHttpGateway implements PedidoGateway {
    private final RestClient client;

    public PedidosHttpGateway(@Value("${servicios.pedidos.base-url:http://localhost:8082}") String url) {
        this.client = ConsultaRemota.crearCliente(url);
    }

    @Override
    public PedidoVerificado obtener(Long pedidoId, String jwt) {
        return ConsultaRemota.obtener(client, "/api/v1/pedidos/{id}", pedidoId, jwt,
            PedidoVerificado.class, "Pedido", "Pedidos");
    }
}
