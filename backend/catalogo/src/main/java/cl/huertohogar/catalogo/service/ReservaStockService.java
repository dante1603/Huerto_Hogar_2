package cl.huertohogar.catalogo.service;

import cl.huertohogar.catalogo.api.ReservaStockRequest;
import cl.huertohogar.catalogo.api.ReservaStockResponse;
import cl.huertohogar.catalogo.api.ReservaStockResultado;
import cl.huertohogar.catalogo.domain.EstadoReserva;
import cl.huertohogar.catalogo.domain.LineaReserva;
import cl.huertohogar.catalogo.domain.Producto;
import cl.huertohogar.catalogo.domain.ReservaStock;
import cl.huertohogar.catalogo.persistence.ProductoRepository;
import cl.huertohogar.catalogo.persistence.ReservaStockRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Frontera REST entre Pedidos y Productos, todavía alojada dentro de Catálogo.
 * No sustituye la transacción del PedidoService legado en este corte.
 */
@Service
public class ReservaStockService {
    private final ReservaStockRepository reservas;
    private final ProductoRepository productos;

    public ReservaStockService(ReservaStockRepository reservas, ProductoRepository productos) {
        this.reservas = reservas;
        this.productos = productos;
    }

    @Transactional
    public ReservaStockResultado reservar(String referencia, ReservaStockRequest solicitud) {
        List<ReservaStockRequest.Item> items = normalizar(solicitud);
        String huella = huella(items);

        var previa = reservas.findBloqueada(referencia);
        if (previa.isPresent()) {
            return new ReservaStockResultado(repeticionValida(previa.get(), huella), false);
        }

        // Orden estable: evita bloqueos cruzados y coincide con PedidoService legado.
        var bloqueados = new ArrayList<Producto>();
        for (var item : items) {
            Producto producto = productos.findPorIdParaReserva(item.productoId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Producto no existe: " + item.productoId()));
            bloqueados.add(producto);
        }

        // Otro hilo pudo confirmar o cancelar esta referencia mientras esperabamos el lock.
        var concurrente = reservas.findBloqueada(referencia);
        if (concurrente.isPresent()) {
            return new ReservaStockResultado(repeticionValida(concurrente.get(), huella), false);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            Producto producto = bloqueados.get(i);
            int cantidad = items.get(i).cantidad();
            if (producto.getStock() < cantidad) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Inventario insuficiente para " + producto.getNombre());
            }
            total = total.add(producto.getPrecio().multiply(BigDecimal.valueOf(cantidad)));
        }
        if (total.precision() > 14) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El total de reserva excede el limite");
        }

        var reserva = new ReservaStock(referencia, huella);
        for (int i = 0; i < items.size(); i++) {
            Producto producto = bloqueados.get(i);
            int cantidad = items.get(i).cantidad();
            producto.reservar(cantidad);
            reserva.agregarLinea(producto, cantidad);
        }
        reservas.saveAndFlush(reserva);
        return new ReservaStockResultado(ReservaStockResponse.desde(reserva), true);
    }

    @Transactional(readOnly = true)
    public ReservaStockResponse recuperarIntento(String referencia, ReservaStockRequest solicitud) {
        String huella = huella(normalizar(solicitud));
        ReservaStock reserva = reservas.findById(referencia)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                "No se pudo reconciliar el intento de reserva"));
        return repeticionValida(reserva, huella);
    }

    @Transactional(readOnly = true)
    public ReservaStockResponse consultar(String referencia) {
        ReservaStock reserva = reservas.findById(referencia)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Referencia de reserva desconocida"));
        return ReservaStockResponse.desde(reserva);
    }

    /**
     * Compensacion idempotente: una cancelacion prematura crea tombstone.
     * Repetir DELETE jamas repone stock por segunda vez.
     */
    @Transactional
    public void cancelar(String referencia) {
        var registrada = reservas.findBloqueada(referencia);
        if (registrada.isEmpty()) {
            reservas.saveAndFlush(ReservaStock.canceladaSinReserva(referencia));
            return;
        }
        ReservaStock reserva = registrada.get();
        if (reserva.getEstado() == EstadoReserva.CANCELADA) return;

        List<LineaReserva> lineas = reserva.getLineas().stream()
            .sorted(Comparator.comparing(LineaReserva::getProductoId)).toList();
        var bloqueados = new ArrayList<Producto>();
        for (LineaReserva linea : lineas) {
            Producto producto = productos.findPorIdParaReserva(linea.getProductoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede compensar: producto de reserva ya no existe"));
            bloqueados.add(producto);
        }
        for (int i = 0; i < lineas.size(); i++) {
            bloqueados.get(i).reponer(lineas.get(i).getCantidad());
        }
        reserva.cancelar();
        reservas.flush();
    }

    private static List<ReservaStockRequest.Item> normalizar(ReservaStockRequest solicitud) {
        var items = new ArrayList<>(solicitud.productos());
        items.sort(Comparator.comparing(ReservaStockRequest.Item::productoId));
        for (int i = 1; i < items.size(); i++) {
            if (items.get(i - 1).productoId().equals(items.get(i).productoId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se permite repetir el mismo producto en una reserva");
            }
        }
        return items;
    }

    private static ReservaStockResponse repeticionValida(ReservaStock reserva, String huella) {
        if (reserva.getEstado() == EstadoReserva.CANCELADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Referencia cancelada: no se puede volver a reservar");
        }
        if (!reserva.getHuellaSolicitud().equals(huella)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La referencia ya se utilizo para productos o cantidades diferentes");
        }
        return ReservaStockResponse.desde(reserva);
    }

    private static String huella(List<ReservaStockRequest.Item> items) {
        var canonical = new StringBuilder();
        for (var item : items) {
            canonical.append(item.productoId()).append(':').append(item.cantidad()).append(';');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 requerido", e);
        }
    }
}
