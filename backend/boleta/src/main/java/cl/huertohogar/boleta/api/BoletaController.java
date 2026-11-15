package cl.huertohogar.boleta.api;

import cl.huertohogar.boleta.service.BoletaService;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/api/v1/boletas")
public class BoletaController {
    private final BoletaService service;
    public BoletaController(BoletaService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<BoletaResponse> emitir(@Valid @RequestBody EmitirBoletaRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        var resultado = service.emitir(request.pedidoId(), jwt);
        return ResponseEntity.status(resultado.nueva() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(URI.create("/api/v1/boletas/" + resultado.boleta().id()))
                .body(resultado.boleta());
    }

    @GetMapping("/{id}")
    public BoletaResponse obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.obtener(id, jwt);
    }
}
