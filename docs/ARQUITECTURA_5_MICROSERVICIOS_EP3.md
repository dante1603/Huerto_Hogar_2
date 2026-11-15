# Contratos de separación — cinco microservicios HuertoHogar (14-11-2026)

**Alcance confirmado con Dante:** Usuarios, Productos, Pedidos, Boleta y Detalle Boleta. El profesor indicó cinco servicios al equipo; los cinco existen en código y pasaron un contrato HTTP real/H2; aún no están validados con MySQL ni navegador.

## Estado actual

| Servicio objetivo | Estado de la implementación | Propietario de datos |
|---|---|---|
| Usuarios | Spring Boot independiente | Cuentas, roles, BCrypt y refresh JWT |
| Productos | Spring Boot Catálogo, con CRUD y API nueva de reserva | SKU, precio, stock, reservas |
| Pedidos | Tercer Spring Boot independiente creado en PR #21; aún sin tráfico React | Pedido y líneas en esquema propio proyectado (MySQL pendiente); Catálogo conserva ruta legada |
| Boleta | Cuarto Spring Boot integrado PR #23, CI H2/MockMvc PASS (6 tests); tráfico React y MySQL pendientes | Boleta única por pedido, identidad y total verificados vía REST |
| Detalle Boleta | Quinto Spring Boot integrado PR #25, CI H2/MockMvc PASS (9 tests); MySQL y React pendientes; HTTP/H2 PASS PR #27 | Líneas inmutables y validadas en esquema propio proyectado |

## Contrato ejecutable nuevo en Productos

Ruta base: /api/v1/interno/reservas/{referencia}. La referencia es UUID canónico generado por Pedidos, y solo se autoriza un Bearer JWT con rol SERVICIO_PEDIDOS. Ese JWT no se proporciona al navegador ni se obtiene mediante registro público.

| Método | Respuesta normal | Fallos que maneja |
|---|---|---|
| PUT | 201 nueva; 200 mismo UUID y mismo contenido | 400 solicitud inválida; 404 producto ausente; 409 distinto payload, falta de stock o cancelación previa; 401/403 rol |
| GET | 200 con estado RESERVADA/CANCELADA, snapshot y total | 404 referencia desconocida; 401/403 |
| DELETE | 204, idempotente; no restituye dos veces | 400 UUID inválido; 401/403 |

Solicitud PUT (JSON de ejemplo, NO resultado de MySQL):

```json
{"productos":[{"productoId":1,"cantidad":2}]}
```

Respuesta PUT (ejemplo de contrato, NO evidencia MySQL):

```json
{"referencia":"b6c96ec3-dd85-4d8b-85a1-6510b2362712","estado":"RESERVADA","total":240,"productos":[{"productoId":1,"nombreProducto":"Cultivo","cantidad":2,"precioUnitario":120,"subtotal":240}]}
```

Los importes se toman exclusivamente de Productos. La reserva toma locks ordenados PESSIMISTIC_WRITE, descuenta unidades y persiste reserva, snapshots y total en una transacción. Reintentar mismo UUID/lineas no vuelve a descontar; distinta cantidad responde 409.

DELETE sobre una reserva activa devuelve stock una sola vez y mantiene la referencia CANCELADA. Si DELETE llega antes de PUT, se crea un registro CANCELADA sin líneas (tombstone): un PUT posterior con ese UUID responde 409. Nunca reutilizar un UUID tras cancelar.

La edición administrativa no puede sobrescribir stock ni eliminar producto durante una reserva activa. El flujo POST /api/v1/pedidos **del Catálogo heredado** sigue usando su transacción local; el nuevo proyecto backend/pedidos consume esta API interna, pero aún no recibe tráfico de la tienda.

## Invariante para la extracción posterior

El servicio backend/pedidos ya registra intención local antes de PUT, persiste el pedido cuando recibe snapshot y consulta GET tras timeout con el mismo UUID. **No hay transacción distribuida**. PR #26 implementó reconciliación y compensación de intenciones **desactivadas por defecto**; falta validar fallos de red y MySQL antes de activarlas. Antes de cambiar rutas se requieren E2E, pruebas de concurrencia y MySQL.

Los servicios Boleta y Detalle Boleta deben validar existencia de pedido y copiar precios de snapshots autorizados, nunca de campos libres del cliente. Los ZIP del compañero contienen API CRUD aprovechable, pero Boleta trae credencial literal y ambos carecen de JWT: no trasladar secretos ni publicar esas APIs sin seguridad.

## Límites de verificación

Tests MockMvc/H2 y CI comprueban los contratos de Productos y el nuevo proceso Pedidos con gateway remoto simulado. PR #27 verificó clientes HTTP entre cinco procesos Spring reales y JWT de servicio sobre H2 separadas. No hay MySQL real ni recorridos completos de navegador, fallos inducidos o despliegue. Esto corresponde al último corte acordado para MySQL, o a futuras misiones para el resto.

Auditoría y seguimiento: https://github.com/dante1603/Huerto_Hogar_2/issues/19


## Segundo corte: proceso Pedidos independiente (sin cambio de tráfico)

El módulo `backend/pedidos` constituye un **tercer proceso Spring Boot** con base de datos propia proyectada `huertohogar_pedidos`. Expone el contrato público ya usado por React:
- POST `/api/v1/pedidos` CLIENTE JWT, `Idempotency-Key` UUID (201 nuevo / 200 repetido).
- GET `/api/v1/pedidos/mios` CLIENTE, GET `/pendientes` VENDEDOR/ADMIN, GET `/{id}` propietario o staff.
- GET `/v3/api-docs` y Swagger.

**Intención durable:** Pedidos registra `INICIADO` con UNIQUE(cliente_id, clave_idempotencia) y referencia UUID derivada del cliente y la clave **antes** de llamar Productos. La respuesta de Productos es el único origen válido de precios y líneas. La confirmación local `PENDIENTE` se escribe en una transacción separada y serializada. Los GET públicos no muestran pedidos sin confirmar.

**Reconciliación:** si se pierde la respuesta PUT, Pedidos consulta GET por la misma referencia; si ambos fallan, devuelve 503 y mantiene intención `INICIADO` para reintentar el mismo `Idempotency-Key`. No devuelve un pedido ficticio ni repite una reserva con otra referencia. Un snapshot que no corresponda a la solicitud o cuyo total no coincida se rechaza con 503.

**Límite importante:** no existe una transacción global entre los dos procesos. El sistema todavía no cuenta con un reconciliador periódico, compensación/abandono de intenciones, política de caducidad de reservas ni simulación de fallos en un MySQL real. Un pedido abandonado después de reservar puede conservar stock inmovilizado hasta la reconciliación manual. **No redirigir tráfico de la tienda al servicio independiente ni retirar el pedido legado de Catálogo hasta cerrar esos casos.**

La implementación usa **JWT interno efímero** con rol `SERVICIO_PEDIDOS` y firma HMAC compartida en el MVP local, **sin exponerlo a React**. Antes de producción usar firma asimétrica/mTLS y credenciales segregadas; una clave simétrica compartida permite emitir otros roles si se compromete.

La CI ahora incluye tests MockMvc/H2 del tercer servicio con gateway de Productos simulado. PR #27 valida un enlace HTTP físico sobre H2, no una BD MySQL real. En el último corte hay que configurar el tercer esquema, puertos y rutas proxy para el cambio de tráfico, más E2E completos.


## Corte Boleta (PR #23 integrada; HTTP real/H2 comprobado en PR #27)

El cuarto proceso `backend/boleta` es una adaptación segura del ZIP del compañero: solo `POST /api/v1/boletas` (JWT CLIENTE, `{pedidoId}`) y `GET /api/v1/boletas/{id}` (propietario o staff). El servicio verifica el pedido `PENDIENTE` con su dueño (`backend/pedidos`) utilizando REST y el JWT del cliente; no confía en usuario, precios ni importes suministrados por React. El comprobante ficticio es inmutable, no representa pago ni boleta SII; UNIQUE(pedido_id) impide duplicar emisiones. No se activan rutas React, no se tocan esquemas MySQL y Detalle Boleta sigue fuera de integración.


## Quinto corte: Detalle Boleta independiente

`backend/detalle-boleta` implementa `POST /api/v1/detalles-boleta` con solo `{boletaId}`, GET por línea o por boleta, JWT CLIENTE propietario/roles de lectura. Consulta Boleta (propietaria de la cabecera) y Pedidos (propietario del snapshot del pedido) por REST con JWT delegado; rechaza cifras suministradas por frontend, comprobantes ajenos y cualquier diferencia entre importes.

La escritura de todas las líneas se realiza bajo una transacción local y `UNIQUE(id_boleta,numero_linea)` evita líneas duplicadas. Una carrera se resuelve releyendo la ganadora; los documentos no se pueden editar ni eliminar. **E2E HTTP sobre H2 PASS en PR #27; no hay transacción global ni E2E MySQL/navegador**, y React no cambió. El uso de JWT delegado y la firma simétrica compartida son compromisos de laboratorio, no una postura definitiva de despliegue.


## Compensación de intenciones abandonadas: código opt-in (corte siguiente)

La recuperación automática anterior a MySQL está **desactivada por defecto**. `INICIADO` con más de 60 minutos puede entrar a `CANCELANDO` con una transición local transaccional antes de ejecutar DELETE idempotente de Productos. El estado `CANCELADO` solo se persiste **después** de HTTP 204; un timeout deja `CANCELANDO` y reintenta con la misma referencia. `confirmar()` rechaza pedidos en cancelación, evitando pedidos confirmados con stock ya repuesto. Tras cancelar, el cliente necesita otra Idempotency-Key; no se intenta re-reservar ni publicar un pedido ficticio.

Activación manual tras E2E de red/concurrencia y MySQL: `RECONCILIACION_HABILITADA=true`, `RECONCILIACION_CADUCIDAD_MINUTOS=60` (mínimo efectivo un minuto). **No afirmar seguridad distribuida total** antes de validar situaciones de caída, carrera y timeouts en procesos reales. El reconciliador no es una saga comercial completa ni prueba de pago.


## Verificación HTTP transversal — PR #27

[GitHub Actions 43668577173](https://github.com/dante1603/Huerto_Hogar_2/actions/runs/43668577173) acreditó 71/71 tests y circuito REST entre cinco procesos/H2 independientes: JWT, reservas reales, Boleta y Detalle consistentes, reintentos sin duplicación y validación multicliente. La corrección a `DispatcherType.ERROR` evita enmascarar conflictos 409 y errores 404 como 403. Ver `docs/VERIFICACION_HTTP_EP3.md`. **No activar reconciliador ni cambiar proxy React hasta completar MySQL, escenarios de fallo y QA visual.**
