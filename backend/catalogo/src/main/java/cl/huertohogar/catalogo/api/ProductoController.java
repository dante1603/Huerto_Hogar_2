package cl.huertohogar.catalogo.api;

import cl.huertohogar.catalogo.domain.Producto;
import cl.huertohogar.catalogo.persistence.ProductoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/productos")
@Tag(name = "Productos", description = "Catalogo publico de productos galacticos")
public class ProductoController {
    private final ProductoRepository repository;

    public ProductoController(ProductoRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Operation(summary = "Listar productos ordenados por ID")
    public List<ProductoResponse> listar() {
        return repository.findAllByOrderByIdAsc().stream()
            .map(ProductoController::toResponse)
            .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un producto por ID")
    public ProductoResponse obtener(@PathVariable Long id) {
        Producto producto = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Producto no encontrado"));
        return toResponse(producto);
    }

    private static ProductoResponse toResponse(Producto p) {
        return new ProductoResponse(
            p.getId(), p.getSku(), p.getNombre(), p.getDescripcion(),
            p.getPrecio(), p.getStock(), p.getPlanetaOrigen());
    }
}
