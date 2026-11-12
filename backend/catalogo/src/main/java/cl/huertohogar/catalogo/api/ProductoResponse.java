package cl.huertohogar.catalogo.api;

import java.math.BigDecimal;

public record ProductoResponse(
    Long id,
    String sku,
    String nombre,
    String descripcion,
    BigDecimal precio,
    Integer stock,
    String planetaOrigen
) { }
