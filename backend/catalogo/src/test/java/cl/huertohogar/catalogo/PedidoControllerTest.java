package cl.huertohogar.catalogo;

import cl.huertohogar.catalogo.domain.Producto;
import cl.huertohogar.catalogo.persistence.PedidoRepository;
import cl.huertohogar.catalogo.persistence.ProductoRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "jwt.secret-base64=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=",
    "jwt.issuer=huertohogar-usuarios"
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class PedidoControllerTest {
    private static final String ROOT = "/api/v1/pedidos";
    private static final String TEST_SECRET = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=";

    @Autowired MockMvc mvc;
    @Autowired ProductoRepository productos;
    @Autowired PedidoRepository pedidos;

    private Long idTuberculo;
    private Long idFruta;

    @BeforeEach
    void setUp() {
        pedidos.deleteAll();
        productos.deleteAll();
        idTuberculo = productos.saveAndFlush(new Producto("B-001", "Tuberculo",
            "Demo de stock", BigDecimal.valueOf(120), 3, "Bruma")).getId();
        idFruta = productos.saveAndFlush(new Producto("A-002", "Fruta de Oasis",
            "Demo de stock", BigDecimal.valueOf(240), 1, "Árido")).getId();
    }

    private String token(long userId, String role) {
        var key = new SecretKeySpec(Base64.getDecoder().decode(TEST_SECRET), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
            .issuer("huertohogar-usuarios")
            .subject(Long.toString(userId))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600))
            .claim("rol", role)
            .build();
        return encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String order(Long firstId, int firstQty) {
        return "{\"planetaDestino\":\"Bruma\",\"productos\":[{\"productoId\":" +
            firstId + ",\"cantidad\":" + firstQty + "}]}";
    }

    private String multi(Long first, int quantity, Long second, int quantity2) {
        return "{\"planetaDestino\":\"Árido\",\"productos\":[{\"productoId\":" +
            first + ",\"cantidad\":" + quantity + "},{\"productoId\":" +
            second + ",\"cantidad\":" + quantity2 + "}]}";
    }

    @Test
    void crearReservaStockYReintentoNoDuplica() throws Exception {
        String key = UUID.randomUUID().toString();
        String payload = order(idTuberculo, 2);
        String jwt = token(42, "CLIENTE");
        String first = mvc.perform(post(ROOT).header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clienteId").value(42))
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.total").value(240))
            .andExpect(jsonPath("$.productos[0].precioUnitario").value(120))
            .andReturn().getResponse().getContentAsString();
        int createdId = JsonPath.read(first, "$.id");

        mvc.perform(post(ROOT).header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(createdId));

        org.junit.jupiter.api.Assertions.assertEquals(1, productos.findById(idTuberculo).orElseThrow().getStock());
        org.junit.jupiter.api.Assertions.assertEquals(1, pedidos.count());
    }

    @Test
    void inventarioInsuficienteRevierteTodaLaTransaccion() throws Exception {
        String payload = multi(idTuberculo, 2, idFruta, 2);
        mvc.perform(post(ROOT).header("Authorization", "Bearer " + token(42, "CLIENTE"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(3, productos.findById(idTuberculo).orElseThrow().getStock());
        org.junit.jupiter.api.Assertions.assertEquals(1, productos.findById(idFruta).orElseThrow().getStock());
        org.junit.jupiter.api.Assertions.assertEquals(0, pedidos.count());
    }

    @Test
    void noVendeMasStockTrasPrimeraReserva() throws Exception {
        String jwt = token(42, "CLIENTE");
        mvc.perform(post(ROOT).header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(order(idTuberculo, 3)))
            .andExpect(status().isCreated());
        mvc.perform(post(ROOT).header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(order(idTuberculo, 1)))
            .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(0, productos.findById(idTuberculo).orElseThrow().getStock());
        org.junit.jupiter.api.Assertions.assertEquals(1, pedidos.count());
    }

    @Test
    void clientesSoloVenSusPedidosYVendedorVePendientes() throws Exception {
        String text = mvc.perform(post(ROOT)
                .header("Authorization", "Bearer " + token(42, "CLIENTE"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(order(idTuberculo, 1)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        int pedidoId = JsonPath.read(text, "$.id");

        mvc.perform(get(ROOT + "/" + pedidoId)
                .header("Authorization", "Bearer " + token(43, "CLIENTE")))
            .andExpect(status().isForbidden());

        mvc.perform(get(ROOT + "/mios")
                .header("Authorization", "Bearer " + token(42, "CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(get(ROOT + "/mios")
                .header("Authorization", "Bearer " + token(43, "CLIENTE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get(ROOT + "/pendientes")
                .header("Authorization", "Bearer " + token(99, "VENDEDOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(pedidoId));
        mvc.perform(get(ROOT + "/pendientes")
                .header("Authorization", "Bearer " + token(42, "CLIENTE")))
            .andExpect(status().isForbidden());
    }

    @Test
    void rechazaAccesoAnonimoYVendedorNoPuedeCrear() throws Exception {
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content(order(idTuberculo, 1)))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token(9, "VENDEDOR"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content(order(idTuberculo, 1)))
            .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertEquals(0, pedidos.count());
    }

    @Test
    void mismaClaveConOtroPedidoEsConflicto() throws Exception {
        String jwt = token(42, "CLIENTE");
        String key = UUID.randomUUID().toString();
        mvc.perform(post(ROOT).header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(order(idTuberculo, 1)))
            .andExpect(status().isCreated());
        mvc.perform(post(ROOT).header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(order(idTuberculo, 2)))
            .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(2, productos.findById(idTuberculo).orElseThrow().getStock());
        org.junit.jupiter.api.Assertions.assertEquals(1, pedidos.count());
    }

    @Test
    void validacionesRechazanDuplicadosYCantidadesNoValidas() throws Exception {
        String jwt = token(42, "CLIENTE");
        String duplicate = multi(idTuberculo, 1, idTuberculo, 1);
        mvc.perform(post(ROOT).header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(duplicate))
            .andExpect(status().isBadRequest());
        mvc.perform(post(ROOT).header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(order(idTuberculo, 0)))
            .andExpect(status().isBadRequest());
        mvc.perform(post(ROOT).header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", "no-es-uuid")
                .contentType(MediaType.APPLICATION_JSON).content(order(idTuberculo, 1)))
            .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(0, pedidos.count());
    }
}
