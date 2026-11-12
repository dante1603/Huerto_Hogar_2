package cl.huertohogar.catalogo.api;

import cl.huertohogar.catalogo.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/pedidos")
@Tag(name = "Pedidos", description = "Reserva de inventario, sin pagos ni entregas todavia")
public class PedidoController {
    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Crear pedido PENDIENTE y reservar stock atomico")
    public ResponseEntity<PedidoResponse> crear(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CrearPedidoRequest request) {

        Long clienteId = actorId(jwt);
        String key = canonicalUuid(idempotencyKey);
        ResultadoCreacion result;
        try {
            result = service.crear(clienteId, key, request);
        } catch (DataIntegrityViolationException duplicateRace) {
            // Un intento concurrente con la misma clave ganó la restricción UNIQUE.
            // Consulta nueva tras rollback de la transacción; no crea otro pedido.
            result = new ResultadoCreacion(service.recuperarIntento(clienteId, key, request), false);
        }
        return ResponseEntity.status(result.nuevo() ? HttpStatus.CREATED : HttpStatus.OK)
            .location(URI.create("/api/v1/pedidos/" + result.pedido().id()))
            .body(result.pedido());
    }

    @GetMapping("/mios")
    @Operation(summary = "Pedidos del cliente autenticado")
    public List<PedidoResponse> mios(@AuthenticationPrincipal Jwt jwt) {
        return service.mios(actorId(jwt));
    }

    @GetMapping("/pendientes")
    @Operation(summary = "Pedidos pendientes para VENDEDOR o ADMINISTRADOR")
    public List<PedidoResponse> pendientes() {
        return service.pendientes();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar pedido propio o como vendedor/administrador")
    public PedidoResponse detalle(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.detalle(id, actorId(jwt), jwt.getClaimAsString("rol"));
    }

    private static Long actorId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidad JWT no valida");
        }
    }

    private static String canonicalUuid(String raw) {
        try {
            UUID parsed = UUID.fromString(raw);
            if (!parsed.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException();
            return parsed.toString();
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Idempotency-Key debe ser UUID canonico");
        }
    }
}
