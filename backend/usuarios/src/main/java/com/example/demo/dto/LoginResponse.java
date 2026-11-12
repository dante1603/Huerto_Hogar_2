package com.example.demo.dto;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    Long idUsuario,
    String rolUsuario
) {}
