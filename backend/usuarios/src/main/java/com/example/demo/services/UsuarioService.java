package com.example.demo.services;

import com.example.demo.dto.CrearUsuarioRequest;
import com.example.demo.dto.UsuarioRespuesta;
import com.example.demo.models.entities.RolUsuario;
import com.example.demo.models.entities.Usuario;
import com.example.demo.repositories.UsuarioRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UsuarioRespuesta registrar(CrearUsuarioRequest request) {
        String rut = RutChileno.normalizarYValidar(request.rutUsuario());
        String email = request.emailUsuario().strip().toLowerCase(Locale.ROOT);
        if (repository.existsByRutUsuario(rut)) {
            throw new UsuarioDuplicadoException("Ya existe un usuario con ese RUT");
        }
        if (repository.existsByEmailUsuarioIgnoreCase(email)) {
            throw new UsuarioDuplicadoException("Ya existe un usuario con ese correo");
        }

        Usuario usuario = new Usuario();
        usuario.setNombreUsuario(request.nombreUsuario().strip());
        usuario.setRutUsuario(rut);
        usuario.setDireccionUsuario(request.direccionUsuario().strip());
        usuario.setEmailUsuario(email);
        usuario.setContrasenaHash(passwordEncoder.encode(request.contrasena()));
        usuario.setRolUsuario(RolUsuario.CLIENTE);

        try {
            Usuario guardado = repository.saveAndFlush(usuario);
            return UsuarioRespuesta.desdeEntidad(guardado);
        } catch (DataIntegrityViolationException ex) {
            // Protege tambien frente a dos solicitudes concurrentes con el mismo RUT/email.
            throw new UsuarioDuplicadoException("Ya existe un usuario con esos datos", ex);
        }
    }

    @Transactional(readOnly = true)
    public UsuarioRespuesta buscarPorId(Long id) {
        return repository.findById(id)
            .map(UsuarioRespuesta::desdeEntidad)
            .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario no encontrado"));
    }
}
