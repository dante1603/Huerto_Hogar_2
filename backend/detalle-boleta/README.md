# Detalle Boleta — quinto microservicio EP3 (corte de código)

Adaptación segura del ZIP `huertohogar-detalle-boleta.zip` entregado por el compañero. Conserva el modelo de línea de comprobante, pero **no reutiliza CRUD público que permita alterar precios, cantidades ni relaciones**.

## Contrato

- `POST /api/v1/detalles-boleta` — JWT CLIENTE, cuerpo **solo** `{"boletaId":9}`. Devuelve 201 con lista de líneas si se crean o 200 en un reintento. `Location: /api/v1/detalles-boleta/boleta/9`.
- `GET /api/v1/detalles-boleta/boleta/{boletaId}` — CLIENTE propietario o VENDEDOR/ADMINISTRADOR. 404 si no existe un detalle.
- `GET /api/v1/detalles-boleta/{id}` — CLIENTE propietario o staff.
- No permite PUT/DELETE ni listados globales: los comprobantes son inmutables y no son DTE/SII ni evidencia de un pago.

Al crear, primero verifica por REST `GET /api/v1/boletas/{id}`, luego `GET /api/v1/pedidos/{id}`, **delegando el JWT ya autenticado del cliente**, sin enviarlo jamás a React desde el servidor. Los precios, nombres y cantidades proceden del snapshot de Pedidos, no del cuerpo POST. Rechaza documentos incorrectos y exige que suma de líneas = total del pedido = total de Boleta, en GalatiCoins enteros.

Cada boleta tiene posiciones 1..N y restricción `UNIQUE(id_boleta, numero_linea)`. `guardarTodas` es una transacción: un fallo no deja líneas parciales. Los reintentos concurrentes releen las líneas ganadoras tras rollback y las validan de nuevo. Boleta y Pedidos viven en **bases propias**; no existen claves foráneas SQL entre microservicios.

## Ejecución demo (sin MySQL)

Se reservan puertos: Productos 8080, Usuarios configurado aparte, Pedidos 8082, Boleta 8083 y **Detalle Boleta 8084**. Compartir solo en laboratorio la misma `JWT_SECRET_BASE64` y `JWT_ISSUER`; JWT no se incluye en variables del navegador.

```bash
PORT=8084 BOLETAS_BASE_URL=http://localhost:8083 PEDIDOS_BASE_URL=http://localhost:8082 \
  mvn -f backend/detalle-boleta/pom.xml spring-boot:run -Dspring-boot.run.profiles=demo
mvn -B -ntp -f backend/detalle-boleta/pom.xml test
```

Para MySQL final, configurar `DB_URL`, `DB_USER`, `DB_PASSWORD` en entorno privado con esquema `huertohogar_detalle_boleta`.

## Límites deliberados

No cambia proxy de React, tráfico de compra ni `main`. Los tests usan MockMvc/H2 con gateways simulados: **todavía no prueban comunicación real entre procesos, MySQL, concurrencia real ni navegador**. El diseño es un comprobante académico ficticio; la sesión entre servicios reutiliza el JWT del usuario únicamente como compromiso del MVP local y necesita revisión de seguridad antes de producción.
