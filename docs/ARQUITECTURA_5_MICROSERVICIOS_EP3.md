# Contratos de separación — cinco microservicios HuertoHogar (14-11-2026)

**Alcance confirmado con Dante:** Usuarios, Productos, Pedidos, Boleta y Detalle Boleta. El profesor indicó cinco servicios al equipo; este corte no afirma que los cinco estén operativos.

## Estado actual

| Servicio objetivo | Estado de la implementación | Propietario de datos |
|---|---|---|
| Usuarios | Spring Boot independiente | Cuentas, roles, BCrypt y refresh JWT |
| Productos | Spring Boot Catálogo, con CRUD y API nueva de reserva | SKU, precio, stock, reservas |
| Pedidos | Módulo del mismo Catálogo; extracción todavía pendiente | Pedido y líneas, todavía en esquema Catálogo |
| Boleta | ZIP BoletaV2 del compañero, sin integrar ni depurar | Boletas y referencia a pedido, futuro |
| Detalle Boleta | ZIP del compañero, sin integrar ni depurar | Líneas de comprobante, futuro |

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

La edición administrativa no puede sobrescribir stock ni eliminar producto durante una reserva activa. El flujo POST /api/v1/pedidos existente **continúa usando la transacción local**: aún no llama esta API ni se ha dividido en procesos.

## Invariante para la extracción posterior

El servicio independiente Pedidos debe invocar PUT, persistir el pedido local, y compensar con DELETE si falla su commit; ante timeout debe primero consultar GET con el mismo UUID, nunca crear uno nuevo. Esto es una saga, **no una transacción distribuida**. Se requieren pruebas de errores remotos, reintentos concurrentes, compensación y reconciliación antes de mover los pedidos.

Los servicios Boleta y Detalle Boleta deben validar existencia de pedido y copiar precios de snapshots autorizados, nunca de campos libres del cliente. Los ZIP del compañero contienen API CRUD aprovechable, pero Boleta trae credencial literal y ambos carecen de JWT: no trasladar secretos ni publicar esas APIs sin seguridad.

## Límites de verificación

Tests MockMvc/H2 y CI comprueban este contrato dentro de Productos. No se ha ejecutado MySQL real, no hay cliente remoto independiente, no se ha emitido token de servicio por un runtime de Pedidos, ni se ha probado el sistema completo en navegador. Esto corresponde al último corte acordado para MySQL, o a futuras misiones para el resto.

Auditoría y seguimiento: https://github.com/dante1603/Huerto_Hogar_2/issues/19
