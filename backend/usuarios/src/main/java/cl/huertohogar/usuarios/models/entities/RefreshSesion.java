package cl.huertohogar.usuarios.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "refresh_sesion")
public class RefreshSesion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Solo SHA-256 del token; nunca guardar el secreto que se entrega al cliente.
    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    protected RefreshSesion() { }

    public RefreshSesion(String tokenHash, Long usuarioId, Instant expiraEn) {
        this.tokenHash = tokenHash;
        this.usuarioId = usuarioId;
        this.expiraEn = expiraEn;
    }

    public String getTokenHash() { return tokenHash; }
    public Long getUsuarioId() { return usuarioId; }
    public Instant getExpiraEn() { return expiraEn; }
}
