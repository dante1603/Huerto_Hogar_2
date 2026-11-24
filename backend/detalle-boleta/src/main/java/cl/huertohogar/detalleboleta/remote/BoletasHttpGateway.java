package cl.huertohogar.detalleboleta.remote;

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
public class BoletasHttpGateway implements BoletaGateway {
    private final RestClient client;

    public BoletasHttpGateway(@Value("${servicios.boletas.base-url:http://localhost:8083}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Override
    public BoletaVerificada obtener(Long boletaId, String jwt) {
        try {
            return client.get().uri("/api/v1/boletas/{id}", boletaId)
                .headers(h -> h.setBearerAuth(jwt)).retrieve().body(BoletaVerificada.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404 || ex.getStatusCode().value() == 403)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Boleta no accesible");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo verificar Boleta", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Boleta no disponible", ex);
        }
    }
}
