package cl.huertohogar.pedidos.remote;
import cl.huertohogar.pedidos.api.CrearPedidoRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/** Interfaz de red: los errores ambiguos no se convierten en falsas reservas fallidas. */
@Component
public class ProductosHttpGateway implements ProductoReservaGateway {
    private final RestClient client;
    private final ServicioPedidosToken token;
    public ProductosHttpGateway(@Value("${servicios.productos.base-url:http://localhost:8080}") String url,ServicioPedidosToken token) {
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.token=token;
    }
    private record ReservaRequest(List<CrearPedidoRequest.Item> productos) {}

    @Override
    public ReservaRemota reservar(String referencia,List<CrearPedidoRequest.Item> productos) {
        try {
            return client.put().uri("/api/v1/interno/reservas/{referencia}",referencia)
                .header("Authorization","Bearer "+token.emitir())
                .body(new ReservaRequest(productos)).retrieve().body(ReservaRemota.class);
        } catch (ResourceAccessException e) {
            throw new ReservaIndeterminadaException(e);
        } catch (RestClientResponseException e) {
            throw mapear(e);
        } catch (RuntimeException e) {
            // Sin status HTTP verificable: falla de transporte, protocolo o parseo.
            // Sin snapshot confiable no confirmamos ni cancelamos a ciegas.
            // Consultar la MISMA referencia antes de declarar fallo.
            throw new ReservaIndeterminadaException(e);
        }
    }
    @Override
    public ReservaRemota consultar(String referencia) {
        try {
            return client.get().uri("/api/v1/interno/reservas/{referencia}",referencia)
                .header("Authorization","Bearer "+token.emitir())
                .retrieve().body(ReservaRemota.class);
        } catch (ResourceAccessException e) {
            throw new ReservaIndeterminadaException(e);
        } catch (RestClientResponseException e) {
            throw mapear(e);
        } catch (RuntimeException e) {
            // Respuesta perdida o transporte ambiguo (sin status HTTP verificable).
            // Consultar la MISMA referencia antes de declarar fallo.
            throw new ReservaIndeterminadaException(e);
        }
    }
    @Override
    public void cancelar(String referencia) {
        try {
            client.delete().uri("/api/v1/interno/reservas/{referencia}", referencia)
                .header("Authorization", "Bearer " + token.emitir())
                .retrieve().toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw new ReservaIndeterminadaException(e);
        } catch (RestClientResponseException e) {
            // Un fallo remoto NO autoriza a marcar CANCELADO.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se ha confirmado la compensacion de stock");
        } catch (RuntimeException e) {
            // Si DELETE pudo ejecutarse, repetimos la misma referencia luego.
            throw new ReservaIndeterminadaException(e);
        }
    }
    private static ResponseStatusException mapear(RestClientResponseException e) {
        int status=e.getStatusCode().value();
        if(status==400 || status==404 || status==409)
            return new ResponseStatusException(HttpStatus.valueOf(status),
                "Productos rechazo la solicitud de reserva");
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Productos no disponible o credencial de servicio invalida");
    }
}