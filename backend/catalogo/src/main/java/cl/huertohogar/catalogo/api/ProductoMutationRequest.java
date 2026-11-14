package cl.huertohogar.catalogo.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** La identidad del producto se asigna en BD, no desde el formulario. */
public record ProductoMutationRequest(
    @NotBlank @Size(max = 40) String sku,
    @NotBlank @Size(max = 120) String nombre,
    @NotNull @Size(max = 500) String descripcion,
    @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal precio,
    @NotNull @Min(0) Integer stock,
    @NotBlank @Size(max = 60) String planetaOrigen
) {}
