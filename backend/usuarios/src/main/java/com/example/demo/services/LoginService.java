package com.example.demo.services;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.models.entities.Usuario;
import com.example.demo.repositories.UsuarioRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoginService {
    private static final long EXPIRACION_SEGUNDOS = 1800L;
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final String issuer;

    public LoginService(UsuarioRepository repository,
                        PasswordEncoder passwordEncoder,
                        JwtEncoder jwtEncoder,
                        @Value("${jwt.issuer:huertohogar-usuarios}") String issuer) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
    }

    @Transactional(readOnly = true)
    public LoginResponse ingresar(LoginRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        Usuario usuario = repository.findByEmailUsuarioIgnoreCase(email)
            .orElseThrow(() -> credencialesIncorrectas());
        if (!passwordEncoder.matches(request.contrasena(), usuario.getContrasenaHash())) {
            throw credencialesIncorrectas();
        }
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(usuario.getIdUsuario().toString())
            .issuedAt(ahora)
            .expiresAt(ahora.plusSeconds(EXPIRACION_SEGUNDOS))
            .claim("rol", usuario.getRolUsuario().name())
            .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", EXPIRACION_SEGUNDOS,
            usuario.getIdUsuario(), usuario.getRolUsuario().name());
    }

    private static ResponseStatusException credencialesIncorrectas() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas");
    }
}
