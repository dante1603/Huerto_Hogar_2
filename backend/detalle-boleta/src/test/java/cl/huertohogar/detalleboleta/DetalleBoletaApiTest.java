package cl.huertohogar.detalleboleta;

import cl.huertohogar.detalleboleta.persistence.DetalleRepository;
import cl.huertohogar.detalleboleta.remote.BoletaGateway;
import cl.huertohogar.detalleboleta.remote.BoletaVerificada;
import cl.huertohogar.detalleboleta.remote.PedidoGateway;
import cl.huertohogar.detalleboleta.remote.PedidoVerificado;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={
    "jwt.secret-base64=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=",
    "jwt.issuer=huertohogar-usuarios"
})
@AutoConfigureMockMvc @ActiveProfiles("demo")
class DetalleBoletaApiTest {
    @Autowired MockMvc mvc;
    @Autowired DetalleRepository repository;
    @MockitoBean BoletaGateway boletas;
    @MockitoBean PedidoGateway pedidos;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
        reset(boletas, pedidos);
        when(boletas.obtener(anyLong(), anyString()))
            .thenAnswer(inv -> boleta(inv.getArgument(0), 42L, 240));
        when(pedidos.obtener(anyLong(), anyString()))
            .thenAnswer(inv -> pedido(inv.getArgument(0), "PENDIENTE",
                BigDecimal.valueOf(240), dosLineas()));
    }

    private static BoletaVerificada boleta(Long id, Long cliente, long total) {
        return new BoletaVerificada(id, 80L, cliente, BigDecimal.valueOf(total),
            "GALATICOINS", "COMPROBANTE_ACADEMICO_NO_TRIBUTARIO");
    }

    private static List<PedidoVerificado.Linea> dosLineas() {
        return List.of(
            new PedidoVerificado.Linea(1L, "Tuberculo", 2, BigDecimal.valueOf(100),
                BigDecimal.valueOf(200)),
            new PedidoVerificado.Linea(2L, "Hongo", 1, BigDecimal.valueOf(40),
                BigDecimal.valueOf(40)));
    }

    private static PedidoVerificado pedido(Long id, String estado, BigDecimal total,
                                          List<PedidoVerificado.Linea> lineas) {
        return new PedidoVerificado(id, 42L, estado, total, lineas);
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder generar(
            long usuario, String rol, long boletaId) {
        return post("/api/v1/detalles-boleta")
            .header("Authorization", "Bearer " + jwt(usuario, rol))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"boletaId\":" + boletaId + "}");
    }

    @Test void generaDesdePedidoYConservaImportesVerificados() throws Exception {
        mvc.perform(generar(42, "CLIENTE", 9))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/detalles-boleta/boleta/9"))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].boletaId").value(9))
            .andExpect(jsonPath("$[0].numeroLinea").value(1))
            .andExpect(jsonPath("$[0].productoId").value(1))
            .andExpect(jsonPath("$[0].cantidadProducto").value(2))
            .andExpect(jsonPath("$[0].precioUnitario").value(100))
            .andExpect(jsonPath("$[0].totalVenta").value(200))
            .andExpect(jsonPath("$[1].totalVenta").value(40));
        assertEquals(2, repository.count());
    }

    @Test void reintentarNoCreaFilasNiConsultaPedidoDosVeces() throws Exception {
        mvc.perform(generar(42, "CLIENTE", 9)).andExpect(status().isCreated());
        mvc.perform(generar(42, "CLIENTE", 9))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        assertEquals(2, repository.count());
        verify(pedidos, times(1)).obtener(anyLong(), anyString());
    }

    @Test void lecturaSoloPropietarioOStaff() throws Exception {
        String body = mvc.perform(generar(42, "CLIENTE", 9))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$[0].id")).longValue();
        mvc.perform(get("/api/v1/detalles-boleta/boleta/9")
            .header("Authorization", "Bearer " + jwt(42, "CLIENTE")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/detalles-boleta/{id}", id)
            .header("Authorization", "Bearer " + jwt(900, "VENDEDOR")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.productoId").value(1));
        mvc.perform(get("/api/v1/detalles-boleta/boleta/9")
            .header("Authorization", "Bearer " + jwt(43, "CLIENTE")))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/detalles-boleta/{id}", id)
            .header("Authorization", "Bearer " + jwt(43, "CLIENTE")))
            .andExpect(status().isNotFound());
    }

    @Test void jwtYRolProhibenEscriturasNoAutorizadas() throws Exception {
        mvc.perform(post("/api/v1/detalles-boleta")
            .contentType(MediaType.APPLICATION_JSON).content("{\"boletaId\":9}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(generar(900, "VENDEDOR", 9)).andExpect(status().isForbidden());
        mvc.perform(generar(42, "CLIENTE", 0)).andExpect(status().isBadRequest());
        mvc.perform(generar(43, "CLIENTE", 9)).andExpect(status().isNotFound());
        assertEquals(0, repository.count());
    }

    @Test void noAceptaPedidoSinConfirmarNiTotalDistinto() throws Exception {
        when(pedidos.obtener(anyLong(), anyString()))
            .thenReturn(pedido(80L, "INICIADO", BigDecimal.valueOf(240), dosLineas()));
        mvc.perform(generar(42, "CLIENTE", 9)).andExpect(status().isServiceUnavailable());
        when(pedidos.obtener(anyLong(), anyString()))
            .thenReturn(pedido(80L, "PENDIENTE", BigDecimal.valueOf(241), dosLineas()));
        mvc.perform(generar(42, "CLIENTE", 9)).andExpect(status().isServiceUnavailable());
        assertEquals(0, repository.count());
    }

    @Test void noGuardaNiUnaLineaCuandoHaySnapshotCorrupto() throws Exception {
        var mala = List.of(
            dosLineas().get(0),
            new PedidoVerificado.Linea(2L, "Hongo", 1, BigDecimal.valueOf(40),
                BigDecimal.valueOf(41)));
        when(pedidos.obtener(anyLong(), anyString()))
            .thenReturn(pedido(80L, "PENDIENTE", BigDecimal.valueOf(240), mala));
        mvc.perform(generar(42, "CLIENTE", 9)).andExpect(status().isServiceUnavailable());
        assertEquals(0, repository.count());
    }

    @Test void rechazaIdentidadesYDocumentosInconsistentes() throws Exception {
        when(boletas.obtener(anyLong(), anyString())).thenReturn(
            new BoletaVerificada(5L, 80L, 42L, BigDecimal.valueOf(240), "GALATICOINS",
                "COMPROBANTE_ACADEMICO_NO_TRIBUTARIO"));
        mvc.perform(generar(42, "CLIENTE", 9)).andExpect(status().isServiceUnavailable());
        when(boletas.obtener(anyLong(), anyString())).thenReturn(
            new BoletaVerificada(9L, 80L, 42L, BigDecimal.valueOf(240), "CLP",
                "COMPROBANTE_ACADEMICO_NO_TRIBUTARIO"));
        mvc.perform(generar(42, "CLIENTE", 9)).andExpect(status().isServiceUnavailable());
        assertEquals(0, repository.count());
    }

    @Test void consultaSinDetalleNoFingeExitoYSwaggerExiste() throws Exception {
        mvc.perform(get("/api/v1/detalles-boleta/boleta/9")
            .header("Authorization", "Bearer " + jwt(42, "CLIENTE")))
            .andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/detalles-boleta'].post").exists());
    }

    @Test void payloadExtraNoPuedeModificarImportes() throws Exception {
        mvc.perform(post("/api/v1/detalles-boleta")
            .header("Authorization", "Bearer " + jwt(42, "CLIENTE"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"boletaId\":9,\"totalVenta\":99999,\"cantidadProducto\":900}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].totalVenta").value(200));
        assertEquals(2, repository.count());
    }

    @Test void jwtFirmadoSinRolNoAutorizaDetalle() throws Exception {
        mvc.perform(generar(42, null, 9)).andExpect(status().isForbidden());
        assertEquals(0, repository.count());
    }
}
