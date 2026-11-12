package com.example.demo;

import com.example.demo.models.entities.RolUsuario;
import com.example.demo.models.entities.Usuario;
import com.example.demo.repositories.UsuarioRepository;
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
    @Autowired PasswordEncoder encoder;

    private Long id;

    @BeforeEach
    void preparar() {
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
}
