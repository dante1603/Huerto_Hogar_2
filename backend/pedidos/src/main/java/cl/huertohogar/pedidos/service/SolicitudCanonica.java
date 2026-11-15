package cl.huertohogar.pedidos.service;
import cl.huertohogar.pedidos.api.CrearPedidoRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public final class SolicitudCanonica {
    private SolicitudCanonica() {}
    public static List<CrearPedidoRequest.Item> items(CrearPedidoRequest req) {
        var items=new ArrayList<>(req.productos());
        items.sort(Comparator.comparing(CrearPedidoRequest.Item::productoId));
        for(int i=1;i<items.size();i++) if(items.get(i-1).productoId().equals(items.get(i).productoId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Producto duplicado");
        return List.copyOf(items);
    }
    public static String huella(String destino,List<CrearPedidoRequest.Item> items) {
        var s=new StringBuilder(destino).append('|');
        for(var item:items) s.append(item.productoId()).append(':').append(item.cantidad()).append(';');
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(s.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 no disponible",e);}
    }
}