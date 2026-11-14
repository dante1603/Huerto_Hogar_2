package cl.huertohogar.catalogo.service;

import cl.huertohogar.catalogo.domain.Producto;
import cl.huertohogar.catalogo.persistence.ProductoRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductoService {
    private final ProductoRepository repository;

    public ProductoService(ProductoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Producto> listar() {
        return repository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Producto obtener(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Producto no encontrado"));
    }
}
