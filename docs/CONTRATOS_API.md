# Contratos REST — estado real

Todos los servicios usan JSON y prefijo `/api/v1`. Esta tabla distingue expresamente **implementado** de **diseñado**.

| Ruta | Método | Servicio | Estado |
|---|---|---|---|
| `/api/v1/productos` | GET | catálogo | Implementado; lectura JPA |
| `/api/v1/productos/{id}` | GET | catálogo | Implementado; 404 si no existe |
| `/api/v1/usuarios` | POST | usuarios | Registro público CLIENTE; sin asignación de rol privilegiado |
| `/api/v1/usuarios/{id}` | GET | usuarios | Implementado: Bearer JWT, solo propietario o ADMINISTRADOR |
| `/api/v1/auth/login` | POST | usuarios | Implementado: valida BCrypt y emite JWT HS256 de 30 minutos |
| `/api/v1/pedidos` | POST/GET | pedidos | Pendiente |
| `/api/v1/entregas/{id}/telemetria` | POST/GET | entregas | Pendiente |

## Producto actual

```json
{
  "id": 1,
  "sku": "BRU-TUB-001",
  "nombre": "Tuberculo de Bruma",
  "descripcion": "Cultivo de raiz del planeta Bruma",
  "precio": 120,
  "stock": 15,
  "planetaOrigen": "Bruma"
}
```

`GET /productos` devuelve array JSON; `GET /productos/{id}` un objeto o 404. Los productos precargados solo existen con perfil `demo`, no con MySQL.

## Flujo objetivo (NO implementado)

1. El juego informa inventario del cofre mediante un servicio autenticado y versionado.
2. La base conserva el stock disponible para pedidos y reservas transaccionales.
3. React envía solicitud de pedido; Spring Boot valida y reserva stock sin permitir ventas dobles.
4. El piloto en Galaxym acepta una entrega y registra transiciones autorizadas.
5. El juego publica telemetría espacial con marcas temporales y coordenadas en marco solar; la tienda visualiza con el visor orbital reutilizado.
6. El cliente entrega código de confirmación de un solo uso; la API valida cierre.

No acoplar el frontend directamente a MySQL ni dar acceso privilegiado a tokens del juego. No confiar en el estado reportado por el navegador sin autorización y transacciones. Si el juego no transmite posición, el seguimiento mostrará **sin señal**, no posiciones inventadas.
