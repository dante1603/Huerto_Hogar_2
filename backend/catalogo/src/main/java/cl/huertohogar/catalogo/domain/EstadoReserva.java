package cl.huertohogar.catalogo.domain;

/** Estado terminal de una reserva de stock, conservado para idempotencia. */
public enum EstadoReserva {
    RESERVADA,
    CANCELADA
}
