package cl.huertohogar.usuarios.services;

public class UsuarioNoEncontradoException extends RuntimeException {
    public UsuarioNoEncontradoException(String message) { super(message); }
}
