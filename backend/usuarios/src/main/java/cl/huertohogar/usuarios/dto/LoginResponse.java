package cl.huertohogar.usuarios.dto;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    Long idUsuario,
    String rolUsuario,
    String refreshToken,
    long refreshExpiresInSeconds
) {}
