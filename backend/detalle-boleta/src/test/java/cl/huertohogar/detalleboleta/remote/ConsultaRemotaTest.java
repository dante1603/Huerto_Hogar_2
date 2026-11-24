package cl.huertohogar.detalleboleta.remote;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ConsultaRemotaTest {
    @Test
    void conservaRutaBearerYRespuesta() {
        var builder = RestClient.builder().baseUrl("http://localhost:8082");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8082/api/v1/pedidos/9"))
            .andExpect(header("Authorization", "Bearer token-prueba"))
            .andRespond(withSuccess("respuesta", MediaType.TEXT_PLAIN));
        assertEquals("respuesta", ConsultaRemota.obtener(builder.build(),
            "/api/v1/pedidos/{id}", 9L, "token-prueba", String.class, "Pedido", "Pedidos"));
        server.verify();
    }

    @Test
    void conservaOcultacionYErroresDeDependencia() {
        for (var estado : new HttpStatus[]{HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND,
                                           HttpStatus.INTERNAL_SERVER_ERROR}) {
            var builder = RestClient.builder().baseUrl("http://localhost:8083");
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://localhost:8083/api/v1/boletas/9"))
                .andRespond(withStatus(estado));
            var ex = assertThrows(ResponseStatusException.class, () -> ConsultaRemota.obtener(
                builder.build(), "/api/v1/boletas/{id}", 9L, "token-prueba", String.class,
                "Boleta", "Boleta"));
            assertEquals(estado == HttpStatus.INTERNAL_SERVER_ERROR
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.NOT_FOUND, ex.getStatusCode());
            server.verify();
        }
    }
}
