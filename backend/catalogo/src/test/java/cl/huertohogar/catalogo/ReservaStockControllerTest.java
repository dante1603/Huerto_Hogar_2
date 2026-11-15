package cl.huertohogar.catalogo;

import cl.huertohogar.catalogo.domain.Producto;
import cl.huertohogar.catalogo.persistence.PedidoRepository;
import cl.huertohogar.catalogo.persistence.ProductoRepository;
import cl.huertohogar.catalogo.persistence.ReservaStockRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de la futura separacion Pedidos→Productos.
 * H2/MockMvc: NO acredita red distribuida ni MySQL real.
 */
@SpringBootTest(properties = {
    "jwt.secret-base64=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=",
    "jwt.issuer=huertohogar-usuarios"
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReservaStockControllerTest {
    private static final String ROOT = "/api/v1/interno/reservas";
    private static final String SECRET = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=";

    @Autowired MockMvc mvc;
    @Autowired ReservaStockRepository reservas;
    @Autowired ProductoRepository productos;
    @Autowired PedidoRepository pedidos;

    private Long tuberculo;
    private Long fruta;

    @BeforeEach
    void preparar() {
        limpiar();
        tuberculo = productos.saveAndFlush(new Producto("RES-A", "Tuberculo",
            "Producto con stock", BigDecimal.valueOf(120), 6, "Bruma")).getId();
        fruta = productos.saveAndFlush(new Producto("RES-B", "Fruta",
            "Producto con poco stock", BigDecimal.valueOf(240), 1, "Árido")).getId();
    }

    @AfterEach
    void limpiar() {
        reservas.deleteAll();
        pedidos.deleteAll();
        productos.deleteAll();
    }

    private static String token(long id, String role) {
        var key = new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
            .issuer("huertohogar-usuarios")
            .subject(Long.toString(id))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600))
            .claim("rol", role)
            .build();
        return encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String payload(int cantidad) {
        return "{\"productos\":[{\"productoId\":" + tuberculo +
            ",\"cantidad\":" + cantidad + "}]}";
    }

    private String dosProductos(int a, int b) {
        return "{\"productos\":[{\"productoId\":" + tuberculo +
            ",\"cantidad\":" + a + "},{\"productoId\":" + fruta +
            ",\"cantidad\":" + b + "}]}";
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder servicePut(
        String uuid, String body) {
        return put(ROOT + "/{referencia}", uuid)
            .header("Authorization", "Bearer " + token(1L, "SERVICIO_PEDIDOS"))
            .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder serviceDelete(String uuid) {
        return delete(ROOT + "/{referencia}", uuid)
            .header("Authorization", "Bearer " + token(1L, "SERVICIO_PEDIDOS"));
    }

    @Test
    void servicioReservaPrecioDeBDYReintentoNoDuplicaDescuento() throws Exception {
        String uuid = UUID.randomUUID().toString();
        mvc.perform(servicePut(uuid, payload(2)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", ROOT + "/" + uuid))
            .andExpect(jsonPath("$.estado").value("RESERVADA"))
            .andExpect(jsonPath("$.total").value(240))
            .andExpect(jsonPath("$.productos[0].nombreProducto").value("Tuberculo"))
            .andExpect(jsonPath("$.productos[0].precioUnitario").value(120))
            .andExpect(jsonPath("$.productos[0].subtotal").value(240));

        mvc.perform(servicePut(uuid, payload(2)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.referencia").value(uuid));
        mvc.perform(get(ROOT + "/{referencia}", uuid)
                .header("Authorization", "Bearer " + token(1L, "SERVICIO_PEDIDOS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(240));

        assertEquals(4, productos.findById(tuberculo).orElseThrow().getStock());
        assertEquals(1, reservas.count());
    }

    @Test
    void mismaReferenciaConOtroContenidoEs409() throws Exception {
        String uuid = UUID.randomUUID().toString();
        mvc.perform(servicePut(uuid, payload(1))).andExpect(status().isCreated());
        mvc.perform(servicePut(uuid, payload(2))).andExpect(status().isConflict());
        assertEquals(5, productos.findById(tuberculo).orElseThrow().getStock());
        assertEquals(1, reservas.count());
    }

    @Test
    void cancelacionReponeStockUnaVezYNoAdmiteReReserva() throws Exception {
        String uuid = UUID.randomUUID().toString();
        mvc.perform(servicePut(uuid, payload(3))).andExpect(status().isCreated());
        assertEquals(3, productos.findById(tuberculo).orElseThrow().getStock());

        mvc.perform(serviceDelete(uuid)).andExpect(status().isNoContent());
        mvc.perform(serviceDelete(uuid)).andExpect(status().isNoContent());
        assertEquals(6, productos.findById(tuberculo).orElseThrow().getStock());

        mvc.perform(get(ROOT + "/{referencia}", uuid)
                .header("Authorization", "Bearer " + token(1L, "SERVICIO_PEDIDOS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CANCELADA"));
        mvc.perform(servicePut(uuid, payload(3))).andExpect(status().isConflict());
        assertEquals(1, reservas.count());
        assertEquals(6, productos.findById(tuberculo).orElseThrow().getStock());
    }

    @Test
    void cancelacionPrematuraCreaTombstoneYBloqueaReservaTardia() throws Exception {
        String uuid = UUID.randomUUID().toString();
        mvc.perform(serviceDelete(uuid)).andExpect(status().isNoContent());
        mvc.perform(servicePut(uuid, payload(2))).andExpect(status().isConflict());
        assertEquals(6, productos.findById(tuberculo).orElseThrow().getStock());
        assertEquals(1, reservas.count());
    }

    @Test
    void insuficienciaDeStockNoCambiaNingunProductoNiCreaReserva() throws Exception {
        mvc.perform(servicePut(UUID.randomUUID().toString(), dosProductos(2, 2)))
            .andExpect(status().isConflict());
        assertEquals(6, productos.findById(tuberculo).orElseThrow().getStock());
        assertEquals(1, productos.findById(fruta).orElseThrow().getStock());
        assertEquals(0, reservas.count());
    }

    @Test
    void rechazaDuplicadosCamposInvalidosYReferenciasMalformadas() throws Exception {
        String uuid = UUID.randomUUID().toString();
        mvc.perform(servicePut(uuid, "{\"productos\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(servicePut(uuid, dosProductos(1, 0))).andExpect(status().isBadRequest());
        String duplicado = "{\"productos\":[{\"productoId\":" + tuberculo +
            ",\"cantidad\":1},{\"productoId\":" + tuberculo + ",\"cantidad\":1}]}";
        mvc.perform(servicePut(uuid, duplicado)).andExpect(status().isBadRequest());
        mvc.perform(servicePut("no-uuid", payload(1))).andExpect(status().isBadRequest());
        mvc.perform(servicePut(uuid, "{\"productos\":[{\"productoId\":999999,\"cantidad\":1}]}"))
            .andExpect(status().isNotFound());
        assertEquals(0, reservas.count());
    }

    @Test
    void soloServicioPedidosPuedeLeerReservarYCancelar() throws Exception {
        String uuid = UUID.randomUUID().toString();
        mvc.perform(put(ROOT + "/{referencia}", uuid)
                .contentType(MediaType.APPLICATION_JSON).content(payload(1)))
            .andExpect(status().isUnauthorized());
        for (String role : new String[]{"CLIENTE", "VENDEDOR", "ADMINISTRADOR", "DESCONOCIDO"}) {
            String auth = "Bearer " + token(5L, role);
            mvc.perform(put(ROOT + "/{referencia}", uuid).header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content(payload(1)))
                .andExpect(status().isForbidden());
            mvc.perform(get(ROOT + "/{referencia}", uuid).header("Authorization", auth))
                .andExpect(status().isForbidden());
            mvc.perform(delete(ROOT + "/{referencia}", uuid).header("Authorization", auth))
                .andExpect(status().isForbidden());
        }
        assertEquals(6, productos.findById(tuberculo).orElseThrow().getStock());
        assertEquals(0, reservas.count());
    }

    @Test
    void ventaLegadaYReservaInternaCompartenElMismoBloqueoDeStock() throws Exception {
        String payload = "{\"planetaDestino\":\"Bruma\",\"productos\":[{\"productoId\":" +
            tuberculo + ",\"cantidad\":5}]}";
        mvc.perform(post("/api/v1/pedidos")
                .header("Authorization", "Bearer " + token(42L, "CLIENTE"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isCreated());
        mvc.perform(servicePut(UUID.randomUUID().toString(), payload(2)))
            .andExpect(status().isConflict());
        assertEquals(1, productos.findById(tuberculo).orElseThrow().getStock());
        assertEquals(1, pedidos.count());
        assertEquals(0, reservas.count());
    }

    @Test
    void productoConReservaActivaNoSePuedeEliminarNiCambiarStock() throws Exception {
        String uuid = UUID.randomUUID().toString();
        mvc.perform(servicePut(uuid, payload(2))).andExpect(status().isCreated());
        String admin = "Bearer " + token(10L, "ADMINISTRADOR");
        mvc.perform(delete("/api/v1/productos/{id}", tuberculo)
                .header("Authorization", admin)).andExpect(status().isConflict());

        String update = "{\"sku\":\"RES-A\",\"nombre\":\"Otro nombre\",\"descripcion\":\"texto\","
            + "\"precio\":120,\"stock\":5,\"planetaOrigen\":\"Bruma\"}";
        mvc.perform(put("/api/v1/productos/{id}", tuberculo)
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(update)).andExpect(status().isConflict());

        mvc.perform(serviceDelete(uuid)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/productos/{id}", tuberculo)
                .header("Authorization", admin)).andExpect(status().isNoContent());
    }

    @Test
    void swaggerIncluyeFronteraInternaDeReserva() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/interno/reservas/{referencia}'].put").exists())
            .andExpect(jsonPath("$.paths['/api/v1/interno/reservas/{referencia}'].delete").exists());
    }
}
