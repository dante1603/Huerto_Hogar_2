package cl.huertohogar.boleta;

import cl.huertohogar.boleta.persistence.BoletaRepository;
import cl.huertohogar.boleta.remote.PedidoGateway;
import cl.huertohogar.boleta.remote.PedidoVerificado;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={
    "jwt.secret-base64=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=",
    "jwt.issuer=huertohogar-usuarios"
})
@AutoConfigureMockMvc @ActiveProfiles("demo")
class BoletaApiTest {
    @Autowired MockMvc mvc;
    @Autowired BoletaRepository repository;
    @MockitoBean PedidoGateway gateway;

    @BeforeEach
    void inicializar() {
        repository.deleteAll();
        reset(gateway);
        when(gateway.obtener(anyLong(), anyString())).thenAnswer(inv -> valido(inv.getArgument(0)));
    }

    private static PedidoVerificado valido(Long id) {
        return new PedidoVerificado(id, 42L, "PENDIENTE", BigDecimal.valueOf(240),
            List.of(new PedidoVerificado.Linea(1L, "Cultivo Bruma", 2,
                BigDecimal.valueOf(120), BigDecimal.valueOf(240))));
    }

    private static String jwt(long usuario, String rol) {
        var key = new SecretKeySpec(Base64.getDecoder().decode(
            "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA="), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant ahora = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("huertohogar-usuarios")
            .subject(Long.toString(usuario)).issuedAt(ahora)
            .expiresAt(ahora.plusSeconds(600));
        if (rol != null) claims.claim("rol", rol);
        return encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder emitir(
            long actor, String rol, long pedidoId) {
        return post("/api/v1/boletas")
            .header("Authorization", "Bearer " + jwt(actor, rol))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"pedidoId\":" + pedidoId + "}");
    }

    @Test void emitirEsIdempotenteYNoConfiaEnDatosDelCliente() throws Exception {
        mvc.perform(emitir(42, "CLIENTE", 77))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pedidoId").value(77))
            .andExpect(jsonPath("$.clienteId").value(42))
            .andExpect(jsonPath("$.total").value(240))
            .andExpect(jsonPath("$.moneda").value("GALATICOINS"))
            .andExpect(jsonPath("$.tipoDocumento").value("COMPROBANTE_ACADEMICO_NO_TRIBUTARIO"));
        mvc.perform(emitir(42, "CLIENTE", 77))
            .andExpect(status().isOk()).andExpect(jsonPath("$.pedidoId").value(77));
        verify(gateway, times(1)).obtener(anyLong(), anyString());
        assertEquals(1, repository.count());
    }

    @Test void clientesAjenosNoAccedenNiEmitenEnNombreDeOtros() throws Exception {
        String creado = mvc.perform(emitir(42, "CLIENTE", 10))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = ((Number) JsonPath.read(creado, "$.id")).intValue();
        mvc.perform(get("/api/v1/boletas/{id}", id)
            .header("Authorization", "Bearer " + jwt(43, "CLIENTE")))
            .andExpect(status().isNotFound());
        mvc.perform(emitir(43, "CLIENTE", 10)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/boletas/{id}", id)
            .header("Authorization", "Bearer " + jwt(42, "CLIENTE")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/boletas/{id}", id)
            .header("Authorization", "Bearer " + jwt(100, "VENDEDOR")))
            .andExpect(status().isOk());
    }

    @Test void jwtYRolesProtegenLecturaYEscritura() throws Exception {
        mvc.perform(post("/api/v1/boletas").contentType(MediaType.APPLICATION_JSON)
            .content("{\"pedidoId\":1}")).andExpect(status().isUnauthorized());
        mvc.perform(emitir(99, "VENDEDOR", 1)).andExpect(status().isForbidden());
        mvc.perform(emitir(42, "CLIENTE", 0)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/boletas/1")).andExpect(status().isUnauthorized());
        assertEquals(0, repository.count());
    }

    @Test void noEmitePorIntencionSinConfirmarNiTotalInconsistente() throws Exception {
        when(gateway.obtener(anyLong(), anyString())).thenReturn(
            new PedidoVerificado(8L, 42L, "INICIADO", BigDecimal.valueOf(240),
                valido(8L).productos()));
        mvc.perform(emitir(42, "CLIENTE", 8)).andExpect(status().isConflict());
        when(gateway.obtener(anyLong(), anyString())).thenReturn(
            new PedidoVerificado(8L, 42L, "PENDIENTE", BigDecimal.valueOf(250),
                valido(8L).productos()));
        mvc.perform(emitir(42, "CLIENTE", 8)).andExpect(status().isServiceUnavailable());
        assertEquals(0, repository.count());
    }

    @Test void noEmitePorClienteAjenoNiPedidoFalso() throws Exception {
        mvc.perform(emitir(43, "CLIENTE", 45)).andExpect(status().isForbidden());
        when(gateway.obtener(anyLong(), anyString())).thenReturn(
            valido(999L));
        mvc.perform(emitir(42, "CLIENTE", 45)).andExpect(status().isServiceUnavailable());
        assertEquals(0, repository.count());
    }

    @Test void openApiDocumentaBoleta() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/boletas'].post").exists());
    }

    @Test void jwtFirmadoSinRolNoAutorizaEmision() throws Exception {
        mvc.perform(emitir(42, null, 1)).andExpect(status().isForbidden());
        assertEquals(0, repository.count());
    }
}
