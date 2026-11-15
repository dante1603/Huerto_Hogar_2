package cl.huertohogar.boleta.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EmitirBoletaRequest(@NotNull @Positive Long pedidoId) {}
