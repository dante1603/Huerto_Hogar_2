package cl.huertohogar.detalleboleta.remote;
public interface PedidoGateway {
    PedidoVerificado obtener(Long pedidoId, String jwt);
}
