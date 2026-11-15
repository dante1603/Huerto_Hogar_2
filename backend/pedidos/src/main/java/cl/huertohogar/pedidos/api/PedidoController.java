package cl.huertohogar.pedidos.api;
import cl.huertohogar.pedidos.service.PedidoOrquestador;
import cl.huertohogar.pedidos.service.PedidoPersistencia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
@Tag(name="Pedidos",description="Servicio independiente con reservas por API interna")
public class PedidoController {
    private final PedidoOrquestador orquestador;
    private final PedidoPersistencia persistencia;
    public PedidoController(PedidoOrquestador o,PedidoPersistencia p){orquestador=o;persistencia=p;}
    @PostMapping @Operation(summary="Crear pedido idempotente con reserva atomica de Productos")
    public ResponseEntity<PedidoResponse> crear(@AuthenticationPrincipal Jwt jwt,
        @RequestHeader("Idempotency-Key") String clave,@Valid @RequestBody CrearPedidoRequest req) {
        String key=uuid(clave);
        var r=orquestador.crear(actor(jwt),key,req);
        return ResponseEntity.status(r.nuevo()?HttpStatus.CREATED:HttpStatus.OK)
            .location(URI.create("/api/v1/pedidos/"+r.pedido().id())).body(r.pedido());
    }
    @GetMapping("/mios") public List<PedidoResponse> mios(@AuthenticationPrincipal Jwt jwt) {
        return persistencia.mios(actor(jwt));
    }
    @GetMapping("/pendientes") public List<PedidoResponse> pendientes(){return persistencia.pendientes();}
    @GetMapping("/{id}") public PedidoResponse detalle(@PathVariable Long id,@AuthenticationPrincipal Jwt jwt){
        return persistencia.detalle(id,actor(jwt),jwt.getClaimAsString("rol"));
    }
    private static Long actor(Jwt jwt) {
        try{return Long.valueOf(jwt.getSubject());}
        catch(NumberFormatException|NullPointerException e){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"JWT sin identidad valida");
        }
    }
    private static String uuid(String raw){
        try {
            UUID id=UUID.fromString(raw);
            if(!id.toString().equalsIgnoreCase(raw))throw new IllegalArgumentException();
            return id.toString();
        } catch(RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key debe ser UUID canonico");
        }
    }
}