package cl.huertohogar.catalogo.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Solo el servicio Pedidos autenticado puede solicitar esta operación. */
public record ReservaStockRequest(
    @NotEmpty @Size(max = 30) List<@NotNull @Valid Item> productos
) {
    public record Item(
        @NotNull @Min(1) Long productoId,
        @NotNull @Min(1) @Max(999) Integer cantidad
    ) { }
}
