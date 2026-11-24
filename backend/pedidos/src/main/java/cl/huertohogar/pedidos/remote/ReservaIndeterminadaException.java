package cl.huertohogar.pedidos.remote;

/** El servidor Productos puede haber confirmado PUT antes de cortar la red. */
public class ReservaIndeterminadaException extends RuntimeException {
    public ReservaIndeterminadaException(Throwable cause) {
        super("La reserva remota requiere reconciliacion",cause);
    }
}