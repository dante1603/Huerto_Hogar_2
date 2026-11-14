package cl.huertohogar.catalogo.api;

import cl.huertohogar.catalogo.domain.Producto;
import cl.huertohogar.catalogo.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productos")
@Tag(name = "Productos", description = "Catalogo publico y gestion administrativa JWT")
public class ProductoController {
    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar productos ordenados por ID (publico)")
    public List<ProductoResponse> listar() {
        return service.listar().stream().map(ProductoController::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un producto por ID (publico)")
    public ProductoResponse obtener(@PathVariable Long id) {
        return toResponse(service.obtener(id));
    }

    @PostMapping
    @Operation(summary = "Crear producto (solo ADMINISTRADOR)")
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoMutationRequest request) {
        Producto producto = service.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/productos/" + producto.getId()))
            .body(toResponse(producto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar producto completo (solo ADMINISTRADOR)")
    public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoMutationRequest request) {
        return toResponse(service.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar producto sin pedidos asociados (solo ADMINISTRADOR)")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private static ProductoResponse toResponse(Producto p) {
        return new ProductoResponse(p.getId(), p.getSku(), p.getNombre(),
            p.getDescripcion(), p.getPrecio(), p.getStock(), p.getPlanetaOrigen());
    }
}
