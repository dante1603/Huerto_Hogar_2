package cl.huertohogar.pedidos;

import cl.huertohogar.pedidos.api.CrearPedidoRequest;
import cl.huertohogar.pedidos.domain.EstadoPedido;
import cl.huertohogar.pedidos.persistence.PedidoRepository;
import cl.huertohogar.pedidos.remote.ProductoReservaGateway;
import cl.huertohogar.pedidos.remote.ReservaIndeterminadaException;
import cl.huertohogar.pedidos.remote.ReservaRemota;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
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
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={
    "jwt.secret-base64=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=",
    "jwt.issuer=huertohogar-usuarios"
})
@AutoConfigureMockMvc @ActiveProfiles("demo")
class PedidoIndependienteTest {
    private static final String ROOT="/api/v1/pedidos";
    @Autowired MockMvc mvc;
    @Autowired PedidoRepository pedidos;
    @MockitoBean ProductoReservaGateway gateway;

    @BeforeEach
    void before() {
        pedidos.deleteAll();
        reset(gateway);
        when(gateway.reservar(anyString(),anyList())).thenAnswer(inv -> {
            String referencia=inv.getArgument(0);
            @SuppressWarnings("unchecked")
            List<CrearPedidoRequest.Item> items=inv.getArgument(1);
            var lineas=items.stream().map(item -> new ReservaRemota.Linea(
                item.productoId(),"Producto verificado",item.cantidad(),
                BigDecimal.valueOf(100),BigDecimal.valueOf(item.cantidad()*100L))).toList();
            BigDecimal total=lineas.stream().map(ReservaRemota.Linea::subtotal)
                .reduce(BigDecimal.ZERO,BigDecimal::add);
            return new ReservaRemota(referencia,"RESERVADA",total,Instant.now(),lineas);
        });
    }
    static String jwt(long user,String rol){
        var key=new SecretKeySpec(Base64.getDecoder().decode(
            "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA="),"HmacSHA256");
        var encoder=new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer("huertohogar-usuarios")
            .subject(Long.toString(user)).issuedAt(now).expiresAt(now.plusSeconds(600));
        if (rol != null) claims.claim("rol", rol);
        return encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
    static String body(int qty) {
        return "{\"planetaDestino\":\"Bruma\",\"productos\":[{\"productoId\":1,\"cantidad\":"+qty+"}]}";
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder crear(long id,String rol,String key,String payload) {
        return post(ROOT).header("Authorization","Bearer "+jwt(id,rol))
            .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(payload);
    }

    @Test void clienteCreaPedidoYSoloVeSuDetalle() throws Exception {
        String key=UUID.randomUUID().toString();
        String json=mvc.perform(crear(42,"CLIENTE",key,body(2)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.total").value(200))
            .andExpect(jsonPath("$.productos[0].nombreProducto").value("Producto verificado"))
            .andReturn().getResponse().getContentAsString();
        int id=JsonPath.read(json,"$.id");
        mvc.perform(get(ROOT+"/{id}",id).header("Authorization","Bearer "+jwt(42,"CLIENTE")))
            .andExpect(status().isOk());
        mvc.perform(get(ROOT+"/{id}",id).header("Authorization","Bearer "+jwt(43,"CLIENTE")))
            .andExpect(status().isForbidden());
        mvc.perform(get(ROOT+"/mios").header("Authorization","Bearer "+jwt(42,"CLIENTE")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(ROOT+"/pendientes").header("Authorization","Bearer "+jwt(99,"VENDEDOR")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        assertEquals(1,pedidos.count());
    }

    @Test void mismoUuidReintentaSinLlamarDeNuevoAProductos() throws Exception {
        String key=UUID.randomUUID().toString();
        mvc.perform(crear(42,"CLIENTE",key,body(2))).andExpect(status().isCreated());
        mvc.perform(crear(42,"CLIENTE",key,body(2))).andExpect(status().isOk());
        mvc.perform(crear(42,"CLIENTE",key,body(3))).andExpect(status().isConflict());
        verify(gateway,times(1)).reservar(anyString(),anyList());
        assertEquals(1,pedidos.count());
    }

    @Test void redAmbiguaConsultaMismoUuidYReconcilia() throws Exception {
        when(gateway.reservar(anyString(),anyList())).thenThrow(
            new ReservaIndeterminadaException(new RuntimeException("timeout")));
        when(gateway.consultar(anyString())).thenAnswer(inv -> new ReservaRemota(
            inv.getArgument(0),"RESERVADA",BigDecimal.valueOf(200),Instant.now(),
            List.of(new ReservaRemota.Linea(1L,"Producto",2,BigDecimal.valueOf(100),BigDecimal.valueOf(200)))));
        mvc.perform(crear(42,"CLIENTE",UUID.randomUUID().toString(),body(2)))
            .andExpect(status().isCreated());
        assertEquals(1,pedidos.count());
        verify(gateway,times(1)).consultar(anyString());
    }

    @Test void redSinConfirmacionDejaIntencionParaReintentoSeguro() throws Exception {
        when(gateway.reservar(anyString(),anyList())).thenThrow(
            new ReservaIndeterminadaException(new RuntimeException("timeout")));
        when(gateway.consultar(anyString())).thenThrow(
            new ReservaIndeterminadaException(new RuntimeException("offline")));
        String key=UUID.randomUUID().toString();
        mvc.perform(crear(42,"CLIENTE",key,body(1))).andExpect(status().isServiceUnavailable());
        assertEquals(1,pedidos.count());
        assertEquals(EstadoPedido.INICIADO,pedidos.findAll().get(0).getEstado());
        // No pedir un segundo UUID; la reintento reusa la intención y la misma referencia.
        reset(gateway);
        when(gateway.reservar(anyString(),anyList())).thenAnswer(inv -> new ReservaRemota(
            inv.getArgument(0),"RESERVADA",BigDecimal.valueOf(100),Instant.now(),
            List.of(new ReservaRemota.Linea(1L,"Producto",1,BigDecimal.valueOf(100),BigDecimal.valueOf(100)))));
        mvc.perform(crear(42,"CLIENTE",key,body(1)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDIENTE"));
        assertEquals(1,pedidos.count());
    }

    @Test void jwtYValidacionesDeEntradaImpidenMutaciones() throws Exception {
        String key=UUID.randomUUID().toString();
        mvc.perform(post(ROOT).header("Idempotency-Key",key)
            .contentType(MediaType.APPLICATION_JSON).content(body(1)))
            .andExpect(status().isUnauthorized());
        mvc.perform(crear(9,"VENDEDOR",key,body(1))).andExpect(status().isForbidden());
        mvc.perform(crear(9,"CLIENTE","no-uuid",body(1))).andExpect(status().isBadRequest());
        mvc.perform(crear(9,"CLIENTE",key,body(0))).andExpect(status().isBadRequest());
        String duplicado="{\"planetaDestino\":\"Bruma\",\"productos\":["
            +"{\"productoId\":1,\"cantidad\":1},{\"productoId\":1,\"cantidad\":1}]}";
        mvc.perform(crear(9,"CLIENTE",key,duplicado)).andExpect(status().isBadRequest());
        assertEquals(0,pedidos.count());
    }

    @Test void swaggerDocumentaLasRutasIndependientes() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/pedidos'].post").exists());
    }

    @Test void jwtFirmadoSinRolNoAutorizaPedido() throws Exception {
        mvc.perform(crear(42, null, UUID.randomUUID().toString(), body(1)))
            .andExpect(status().isForbidden());
        assertEquals(0, pedidos.count());
    }
}
