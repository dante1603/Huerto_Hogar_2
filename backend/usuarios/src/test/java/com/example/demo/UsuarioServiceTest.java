package com.example.demo;

import com.example.demo.dto.CrearUsuarioRequest;
import com.example.demo.dto.UsuarioRespuesta;
import com.example.demo.models.entities.RolUsuario;
import com.example.demo.models.entities.Usuario;
import com.example.demo.repositories.UsuarioRepository;
import com.example.demo.services.UsuarioDuplicadoException;
import com.example.demo.services.UsuarioNoEncontradoException;
import com.example.demo.services.UsuarioService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {
    @Mock UsuarioRepository repository;
    @Mock PasswordEncoder passwordEncoder;
    private UsuarioService service;

    @BeforeEach
    void preparar() {
        service = new UsuarioService(repository, passwordEncoder);
    }

    private CrearUsuarioRequest solicitud() {
        return new CrearUsuarioRequest("Cliente Prueba", "12.345.678-5", "Santiago",
            "CLIENTE@GMAIL.COM", "ClaveSegura123!");
    }

    @Test
    void registraClienteYNoDevuelveCredenciales() {
        when(passwordEncoder.encode("ClaveSegura123!")).thenReturn("$2a$hash-demo");
        when(repository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> {
            Usuario entidad = inv.getArgument(0);
            assertEquals(RolUsuario.CLIENTE, entidad.getRolUsuario());
            assertEquals("cliente@gmail.com", entidad.getEmailUsuario());
            assertEquals("12345678-5", entidad.getRutUsuario());
            assertEquals("$2a$hash-demo", entidad.getContrasenaHash());
            entidad.setIdUsuario(1L);
            entidad.setFechaHoraCreacionUsuario(LocalDateTime.now());
            return entidad;
        });
        UsuarioRespuesta respuesta = service.registrar(solicitud());
        assertEquals(1L, respuesta.idUsuario());
        assertEquals(RolUsuario.CLIENTE, respuesta.rolUsuario());
        assertEquals(4, UsuarioRespuesta.class.getRecordComponents().length);
        verify(repository).saveAndFlush(any(Usuario.class));
    }

    @Test
    void rechazaCorreoDuplicado() {
        when(repository.existsByEmailUsuarioIgnoreCase("cliente@gmail.com")).thenReturn(true);
        assertThrows(UsuarioDuplicadoException.class, () -> service.registrar(solicitud()));
        verify(repository, never()).saveAndFlush(any(Usuario.class));
    }

    @Test
    void buscarIdInexistenteArrojaError() {
        when(repository.findById(55L)).thenReturn(Optional.empty());
        assertThrows(UsuarioNoEncontradoException.class, () -> service.buscarPorId(55L));
    }
}
