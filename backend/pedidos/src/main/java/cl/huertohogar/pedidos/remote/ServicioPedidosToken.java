package cl.huertohogar.pedidos.remote;
import java.time.Instant;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.stereotype.Component;

/** Token de servicio efímero; no se expone al navegador ni al registro publico. */
@Component
public class ServicioPedidosToken {
    private final JwtEncoder encoder;
    private final String issuer;
    public ServicioPedidosToken(SecretKey key,@Value("${jwt.issuer:huertohogar-usuarios}") String issuer) {
        this.encoder=new NimbusJwtEncoder(new ImmutableSecret<>(key));this.issuer=issuer;
    }
    public String emitir() {
        Instant now=Instant.now();
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer(issuer).subject("servicio-pedidos")
            .issuedAt(now).expiresAt(now.plusSeconds(60))
            .claim("rol","SERVICIO_PEDIDOS").build();
        return encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
}