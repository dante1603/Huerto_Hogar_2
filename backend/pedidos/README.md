# Pedidos — microservicio independiente (corte EP3)

**Estado:** implementado como proceso Spring Boot propio, **todavía no recibe tráfico de React**. El Catálogo conserva temporalmente su implementación heredada de pedidos; no conectar ambos en producción simultáneamente.

## Responsabilidades

- Es dueño de `pedido`, `linea_pedido` e intenciones `INICIADO` en su propio esquema relacional.
- Valida JWT y rol; clientes solo consultan pedidos propios, staff ve pendientes.
- Registra el intento antes de llamar a Productos, con `Idempotency-Key` UUID y referencia de reserva determinista.
- Invoca la API interna de Productos para reservar inventario y tomar snapshots seguros de precios; ante timeout consulta la referencia remota y permite reconciliar sin duplicar stock.
- No se conecta jamás a la base de datos de Productos.

## Arranque **solo H2 demo** para desarrollo

En una terminal, iniciar Productos (Catálogo) en `localhost:8080` con perfil `demo`. En otra terminal compartir el **mismo** `JWT_SECRET_BASE64` y `JWT_ISSUER`:

```bash
PORT=8082 PRODUCTOS_BASE_URL=http://localhost:8080 \
  mvn -f backend/pedidos/pom.xml spring-boot:run -Dspring-boot.run.profiles=demo
```

El JWT de `SERVICIO_PEDIDOS` se firma solo en backend, nunca en el navegador. Se necesita también un JWT CLIENTE válido emitido por Usuarios para crear pedidos.

Pruebas automatizadas: `mvn -B -ntp -f backend/pedidos/pom.xml test`. Los tests aíslan la llamada remota con un doble de prueba, verifican idempotencia, control de acceso y reconciliación.

## No conmutar React todavía

Hay una diferencia fundamental respecto de Catálogo: ahora la transacción de reserva de stock vive en Productos y la transacción de confirmación del pedido vive en Pedidos. Eso crea un protocolo de saga que necesita casos de recuperación, expiración de intenciones y pruebas E2E antes del cambio de rutas. El estado `INICIADO` nunca es una compra finalizada.

**Pendientes:** reconciliador programado, compensación controlada, integraciones reales con Boleta/Detalle Boleta, MySQL para el tercer esquema, cambio de proxy, pruebas de red y navegador. Sin datos de MySQL ni credenciales en git.
