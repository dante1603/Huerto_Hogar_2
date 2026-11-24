package cl.huertohogar.detalleboleta.api;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GenerarDetalleRequest(@NotNull @Positive Long boletaId) {}
