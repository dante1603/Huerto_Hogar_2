package cl.huertohogar.usuarios;

import cl.huertohogar.usuarios.dto.CrearUsuarioRequest;
import cl.huertohogar.usuarios.dto.UsuarioRespuesta;
import cl.huertohogar.usuarios.models.entities.RolUsuario;
import cl.huertohogar.usuarios.repositories.UsuarioRepository;
import cl.huertohogar.usuarios.services.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UsuarioPersistenciaTest {
    @Autowired UsuarioService service;
    @Autowired UsuarioRepository repository;

    @Test
    void guardaEnBaseYRecuperaPorIdSinExponerHash() {
        CrearUsuarioRequest request = new CrearUsuarioRequest(
            "Cliente de Prueba", "12345678-5", "Calle Ejemplo 123", "prueba@example.com", "ContrasenaSegura123!");
        UsuarioRespuesta creado = service.registrar(request);
        assertNotNull(creado.idUsuario());
        assertEquals(RolUsuario.CLIENTE, creado.rolUsuario());
        assertEquals(creado.idUsuario(), service.buscarPorId(creado.idUsuario()).idUsuario());
        var guardado = repository.findById(creado.idUsuario()).orElseThrow();
        assertNotEquals(request.contrasena(), guardado.getContrasenaHash());
        assertTrue(guardado.getContrasenaHash().startsWith("$2"));
    }
}
