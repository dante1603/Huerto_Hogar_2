package cl.huertohogar.boleta.remote;

public interface PedidoGateway {
    PedidoVerificado obtener(Long pedidoId, String jwtCliente);
}
