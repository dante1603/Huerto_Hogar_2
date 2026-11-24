package cl.huertohogar.boleta.remote;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PedidosHttpGateway implements PedidoGateway {
    private final RestClient client;

    public PedidosHttpGateway(@Value("${servicios.pedidos.base-url:http://localhost:8082}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public PedidoVerificado obtener(Long pedidoId, String jwtCliente) {
        try {
            return client.get().uri("/api/v1/pedidos/{id}", pedidoId)
                    .headers(h -> h.setBearerAuth(jwtCliente))
                    .retrieve().body(PedidoVerificado.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido inexistente o sin confirmar");
            if (e.getStatusCode().value() == 403)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pedido no accesible");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo verificar el pedido con su servicio propietario", e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Servicio Pedidos no disponible", e);
        }
    }
}
