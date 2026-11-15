package cl.huertohogar.usuarios;

import cl.huertohogar.usuarios.models.entities.RolUsuario;
import cl.huertohogar.usuarios.models.entities.Usuario;
import cl.huertohogar.usuarios.repositories.UsuarioRepository;
import cl.huertohogar.usuarios.repositories.RefreshSesionRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "jwt.secret-base64=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=",
    "app.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AuthFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository repository;
    @Autowired RefreshSesionRepository sesiones;
    @Autowired PasswordEncoder encoder;

    private Long id;

    @BeforeEach
    void preparar() {
        sesiones.deleteAll();
        repository.deleteAll();
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario("Cliente de prueba");
        usuario.setRutUsuario("12345678-5");
        usuario.setDireccionUsuario("Santiago");
        usuario.setEmailUsuario("cliente@example.com");
        usuario.setContrasenaHash(encoder.encode("ClaveSegura123!"));
        usuario.setRolUsuario(RolUsuario.CLIENTE);
        usuario.setFechaHoraCreacionUsuario(LocalDateTime.now());
        id = repository.saveAndFlush(usuario).getIdUsuario();
    }

    private String login(String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cliente@example.com\",\"contrasena\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.rolUsuario").value("CLIENTE"))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    @Test
    void loginYConsultaPerfilPropioConJwtValido() throws Exception {
        String jwt = login("ClaveSegura123!");
        mvc.perform(get("/api/v1/usuarios/" + id)
                .header("Authorization", "Bearer " + jwt))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rolUsuario").value("CLIENTE"));
    }

    @Test
    void rechazaContrasenaIncorrecta() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cliente@example.com\",\"contrasena\":\"incorrecta\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void perfilSinTokenEs401() throws Exception {
        mvc.perform(get("/api/v1/usuarios/" + id))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteNoPuedeVerPerfilDeOtroId() throws Exception {
        String jwt = login("ClaveSegura123!");
        mvc.perform(get("/api/v1/usuarios/" + (id + 999))
                .header("Authorization", "Bearer " + jwt))
            .andExpect(status().isForbidden());
    }

    @Test
    void laCorsSoloAceptaOrigenDeclarado() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
    private String loginCompleto() throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cliente@example.com\",\"contrasena\":\"ClaveSegura123!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshExpiresInSeconds").value(86400))
            .andReturn().getResponse().getContentAsString();
    }

    @Test
    void refreshRotaElSecretoYElAnteriorEsInvalido() throws Exception {
        String initial = loginCompleto();
        String first = JsonPath.read(initial, "$.refreshToken");
        org.junit.jupiter.api.Assertions.assertEquals(1, sesiones.count());
        org.junit.jupiter.api.Assertions.assertNotEquals(first, sesiones.findAll().get(0).getTokenHash());

        String rotated = mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + first + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rolUsuario").value("CLIENTE"))
            .andExpect(jsonPath("$.expiresInSeconds").value(1800))
            .andReturn().getResponse().getContentAsString();
        String second = JsonPath.read(rotated, "$.refreshToken");

        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
        org.junit.jupiter.api.Assertions.assertEquals(1, sesiones.count());
        mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + first + "\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + second + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void revocaRefreshYNoPermiteNuevoAcceso() throws Exception {
        String initial = loginCompleto();
        String refresh = JsonPath.read(initial, "$.refreshToken");
        String payload = "{\"refreshToken\":\"" + refresh + "\"}";
        mvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(0, sesiones.count());
        mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isUnauthorized());
        // Logout es idempotente: no revela si el token ya se había revocado.
        mvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isNoContent());
    }

    @Test
    void refreshRecuperaRolActualDelUsuarioYRechazaFormatoIncorrecto() throws Exception {
        String initial = loginCompleto();
        String refresh = JsonPath.read(initial, "$.refreshToken");
        Usuario usuario = repository.findById(id).orElseThrow();
        usuario.setRolUsuario(RolUsuario.VENDEDOR);
        repository.saveAndFlush(usuario);

        mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rolUsuario").value("VENDEDOR"));

        mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"incorrecto\"}"))
            .andExpect(status().isBadRequest());
    }

}
