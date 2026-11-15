package cl.huertohogar.pedidos;

import cl.huertohogar.pedidos.api.CrearPedidoRequest;
import cl.huertohogar.pedidos.domain.EstadoPedido;
import cl.huertohogar.pedidos.persistence.PedidoRepository;
import cl.huertohogar.pedidos.remote.ProductoReservaGateway;
import cl.huertohogar.pedidos.remote.ReservaIndeterminadaException;
import cl.huertohogar.pedidos.remote.ReservaRemota;
import cl.huertohogar.pedidos.service.PedidoOrquestador;
import cl.huertohogar.pedidos.service.PedidoPersistencia;
import cl.huertohogar.pedidos.service.ReconciliacionExpiradas;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties={
    "jwt.secret-base64=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=",
    "jwt.issuer=huertohogar-usuarios",
    "reconciliacion.habilitada=false"
})
@ActiveProfiles("demo")
class ReconciliacionExpiradasTest {
    @Autowired PedidoRepository repository;
    @Autowired PedidoPersistencia persistencia;
    @Autowired ReconciliacionExpiradas reconciliacion;
    @Autowired PedidoOrquestador orquestador;
    @MockitoBean ProductoReservaGateway gateway;

    @BeforeEach
    void antes() {
        repository.deleteAll();
        reset(gateway);
    }

    private PedidoPersistencia.Intento crearIntencion() {
        return persistencia.iniciar(42L, UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), "huella-local-de-prueba", "Bruma");
    }

    private static ReservaRemota snapshot(String ref) {
        return new ReservaRemota(ref, "RESERVADA", BigDecimal.valueOf(100), Instant.now(),
            List.of(new ReservaRemota.Linea(1L, "Cultivo", 1,
                BigDecimal.valueOf(100), BigDecimal.valueOf(100))));
    }

    @Test void devuelveStockYConfirmaCancelacionSoloTrasRespuestaRemota() {
        var intento = crearIntencion();
        int cerradas = reconciliacion.revisar(Instant.now().plusSeconds(10));
        assertEquals(1, cerradas);
        assertEquals(EstadoPedido.CANCELADO, repository.findById(intento.id()).orElseThrow().getEstado());
        verify(gateway, times(1)).cancelar(intento.referencia());
        assertEquals(0, reconciliacion.revisar(Instant.now().plusSeconds(10)));
        verify(gateway, times(1)).cancelar(intento.referencia());
    }

    @Test void falloDeRedMantieneCancelandoYRondaPosteriorEsIdempotente() {
        var intento = crearIntencion();
        doThrow(new ReservaIndeterminadaException(new RuntimeException("timeout")))
            .when(gateway).cancelar(anyString());
        assertEquals(0, reconciliacion.revisar(Instant.now().plusSeconds(10)));
        assertEquals(EstadoPedido.CANCELANDO,
            repository.findById(intento.id()).orElseThrow().getEstado());
        reset(gateway);
        assertEquals(1, reconciliacion.revisar(Instant.now().plusSeconds(10)));
        assertEquals(EstadoPedido.CANCELADO,
            repository.findById(intento.id()).orElseThrow().getEstado());
        verify(gateway, times(1)).cancelar(intento.referencia());
    }

    @Test void intencionRecienteNoSeCancela() {
        var intento = crearIntencion();
        assertEquals(0, reconciliacion.revisar(Instant.now().minusSeconds(3600)));
        assertEquals(EstadoPedido.INICIADO,
            repository.findById(intento.id()).orElseThrow().getEstado());
        verifyNoInteractions(gateway);
    }

    @Test void pedidoConfirmadoNuncaSeCompensa() {
        var intento = crearIntencion();
        persistencia.confirmar(intento.id(), snapshot(intento.referencia()));
        assertEquals(0, reconciliacion.revisar(Instant.now().plusSeconds(10)));
        assertEquals(EstadoPedido.PENDIENTE,
            repository.findById(intento.id()).orElseThrow().getEstado());
        verifyNoInteractions(gateway);
    }

    @Test void confirmacionTardiaNoReviveReservaEnCancelacion() {
        var intento = crearIntencion();
        assertEquals(intento.referencia(),
            persistencia.iniciarCancelacion(intento.id(), Instant.now().plusSeconds(10)));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> persistencia.confirmar(intento.id(), snapshot(intento.referencia())));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(EstadoPedido.CANCELANDO,
            repository.findById(intento.id()).orElseThrow().getEstado());
    }

    @Test void mismaClaveCanceladaNoReiniciaReservaEnProductos() {
        String clave = UUID.randomUUID().toString();
        var req = new CrearPedidoRequest("Bruma",
            List.of(new CrearPedidoRequest.Item(1L, 1)));
        when(gateway.reservar(anyString(), anyList()))
            .thenThrow(new ReservaIndeterminadaException(new RuntimeException("timeout")));
        when(gateway.consultar(anyString()))
            .thenThrow(new ReservaIndeterminadaException(new RuntimeException("sin red")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
            () -> orquestador.crear(42L, clave, req)).getStatusCode());
        assertEquals(1, reconciliacion.revisar(Instant.now().plusSeconds(10)));
        assertEquals(EstadoPedido.CANCELADO, repository.findAll().get(0).getEstado());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> orquestador.crear(42L, clave, req)).getStatusCode());
        verify(gateway, times(1)).reservar(anyString(), anyList());
    }
}
