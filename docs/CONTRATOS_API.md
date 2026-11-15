# Contratos REST — estado real

Todos los servicios usan JSON y prefijo `/api/v1`. Esta tabla distingue expresamente **implementado** de **diseñado**.

| Ruta | Método | Servicio | Estado |
|---|---|---|---|
| `/api/v1/productos` | GET | catálogo | Implementado; lectura JPA |
| `/api/v1/productos/{id}` | GET | catálogo | Implementado; 404 si no existe |
| `/api/v1/productos` | POST | catálogo | ADMINISTRADOR JWT; 201 + Location; 400 inválido; 409 SKU duplicado |
| `/api/v1/productos/{id}` | PUT | catálogo | ADMINISTRADOR JWT; 200 o 400/404/409 |
| `/api/v1/productos/{id}` | DELETE | catálogo | ADMINISTRADOR JWT; 204, 404, 409 si hay pedidos |
| `/api/v1/usuarios` | POST | usuarios | Registro público CLIENTE; sin asignación de rol privilegiado |
| `/api/v1/usuarios/{id}` | GET | usuarios | Implementado: Bearer JWT, solo propietario o ADMINISTRADOR |
| `/api/v1/auth/login` | POST | usuarios | Implementado: valida BCrypt y emite JWT HS256 de 30 minutos |
| `/api/v1/auth/refresh` | POST | usuarios | Rota token de renovación opaco de un solo uso, reemite JWT (24 h máximo de refresh) |
| `/api/v1/auth/logout` | POST | usuarios | Revoca refresh token, respuesta 204 sin revelar su existencia |
| `/api/v1/pedidos` | POST | Catálogo | MVP: CLIENTE JWT, reserva transaccional, Idempotency-Key UUID |
| `/api/v1/pedidos/mios` | GET | Catálogo | Pedidos del CLIENTE autenticado |
| `/api/v1/pedidos/pendientes` | GET | Catálogo | VENDEDOR/ADMINISTRADOR (solo lectura) |
| `/api/v1/pedidos/{id}` | GET | Catálogo | Propietario o roles VENDEDOR/ADMINISTRADOR |
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
3. React envía solicitud de pedido; Spring Boot valida y reserva stock sin permitir ventas dobles (**incluido en MVP**).
4. El piloto en Galaxym acepta una entrega y registra transiciones autorizadas.
5. El juego publica telemetría espacial con marcas temporales y coordenadas en marco solar; la tienda visualiza con el visor orbital reutilizado.
6. El cliente entrega código de confirmación de un solo uso; la API valida cierre.

No acoplar el frontend directamente a MySQL ni dar acceso privilegiado a tokens del juego. No confiar en el estado reportado por el navegador sin autorización y transacciones. Si el juego no transmite posición, el seguimiento mostrará **sin señal**, no posiciones inventadas.

## CRUD de productos, Evaluación Parcial 3

El catálogo mantiene lectura pública. Las escrituras requieren JWT `ADMINISTRADOR` y no pueden iniciarse con una cuenta CLIENTE o VENDEDOR. El payload POST y PUT exige SKU, nombre, descripción, precio entero no negativo, stock no negativo y planeta de origen. El ID lo genera la BD.

Para preservar la integridad del historial, DELETE devuelve **409** si existen líneas de pedido referidas al producto. El procedimiento de actualización usa el mismo bloqueo de fila que una reserva. Swagger está configurado en `/swagger-ui.html` y OpenAPI en `/v3/api-docs`; comprobar disponibilidad visual con servicios arrancados y MySQL real antes del cierre.

## Renovación de autenticación — backend EP3

Login entrega JWT HS256 (30 minutos) y refreshToken aleatorio de 256 bits (24 horas). La base Usuarios conserva **solo SHA-256** del refresh token en una nueva tabla `refresh_sesion`. POST `/api/v1/auth/refresh` exige JSON `{"refreshToken":"..."}` y rota la credencial de un solo uso en transacción con bloqueo pesimista. Reutilizar la credencial anterior produce HTTP 401. POST `/api/v1/auth/logout` revoca la credencial y responde HTTP 204 incluso si ya no existe. El JWT vigente no se revoca de inmediato: expira naturalmente a los 30 minutos (limitación del MVP).

Nota: los refresh tokens son secretos y no deben mostrarse en logs, URLs ni capturas. La conexión y persistencia MySQL real aún requieren validación final; en CI se usa H2.


## Contrato REST Productos / Pedidos (primer corte cinco microservicios)

PUT/GET/DELETE /api/v1/interno/reservas/{referencia} requieren JWT SERVICIO_PEDIDOS. PUT acepta productos[{productoId,cantidad}]; crea reserva atómica (201), reintenta mismo UUID sin descontar (200), o devuelve 400/404/409. GET consulta snapshot y estado para reconciliar timeouts. DELETE compensa stock solo una vez y conserva tombstone de CANCELADA (204). Precio proviene del backend, no del navegador. Consulte docs/ARQUITECTURA_5_MICROSERVICIOS_EP3.md. **Existe un tercer proyecto Pedidos independiente (PR #21) con controlador y gateway, pero React aún usa Pedidos legado del Catálogo; no se ha verificado comunicación distribuida ni MySQL.**
