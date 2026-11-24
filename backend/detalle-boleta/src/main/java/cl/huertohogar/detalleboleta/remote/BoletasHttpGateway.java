package cl.huertohogar.detalleboleta.remote;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BoletasHttpGateway implements BoletaGateway {
    private final RestClient client;

    public BoletasHttpGateway(@Value("${servicios.boletas.base-url:http://localhost:8083}") String url) {
        this.client = ConsultaRemota.crearCliente(url);
    }

    @Override
    public BoletaVerificada obtener(Long boletaId, String jwt) {
        return ConsultaRemota.obtener(client, "/api/v1/boletas/{id}", boletaId, jwt,
            BoletaVerificada.class, "Boleta", "Boleta");
    }
}
