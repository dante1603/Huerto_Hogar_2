# Boleta — cuarto microservicio EP3

Adaptacion endurecida del `HuertoHogar-BoletaV2.zip` del equipo. Conserva el concepto de entidad `boleta`, ID generado y fecha de emision, pero **no copia** los endpoints CRUD inseguros ni sus credenciales.

## Contrato

- `POST /api/v1/boletas` — CLIENTE JWT; cuerpo `{"pedidoId":123}`; 201 al crear, 200 al reintentar. `Location` apunta al documento.
- `GET /api/v1/boletas/{id}` — CLIENTE propietario o VENDEDOR/ADMINISTRADOR JWT; cliente ajeno recibe 404.
- No hay PUT/DELETE ni listado publico: el comprobante es inmutable y no expone compras ajenas.
- Swagger: `/swagger-ui.html`.

El cliente **no envia usuario, total, precios ni estado**. El servicio valida el JWT emitido por Usuarios y consulta por HTTP `GET /api/v1/pedidos/{id}` usando el mismo JWT del cliente; Pedidos es propietario de la reserva y sus snapshots. Un pedido `PENDIENTE` permite emitir **un comprobante academico de reserva, no tributario ni prueba de pago**. Los importes se expresan en **GalatiCoins**. `UNIQUE(pedido_id)` y una lectura posterior al conflicto dan idempotencia a reintentos concurrentes.

La lectura remota usa timeout corto; si Pedidos no responde, Boleta devuelve 503 y no inventa datos. Aun **no** existe el flujo de emision automatica ni integracion con Detalle Boleta. El frontend usa la ruta heredada de pedidos en Catalogo: no redirigirla sin cerrar la saga, validar el trafico real y MySQL.

## Desarrollo

Con Catalogo, Usuarios y el nuevo Pedidos por separado y **la misma** clave de laboratorio `JWT_SECRET_BASE64`:

```bash
PORT=8083 PEDIDOS_BASE_URL=http://localhost:8082 \
 mvn -f backend/boleta/pom.xml spring-boot:run -Dspring-boot.run.profiles=demo
```

Pruebas: `mvn -B -ntp -f backend/boleta/pom.xml test` (MockMvc/H2 + sustituto de Pedidos). Para produccion harian falta claves segregadas y autenticacion entre servicios mas robusta; la delegacion del JWT de cliente es un compromiso de laboratorio y no se considera probada en red real.

## Criterio del corte

JWT obligatorio y validado, propiedad por cliente, no aceptar datos monetarios externos, un comprobante por pedido, GET protegido, pruebas CI. **MySQL real, navegador y comunicacion entre procesos quedan pendientes del corte final**, segun decision del proyecto. Detalle Boleta sera el quinto proceso y obtendra lineas verificadas desde Pedidos, no campos libres.
