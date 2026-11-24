package cl.huertohogar.detalleboleta.api;

import cl.huertohogar.detalleboleta.service.DetalleService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/detalles-boleta")
public class DetalleBoletaController {
    private final DetalleService service;
    public DetalleBoletaController(DetalleService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<List<DetalleResponse>> generar(
            @Valid @RequestBody GenerarDetalleRequest request, @AuthenticationPrincipal Jwt jwt) {
        var resultado = service.generar(request.boletaId(), jwt);
        return ResponseEntity.status(resultado.nuevo() ? HttpStatus.CREATED : HttpStatus.OK)
            .location(URI.create("/api/v1/detalles-boleta/boleta/" + request.boletaId()))
            .body(resultado.lineas());
    }

    @GetMapping("/boleta/{boletaId}")
    public List<DetalleResponse> porBoleta(@PathVariable Long boletaId, @AuthenticationPrincipal Jwt jwt) {
        return service.porBoleta(boletaId, jwt);
    }

    @GetMapping("/{id}")
    public DetalleResponse porId(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.porId(id, jwt);
    }
}
