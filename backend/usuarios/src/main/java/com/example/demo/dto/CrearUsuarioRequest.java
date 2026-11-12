package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// No incluye rolUsuario: una cuenta creada publicamente siempre es CLIENTE.
public record CrearUsuarioRequest(
    @NotBlank @Size(max = 120) String nombreUsuario,
    @NotBlank String rutUsuario,
    @NotBlank @Size(max = 240) String direccionUsuario,
    @NotBlank @Email @Size(max = 180) String emailUsuario,
    @NotBlank @Size(min = 8, max = 72) String contrasena
) {}
