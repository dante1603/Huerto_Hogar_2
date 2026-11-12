package com.example.demo.controller;

import com.example.demo.dto.CrearUsuarioRequest;
import com.example.demo.dto.UsuarioRespuesta;
import com.example.demo.services.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuarios", description = "Registro y consulta basica de usuarios")
public class UsuarioController {
    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Registrar un usuario con rol CLIENTE")
    public ResponseEntity<UsuarioRespuesta> crear(@Valid @RequestBody CrearUsuarioRequest request) {
        UsuarioRespuesta usuario = service.registrar(request);
        return ResponseEntity
            .created(URI.create("/api/v1/usuarios/" + usuario.idUsuario()))
            .body(usuario);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar datos publicos de un usuario por ID")
    public UsuarioRespuesta obtener(@PathVariable Long id) {
        return service.buscarPorId(id);
    }
}
