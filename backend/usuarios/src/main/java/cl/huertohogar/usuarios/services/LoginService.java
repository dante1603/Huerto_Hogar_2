package cl.huertohogar.usuarios.services;

import cl.huertohogar.usuarios.dto.LoginRequest;
import cl.huertohogar.usuarios.dto.LoginResponse;
import cl.huertohogar.usuarios.dto.RefreshRequest;
import cl.huertohogar.usuarios.models.entities.RefreshSesion;
import cl.huertohogar.usuarios.models.entities.Usuario;
import cl.huertohogar.usuarios.repositories.RefreshSesionRepository;
import cl.huertohogar.usuarios.repositories.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
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
    private static final long REFRESH_EXPIRACION_SEGUNDOS = 86400L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final RefreshSesionRepository sesiones;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final String issuer;

    public LoginService(UsuarioRepository usuarios,
                        RefreshSesionRepository sesiones,
                        PasswordEncoder passwordEncoder,
                        JwtEncoder jwtEncoder,
                        @Value("${jwt.issuer:huertohogar-usuarios}") String issuer) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
    }

    @Transactional
    public LoginResponse ingresar(LoginRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarios.findByEmailUsuarioIgnoreCase(email)
            .orElseThrow(LoginService::credencialesIncorrectas);
        if (!passwordEncoder.matches(request.contrasena(), usuario.getContrasenaHash())) {
            throw credencialesIncorrectas();
        }
        return emitir(usuario, Instant.now());
    }

    @Transactional
    public LoginResponse renovar(RefreshRequest request) {
        Instant now = Instant.now();
        RefreshSesion anterior = sesiones.findPorHashBloqueada(hash(request.refreshToken()))
            .orElseThrow(LoginService::credencialesIncorrectas);
        if (!anterior.getExpiraEn().isAfter(now)) {
            throw credencialesIncorrectas();
        }
        Usuario usuario = usuarios.findById(anterior.getUsuarioId())
            .orElseThrow(LoginService::credencialesIncorrectas);

        // Solo un intento puede consumir el token anterior.
        sesiones.delete(anterior);
        sesiones.flush();
        return emitir(usuario, now);
    }

    @Transactional
    public void revocar(RefreshRequest request) {
        // Respuesta idempotente: no revelar si existía ese token.
        sesiones.findPorHashBloqueada(hash(request.refreshToken()))
            .ifPresent(sesiones::delete);
    }

    private LoginResponse emitir(Usuario usuario, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(usuario.getIdUsuario().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(EXPIRACION_SEGUNDOS))
            .claim("rol", usuario.getRolUsuario().name())
            .build();
        String jwt = jwtEncoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        sesiones.saveAndFlush(new RefreshSesion(hash(refresh), usuario.getIdUsuario(),
            now.plusSeconds(REFRESH_EXPIRACION_SEGUNDOS)));
        return new LoginResponse(jwt, "Bearer", EXPIRACION_SEGUNDOS,
            usuario.getIdUsuario(), usuario.getRolUsuario().name(),
            refresh, REFRESH_EXPIRACION_SEGUNDOS);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static ResponseStatusException credencialesIncorrectas() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales o sesion incorrecta");
    }
}
