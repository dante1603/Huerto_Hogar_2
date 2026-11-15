package cl.huertohogar.catalogo.api;

import cl.huertohogar.catalogo.service.ReservaStockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/interno/reservas")
@Tag(name = "Reservas internas", description = "Contrato de Productos para futuro microservicio Pedidos")
public class ReservaStockController {
    private final ReservaStockService service;

    public ReservaStockController(ReservaStockService service) {
        this.service = service;
    }

    @PutMapping("/{referencia}")
    @Operation(summary = "Reservar stock de forma idempotente, solo SERVICIO_PEDIDOS")
    public ResponseEntity<ReservaStockResponse> reservar(
        @PathVariable String referencia, @Valid @RequestBody ReservaStockRequest request) {
        String key = validarUuid(referencia);
        ReservaStockResultado resultado;
        try {
            resultado = service.reservar(key, request);
        } catch (DataIntegrityViolationException duplicateRace) {
            // Un hilo pudo ganar el UNIQUE de la referencia: transaccion original revertida.
            resultado = new ReservaStockResultado(service.recuperarIntento(key, request), false);
        }
        return ResponseEntity
            .status(resultado.nueva() ? HttpStatus.CREATED : HttpStatus.OK)
            .location(URI.create("/api/v1/interno/reservas/" + key))
            .body(resultado.reserva());
    }

    @GetMapping("/{referencia}")
    @Operation(summary = "Reconciliar reserva tras timeout de red, solo SERVICIO_PEDIDOS")
    public ReservaStockResponse consultar(@PathVariable String referencia) {
        return service.consultar(validarUuid(referencia));
    }

    @DeleteMapping("/{referencia}")
    @Operation(summary = "Compensacion idempotente con tombstone, solo SERVICIO_PEDIDOS")
    public ResponseEntity<Void> cancelar(@PathVariable String referencia) {
        String key = validarUuid(referencia);
        try {
            service.cancelar(key);
        } catch (DataIntegrityViolationException duplicateRace) {
            // Conflicto de primera reserva vs cancelacion; repetir tras rollback.
            service.cancelar(key);
        }
        return ResponseEntity.noContent().build();
    }

    private static String validarUuid(String raw) {
        try {
            UUID uuid = UUID.fromString(raw);
            if (!uuid.toString().equals(raw)) throw new IllegalArgumentException();
            return uuid.toString();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "referencia debe ser UUID canonico");
        }
    }
}
