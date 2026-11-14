package cl.huertohogar.catalogo.api;

import cl.huertohogar.catalogo.domain.Producto;
import cl.huertohogar.catalogo.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productos")
@Tag(name = "Productos", description = "Catalogo publico de productos galacticos")
public class ProductoController {
    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar productos ordenados por ID")
    public List<ProductoResponse> listar() {
        return service.listar().stream()
            .map(ProductoController::toResponse)
            .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un producto por ID")
    public ProductoResponse obtener(@PathVariable Long id) {
        Producto producto = service.obtener(id);
        return toResponse(producto);
    }

    private static ProductoResponse toResponse(Producto p) {
        return new ProductoResponse(
            p.getId(), p.getSku(), p.getNombre(), p.getDescripcion(),
            p.getPrecio(), p.getStock(), p.getPlanetaOrigen());
    }
}
