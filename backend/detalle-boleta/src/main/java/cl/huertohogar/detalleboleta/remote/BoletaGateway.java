package cl.huertohogar.detalleboleta.remote;
public interface BoletaGateway {
    BoletaVerificada obtener(Long boletaId, String jwt);
}
