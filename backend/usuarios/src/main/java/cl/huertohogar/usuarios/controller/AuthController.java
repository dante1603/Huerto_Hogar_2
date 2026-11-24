package cl.huertohogar.usuarios.controller;

import cl.huertohogar.usuarios.dto.LoginRequest;
import cl.huertohogar.usuarios.dto.LoginResponse;
import cl.huertohogar.usuarios.dto.RefreshRequest;
import cl.huertohogar.usuarios.services.LoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticacion", description = "Login JWT, renovacion rotativa y revocacion")
public class AuthController {
    private final LoginService loginService;

    public AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    @Operation(summary = "Emitir JWT temporal y refresh token")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.ingresar(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar JWT y rotar refresh token de un solo uso")
    public LoginResponse refrescar(@Valid @RequestBody RefreshRequest request) {
        return loginService.renovar(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revocar refresh token sin revelar su existencia")
    public ResponseEntity<Void> cerrar(@Valid @RequestBody RefreshRequest request) {
        loginService.revocar(request);
        return ResponseEntity.noContent().build();
    }
}
