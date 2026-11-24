package cl.huertohogar.usuarios.dto;

import cl.huertohogar.usuarios.models.entities.RolUsuario;
import cl.huertohogar.usuarios.models.entities.Usuario;
import java.time.LocalDateTime;

// Respuesta publica minimizada. No incluye RUT, direccion, email ni hash de contrasena.
public record UsuarioRespuesta(
    Long idUsuario,
    String nombreUsuario,
    RolUsuario rolUsuario,
    LocalDateTime fechaHoraCreacionUsuario
) {
    public static UsuarioRespuesta desdeEntidad(Usuario usuario) {
        return new UsuarioRespuesta(
            usuario.getIdUsuario(),
            usuario.getNombreUsuario(),
            usuario.getRolUsuario(),
            usuario.getFechaHoraCreacionUsuario()
        );
    }
}
