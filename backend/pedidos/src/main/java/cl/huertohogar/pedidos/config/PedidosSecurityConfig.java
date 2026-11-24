package cl.huertohogar.pedidos.config;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.DispatcherType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration @EnableWebSecurity
public class PedidosSecurityConfig {
    @Bean
    SecretKey jwtSecretKey(@Value("${jwt.secret-base64:}") String encoded) {
        if(encoded==null || encoded.isBlank()) throw new IllegalStateException("JWT_SECRET_BASE64 obligatorio");
        byte[] decoded;
        try {decoded=Base64.getDecoder().decode(encoded.trim());}
        catch(IllegalArgumentException e){throw new IllegalStateException("JWT_SECRET_BASE64 invalido",e);}
        if(decoded.length<32) throw new IllegalStateException("La clave JWT necesita 32+ bytes");
        return new SecretKeySpec(decoded,"HmacSHA256");
    }
    @Bean
    JwtDecoder jwtDecoder(SecretKey key,@Value("${jwt.issuer:huertohogar-usuarios}") String issuer) {
        var decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter=new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String rol=jwt.getClaimAsString("rol");
            if(!List.of("CLIENTE","VENDEDOR","ADMINISTRADOR").contains(rol))
                return List.<GrantedAuthority>of();
            return List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_"+rol));
        });
        return converter;
    }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationConverter converter) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable).cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Conserva el codigo original de error en el dispatch interno /error.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET,"/swagger-ui.html","/swagger-ui/**",
                    "/v3/api-docs","/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/pedidos").hasRole("CLIENTE")
                .requestMatchers(HttpMethod.GET,"/api/v1/pedidos/mios").hasRole("CLIENTE")
                .requestMatchers(HttpMethod.GET,"/api/v1/pedidos/pendientes").hasAnyRole("VENDEDOR","ADMINISTRADOR")
                .requestMatchers(HttpMethod.GET,"/api/v1/pedidos/*").hasAnyRole("CLIENTE","VENDEDOR","ADMINISTRADOR")
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter))).build();
    }
    @Bean
    CorsConfigurationSource cors(@Value("${app.cors.allowed-origins:}") String origins) {
        CorsConfiguration c=new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim)
            .filter(s -> !s.isBlank()).toList());
        c.setAllowedMethods(List.of("GET","POST","OPTIONS"));
        c.setAllowedHeaders(List.of("Content-Type","Authorization","Idempotency-Key"));
        c.setExposedHeaders(List.of("Location"));
        var source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**",c);
        return source;
    }
}