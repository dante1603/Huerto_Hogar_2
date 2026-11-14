package cl.huertohogar.catalogo.service;

import cl.huertohogar.catalogo.api.ProductoMutationRequest;
import cl.huertohogar.catalogo.domain.Producto;
import cl.huertohogar.catalogo.persistence.LineaPedidoRepository;
import cl.huertohogar.catalogo.persistence.ProductoRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductoService {
    private final ProductoRepository repository;
    private final LineaPedidoRepository lineas;

    public ProductoService(ProductoRepository repository, LineaPedidoRepository lineas) {
        this.repository = repository;
        this.lineas = lineas;
    }

    @Transactional(readOnly = true)
    public List<Producto> listar() {
        return repository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Producto obtener(Long id) {
        return repository.findById(id).orElseThrow(ProductoService::noEncontrado);
    }

    @Transactional
    public Producto crear(ProductoMutationRequest request) {
        String sku = request.sku().strip();
        if (repository.existsBySku(sku)) throw skuDuplicado();
        Producto producto = new Producto(sku, request.nombre().strip(),
            request.descripcion(), request.precio(), request.stock(),
            request.planetaOrigen().strip());
        try {
            return repository.saveAndFlush(producto);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No se pudo crear el producto: datos duplicados", ex);
        }
    }

    @Transactional
    public Producto actualizar(Long id, ProductoMutationRequest request) {
        Producto producto = repository.findPorIdParaReserva(id)
            .orElseThrow(ProductoService::noEncontrado);
        String sku = request.sku().strip();
        if (repository.existsBySkuAndIdNot(sku, id)) throw skuDuplicado();
        producto.actualizar(sku, request.nombre().strip(), request.descripcion(),
            request.precio(), request.stock(), request.planetaOrigen().strip());
        try {
            return repository.saveAndFlush(producto);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No se pudo actualizar el producto: datos duplicados", ex);
        }
    }

    @Transactional
    public void eliminar(Long id) {
        // Mismo bloqueo que una reserva; conserva historial e idempotencia.
        Producto producto = repository.findPorIdParaReserva(id)
            .orElseThrow(ProductoService::noEncontrado);
        if (lineas.existsByProductoId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "El producto tiene pedidos y no puede eliminarse");
        }
        repository.delete(producto);
        repository.flush();
    }

    private static ResponseStatusException skuDuplicado() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un producto con ese SKU");
    }

    private static ResponseStatusException noEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
    }
}
