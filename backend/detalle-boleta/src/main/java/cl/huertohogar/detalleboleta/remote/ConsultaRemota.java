package cl.huertohogar.detalleboleta.remote;

import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/** Transporte compartido solo dentro de Detalle Boleta; conserva su politica de errores. */
final class ConsultaRemota {
    private ConsultaRemota() {}

    static RestClient crearCliente(String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    static <T> T obtener(RestClient client, String ruta, Long id, String jwt,
                         Class<T> tipo, String recurso, String servicio) {
        try {
            return client.get().uri(ruta, id)
                .headers(h -> h.setBearerAuth(jwt)).retrieve().body(tipo);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404 || ex.getStatusCode().value() == 403)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, recurso + " no accesible");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo verificar " + servicio, ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, servicio + " no disponible", ex);
        }
    }
}
