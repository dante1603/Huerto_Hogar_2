package cl.huertohogar.catalogo;

import cl.huertohogar.catalogo.domain.Producto;
import cl.huertohogar.catalogo.persistence.PedidoRepository;
import cl.huertohogar.catalogo.persistence.ProductoRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pruebas HTTP + seguridad + consistencia con pedidos (H2 demo, no MySQL real). */
@SpringBootTest(properties = {
    "jwt.secret-base64=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=",
    "jwt.issuer=huertohogar-usuarios"
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductoAdminControllerTest {
    private static final String ROOT = "/api/v1/productos";
    private static final String PEDIDOS = "/api/v1/pedidos";
    private static final String KEY = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=";
    @Autowired MockMvc mvc;
    @Autowired ProductoRepository productos;
    @Autowired PedidoRepository pedidos;

    private Long baseId;

    @BeforeEach
    void reset() {
        pedidos.deleteAll();
        productos.deleteAll();
        baseId = productos.saveAndFlush(new Producto("BASE-001", "Cultivo",
            "Referencia", BigDecimal.valueOf(120), 9, "Bruma")).getId();
    }

    private static String token(String role) {
        var key = new SecretKeySpec(Base64.getDecoder().decode(KEY), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("huertohogar-usuarios")
            .subject("42").issuedAt(now).expiresAt(now.plusSeconds(600));
        if (role != null) claims.claim("rol", role);
        return encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    private static String valid(String sku, int stock) {
        return "{\"sku\":\"" + sku + "\",\"nombre\":\"Cultivo administrado\","
            + "\"descripcion\":\"Producto de prueba\",\"precio\":450,\"stock\":" + stock
            + ",\"planetaOrigen\":\"Árido\"}";
    }

    @Test
    void adminCreaProductoYLoHaceVisibleEnLecturaPublica() throws Exception {
        var body = mvc.perform(post(ROOT)
                .header("Authorization", "Bearer " + token("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON).content(valid("TEST-NUEVO", 14)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/productos/[0-9]+")))
            .andExpect(jsonPath("$.sku").value("TEST-NUEVO"))
            .andExpect(jsonPath("$.stock").value(14))
            .andReturn().getResponse().getContentAsString();

        int id = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        mvc.perform(get(ROOT + "/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sku").value("TEST-NUEVO"));
        assertTrue(productos.existsBySku("TEST-NUEVO"));
    }

    @Test
    void adminActualizaNombreSkuPrecioYStockSinCambiarId() throws Exception {
        mvc.perform(put(ROOT + "/{id}", baseId)
                .header("Authorization", "Bearer " + token("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON).content(valid("NUEVO-001", 37)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(baseId))
            .andExpect(jsonPath("$.sku").value("NUEVO-001"))
            .andExpect(jsonPath("$.stock").value(37))
            .andExpect(jsonPath("$.precio").value(450));

        assertFalse(productos.existsBySku("BASE-001"));
        assertEquals(37, productos.findById(baseId).orElseThrow().getStock());
    }

    @Test
    void adminEliminaProductoNoReferenciado() throws Exception {
        mvc.perform(delete(ROOT + "/{id}", baseId)
                .header("Authorization", "Bearer " + token("ADMINISTRADOR")))
            .andExpect(status().isNoContent());
        mvc.perform(get(ROOT + "/{id}", baseId)).andExpect(status().isNotFound());
    }

    @Test
    void escrituraExigeAdminYNoPermiteAnonimoClienteNiVendedor() throws Exception {
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON)
                .content(valid("ANONIMO", 2))).andExpect(status().isUnauthorized());
        for (String rol : new String[]{"CLIENTE", "VENDEDOR"}) {
            String jwt = token(rol);
            mvc.perform(post(ROOT).header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON).content(valid("NO-ADMIN", 4)))
                .andExpect(status().isForbidden());
            mvc.perform(put(ROOT + "/{id}", baseId)
                .header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON).content(valid("NO-ADMIN", 4)))
                .andExpect(status().isForbidden());
            mvc.perform(delete(ROOT + "/{id}", baseId)
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
        }
        assertEquals(1, productos.count());
        mvc.perform(get(ROOT + "/{id}", baseId)).andExpect(status().isOk());
    }

    @Test
    void validaCamposYDuplicidadSinEscribirDatos() throws Exception {
        String jwt = "Bearer " + token("ADMINISTRADOR");
        String invalid = valid("BAD-001", -3).replace("\"precio\":450", "\"precio\":-8");
        mvc.perform(post(ROOT).header("Authorization", jwt)
            .contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isBadRequest());
        mvc.perform(post(ROOT).header("Authorization", jwt)
            .contentType(MediaType.APPLICATION_JSON).content(valid("BASE-001", 5)))
            .andExpect(status().isConflict());
        mvc.perform(put(ROOT + "/{id}", baseId)
            .header("Authorization", jwt)
            .contentType(MediaType.APPLICATION_JSON).content(valid("BAD-001", -1)))
            .andExpect(status().isBadRequest());
        assertEquals(1, productos.count());
        assertEquals(9, productos.findById(baseId).orElseThrow().getStock());
    }

    @Test
    void actualizarConSkuDeOtroProductoDevuelve409() throws Exception {
        Long otherId = productos.saveAndFlush(new Producto("OTRO-002", "Otra cosecha",
            "Referencia", BigDecimal.ONE, 2, "Yermo")).getId();
        mvc.perform(put(ROOT + "/{id}", otherId)
                .header("Authorization", "Bearer " + token("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON).content(valid("BASE-001", 2)))
            .andExpect(status().isConflict());
        assertEquals("OTRO-002", productos.findById(otherId).orElseThrow().getSku());
    }

    @Test
    void operacionesSobreIdInexistenteDevuelven404() throws Exception {
        String jwt = "Bearer " + token("ADMINISTRADOR");
        mvc.perform(put(ROOT + "/999999").header("Authorization", jwt)
            .contentType(MediaType.APPLICATION_JSON).content(valid("TEST-404", 1)))
            .andExpect(status().isNotFound());
        mvc.perform(delete(ROOT + "/999999").header("Authorization", jwt))
            .andExpect(status().isNotFound());
    }

    @Test
    void noEliminaProductoConReservaYConservaPedidoHistorico() throws Exception {
        String body = "{\"planetaDestino\":\"Bruma\",\"productos\":[{\"productoId\":" + baseId
            + ",\"cantidad\":2}]}";
        mvc.perform(post(PEDIDOS)
                .header("Authorization", "Bearer " + token("CLIENTE"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.estado").value("PENDIENTE"));
        mvc.perform(delete(ROOT + "/{id}", baseId)
                .header("Authorization", "Bearer " + token("ADMINISTRADOR")))
            .andExpect(status().isConflict());
        assertTrue(productos.existsById(baseId));
        assertEquals(7, productos.findById(baseId).orElseThrow().getStock());
        assertEquals(1, pedidos.count());
    }

    @Test
    void swaggerEnumeraOperacionesDeProducto() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/productos'].post").exists())
            .andExpect(jsonPath("$.paths['/api/v1/productos/{id}'].put").exists())
            .andExpect(jsonPath("$.paths['/api/v1/productos/{id}'].delete").exists());
    }

    @Test
    void jwtFirmadoSinRolNoAutorizaEscritura() throws Exception {
        mvc.perform(post(ROOT).header("Authorization", "Bearer " + token(null))
            .contentType(MediaType.APPLICATION_JSON).content(valid("SIN-ROL", 2)))
            .andExpect(status().isForbidden());
        assertFalse(productos.existsBySku("SIN-ROL"));
    }
}
