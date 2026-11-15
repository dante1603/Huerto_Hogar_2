# Informe de APIs e Integración — entrega grupal EP3

**Versión 1.0.** Base: controladores Java y cliente HTTP React en integration/fullstack-2026-11-14. **PASS 71 tests Java + circuito REST real entre cinco aplicaciones/H2 en PR #27; faltan navegador y MySQL real.** Basado en anexos 2 y 3 de DSY1104: usar contratos reales, no los ejemplos de Pokémon.

## 1. Arquitectura

Frontend React/Vite (puerto habitual 5173) sigue utilizando las rutas heredadas de Catálogo (8080) y Usuarios (8081). El circuito REST nuevo se comprobó con Usuarios (8081), Productos (8080), Pedidos (8082), Boleta (8083) y Detalle (8084), cada uno en su propio proceso con H2. MySQL real sigue pendiente. En desarrollo, Vite proxy reenvía /api/v1/productos y /api/v1/pedidos al Catálogo; /usuarios-api hacia Usuarios. En hosting real CORS/orígenes deben configurarse.

Las APIs usan prefijo /api/v1 y Swagger en /swagger-ui.html de cada servicio; OpenAPI JSON en /v3/api-docs.

## 2. Inventario de endpoints implementados

| HTTP | Ruta (respecto al servicio) | Función | Autenticación | HTTP típico |
|---|---|---|---|---|
| GET | Catálogo /api/v1/productos | Lista inventario | Pública | 200 |
| GET | Catálogo /api/v1/productos/{id} | Detalle producto | Pública | 200/404 |
| POST | Catálogo /api/v1/productos | Crear producto | ADMINISTRADOR JWT | 201/400/401/403/409 |
| PUT | Catálogo /api/v1/productos/{id} | Actualizar producto | ADMINISTRADOR JWT | 200/400/401/403/404/409 |
| DELETE | Catálogo /api/v1/productos/{id} | Eliminar si no hay órdenes | ADMINISTRADOR JWT | 204/401/403/404/409 |
| POST | Usuarios /api/v1/usuarios | Registrar CLIENTE | Pública | 201/400/409 |
| GET | Usuarios /api/v1/usuarios/{id} | Perfil propio o administrador | JWT propietario o ADMINISTRADOR | 200/401/403/404 |
| POST | Usuarios /api/v1/auth/login | Emitir JWT y refresh token | Pública + credenciales | 200/400/401 |
| POST | Usuarios /api/v1/auth/refresh | Renovar JWT y rotar refresh | Token opaco en JSON | 200/400/401 |
| POST | Usuarios /api/v1/auth/logout | Revocar refresh | Token opaco en JSON | 204/400 |
| POST | Catálogo /api/v1/pedidos | Crear reserva e idempotencia | CLIENTE JWT | 201/200 reintento/400/401/403/404/409 |
| GET | Catálogo /api/v1/pedidos/mios | Órdenes propias | CLIENTE JWT | 200/401/403 |
| GET | Catálogo /api/v1/pedidos/pendientes | Órdenes pendientes | VENDEDOR/ADMINISTRADOR JWT | 200/401/403 |
| GET | Catálogo /api/v1/pedidos/{id} | Orden por ID | CLIENTE propietario o VENDEDOR/ADMINISTRADOR | 200/401/403/404 |

La tabla refleja el código, NO respuestas registradas de MySQL. GET telemetría/entregas, pagos y eliminación de usuarios **no son endpoints implementados**. Usuarios sí ofrece ahora POST /api/v1/auth/refresh y POST /api/v1/auth/logout; rotan/revocan credenciales opacas de un solo uso.

## 3. Contratos de entrada/salida

### Productos
POST/PUT JSON:
~~~json
{"sku":"BRU-001","nombre":"Cultivo de prueba","descripcion":"Raíz fresca","precio":450,"stock":8,"planetaOrigen":"Bruma"}
~~~
Respuesta 201/200: objeto con id (asignado por BD), sku, nombre, descripcion, precio, stock, planetaOrigen. DELETE 204 sin JSON. SKU único y validaciones en DTO.

### Login
POST /api/v1/auth/login:
~~~json
{"email":"correo_de_prueba@example.com","contrasena":"valor_de_prueba_NO_REAL"}
~~~
Respuesta: accessToken (JWT), tokenType Bearer, expiresInSeconds (1800), idUsuario, rolUsuario. **No documentar aquí credenciales funcionales ni tokens reales.**

### Pedidos
POST /api/v1/pedidos + Authorization Bearer + Idempotency-Key UUID:
~~~json
{"planetaDestino":"Bruma","productos":[{"productoId":1,"cantidad":2}]}
~~~
Respuesta: id, clienteId, planetaDestino, estado PENDIENTE, creadoEn, total, productos (productoId, nombreProducto, cantidad, precioUnitario, subtotal). La solicitud puede devolver 201 nueva / 200 repetida idéntica / 409 conflicto o falta stock.

### Usuario
Registro: nombreUsuario, rutUsuario, direccionUsuario, emailUsuario, contrasena. Respuesta pública minimizada: idUsuario, nombreUsuario, rolUsuario, fechaHoraCreacionUsuario; no se revela hash ni RUT/dirección/email en JSON de salida.

## 4. Recorridos integrados verificables

**Lectura catálogo:** React useCatalogo → GET Catálogo → ProductoController → ProductoService → ProductoRepository → JSON → ProductGrid.

**CRUD admin:** formulario React → Bearer JWT → Spring Security ADMIN → ProductoController → DTO validado → ProductoService transaccional → JPA → JSON/204/409 → recarga de listado.

**Pedido cliente:** React Carrito → JWT/Idempotency-Key → PedidoController → lock de filas Producto → reserva+Pedido+Líneas misma transacción → JSON → React MisPedidos → GET propio.

**Consulta vendedor:** React ConsultaProductos → GET lista/detalle; React MisPedidos/PedidoDetalle → GET pendientes y GET ID → token VENDEDOR → respuesta de solo lectura.

**Seguridad:** token firmado por Usuarios, verificado en Catálogo con la misma clave de laboratorio. Permisos efectivos en backend; menú de React solo mejora UX. Expiración y sesión guardada temporalmente en sessionStorage de la pestaña.

## 5. Evidencias técnicas y limitaciones

- CI Java prueba CRUD, autorizaciones, reservas, Idempotency-Key, stock y errores mediante MockMvc y H2.
- CI React ejecuta tests Node de políticas de roles, sesión, carrito, contratos mock y build de Vite.
- PR #27 verificó cinco procesos y HTTP real sobre H2 aisladas, incluyendo JWT, stock, idempotencia y consistencia de boletas. MySQL real, persistencia tras reinicio, navegador y capturas visuales de Swagger **se comprobarán al final**.
- El enunciado también menciona renovación de tokens; ahora se implementaron refresh token rotativo y renovación automática de React. Su persistencia real requiere validación MySQL al final.

## 6. Capturas y resultados finales — casillas sin falsear

| Evidencia | Resultado | Archivo/figura |
|---|---|---|
| Swagger CRUD de Catálogo ejecutado | PENDIENTE | Figura por agregar |
| Swagger JWT/Usuarios ejecutado | PENDIENTE | Figura por agregar |
| GET productos React + respuesta HTTP | PENDIENTE | Figura por agregar |
| POST/PUT/DELETE admin + respuesta HTTP | PENDIENTE | Figura por agregar |
| 401/403 en rol inadecuado | PENDIENTE | Figura por agregar |
| Login y compra con MySQL, consulta de pedido | PENDIENTE | Figura por agregar |
| Persistencia entre reinicios Spring | PENDIENTE | Figura por agregar |

Referencias complementarias: docs/CONTRATOS_API.md, docs/BD_MODELO_EP3.md, docs/INTEGRACION_LOCAL.md y código de los controladores.

### Refresh token
Login añade refreshToken aleatorio de 256 bits y refreshExpiresInSeconds (86400). Usuarios almacena su SHA-256 y rota el valor al llamar POST /api/v1/auth/refresh. El frontend conserva refresh en sessionStorage de la misma pestaña (riesgo XSS de MVP). POST /api/v1/auth/logout invalida el refresh; JWT de acceso ya emitidos siguen vigentes hasta caducar. No mostrar secretos reales en documentación/capturas.


## 7. Ensayo E2E HTTP de cinco procesos (15-11-2026 UTC)

[GitHub Actions 43668577173](https://github.com/dante1603/Huerto_Hogar_2/actions/runs/43668577173) — PASS 71 tests Java + comprobación HTTP `[PASS]` sobre cinco servicios/H2 independientes. Registro/login JWT, creación de Pedido por REST con reserva de stock en Productos, generación de Boleta y Detalle, importes y líneas verificados, duplicados evitados y consultas ajenas rechazadas. Primer intento reveló enmascaramiento 403/409 por Spring Security; PR #27 lo corrigió sin alterar roles ni rutas públicas. Prueba reproducible: `scripts/e2e_http_fullstack.py` y `docs/VERIFICACION_HTTP_EP3.md`.

Pendiente: MySQL 8.4, persistencia/reinicio, red degradada, reconciliación activada, navegador React, Swagger visual, capturas y archivos de entrega.
