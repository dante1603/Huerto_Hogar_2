# Verificación de Gate 3: Persistencia Transaccional Real Full Stack con MySQL 8.4

**Fecha de ejecución:** 15 de noviembre de 2026  
**Rama:** `fix/mysql-cinco-esquemas-2026-11-15` (PR #30 contra `integration/fullstack-2026-11-14`)  
**Commit base:** `3d55597c4f820394c7458184d1288ac8d7b70642`  
**Entorno:** Windows 10/11, PowerShell 5.1, Java 21 LTS (`21.0.12`), MySQL Community Server 8.4.13 en contenedor Docker.  
**Resultado global:** **PASS** (Verificación completa sin uso de H2, sin alterar Galaxym, sin conmutar React y sin activar reconciliación automática).

---

## 1. Alcance y Estado de Infraestructura

Se verificó el funcionamiento integral sobre una instancia real de **MySQL 8.4** (`mysql:8.4`) con cinco esquemas independientes y cinco microservicios Spring Boot independientes comunicándose exclusivamente a través de HTTP REST y JWT firmado.

### Contenedor MySQL
```text
NAME                   IMAGE       COMMAND                  SERVICE   STATUS                    PORTS
huertohogar2-mysql-1   mysql:8.4   "docker-entrypoint.s…"   mysql     Up (healthy)              127.0.0.1:3306->3306/tcp
```

### Esquemas independientes en MySQL 8.4
El usuario de aplicación `huerto_app` tiene permisos concedidos sobre los cinco esquemas requeridos (sin uso de `root` en Spring Boot):

```text
Database (huertohogar_%)
huertohogar_boleta
huertohogar_catalogo
huertohogar_detalle_boleta
huertohogar_pedidos
huertohogar_usuarios
```

### Microservicios en ejecución
| Microservicio | Puerto | Esquema MySQL | Endpoint de verificación |
|---|---|---|---|
| Catálogo / Productos | 8080 | `huertohogar_catalogo` | `GET /api/v1/productos` |
| Usuarios | 8081 | `huertohogar_usuarios` | `POST /api/v1/auth/login` |
| Pedidos | 8082 | `huertohogar_pedidos` | `GET /api/v1/pedidos/mios` |
| Boleta | 8083 | `huertohogar_boleta` | `POST /api/v1/boletas` |
| Detalle Boleta | 8084 | `huertohogar_detalle_boleta` | `POST /api/v1/detalles-boleta` |

---

## 2. Aprovisionamiento Controlado de Datos de Prueba

1. **Aprovisionamiento de ADMINISTRADOR en BD local:**
   - Para cumplir estrictamente la regla de que el registro público solo emite rol `CLIENTE` y que ningún rol privilegiado puede registrarse por HTTP público, se aprovisionó una cuenta de prueba `admin.gate3@huertohogar.cl` directamente en la base de datos local `huertohogar_usuarios.usuario` con rol `ADMINISTRADOR` y contraseña hasheada con `BCryptPasswordEncoder`.
   - Credencial administrativa autenticada mediante `POST /api/v1/auth/login` obteniendo un Bearer JWT con claim `"rol": "ADMINISTRADOR"`.

2. **Creación de Productos de Prueba:**
   - Utilizando el JWT de `ADMINISTRADOR`, se consumió `POST http://127.0.0.1:8080/api/v1/productos`:
     - Producto 1: SKU `BRU-TUB-001`, `Tuberculo de Bruma`, precio $120, origen `Bruma`.
     - Producto 2: SKU `ARI-FRU-002`, `Fruta Dorada de Aridiana`, precio $250, origen `Aridiana`.
   - Se verificó su persistencia real en la tabla `huertohogar_catalogo.producto`.

---

## 3. Recorrido Transaccional Completo E2E

El script automatizado `scripts/verify_mysql_gate3.py` ejecutó el flujo transaccional de extremo a extremo sin intervención de mocks:

1. **Registro de Cliente:**
   - `POST http://127.0.0.1:8081/api/v1/usuarios` con datos sintéticos y RUT chileno válido.
   - Respuesta: HTTP 201 Created con `rolUsuario: CLIENTE`.
2. **Login y Emisión de Credenciales:**
   - `POST http://127.0.0.1:8081/api/v1/auth/login`.
   - Respuesta: HTTP 200 OK con `accessToken` JWT (HS256) y `refreshToken` opaco de 256 bits.
   - Se almacenó `refreshToken` pre-reinicio para la prueba posterior de persistencia de sesión.
3. **Consulta de Catálogo:**
   - `GET http://127.0.0.1:8080/api/v1/productos` (público).
   - Lectura de inventario inicial.
4. **Creación de Pedido y Reserva de Stock:**
   - `POST http://127.0.0.1:8082/api/v1/pedidos` con cabecera `Idempotency-Key: <UUID>` y `Authorization: Bearer <JWT_CLIENTE>`.
   - Payload: 2 unidades de Producto 1 ($120 * 2 = $240) + 1 unidad de Producto 2 ($250 * 1 = $250) hacia destino `Bruma`.
   - El servicio Pedidos contactó internamente al Catálogo (`PUT /api/v1/interno/reservas/{referencia}`) con JWT `SERVICIO_PEDIDOS`.
   - Se ejecutó el bloqueo pesimista ordenado, descuente de stock atómico y creación de reserva en estado `RESERVADA`.
   - Respuesta: HTTP 201 Created con total $490 y estado `PENDIENTE`.
5. **Verificación de Descuento de Inventario:**
   - `GET http://127.0.0.1:8080/api/v1/productos`:
     - Producto 1: stock decrementado en 2 unidades.
     - Producto 2: stock decrementado en 1 unidad.
6. **Emisión de Boleta Académica:**
   - `POST http://127.0.0.1:8083/api/v1/boletas` con `{"pedidoId": <id>}` y JWT del cliente.
   - Boleta consultó el snapshot verificado de Pedidos (`GET /api/v1/pedidos/{id}`).
   - Respuesta: HTTP 201 Created con `total: 490`, `tipoDocumento: "COMPROBANTE_ACADEMICO_NO_TRIBUTARIO"`.
7. **Generación de Detalle de Boleta:**
   - `POST http://127.0.0.1:8084/api/v1/detalles-boleta` con `{"boletaId": <id>}` y JWT del cliente.
   - El servicio validó la boleta y el pedido, generando 2 líneas de detalle:
     - Línea 1: Producto 1, 2 unidades, precio $120, total venta $240.
     - Línea 2: Producto 2, 1 unidad, precio $250, total venta $250.
   - Suma exacta: $490 (coherente con boleta y pedido).

---

## 4. Pruebas de Idempotencia y Aislamiento

1. **Idempotencia de Pedido (reintento idéntico):**
   - Repetición de `POST /api/v1/pedidos` con la misma `Idempotency-Key` y mismo payload.
   - Resultado: HTTP 200 OK con el mismo `id` de pedido original.
   - **Verificación de inventario:** El stock no se descontó por segunda vez.
2. **Conflicto de Idempotencia (reintento con payload alterado):**
   - Envío de la misma `Idempotency-Key` con diferente payload (`planetaDestino: Yermo`, cantidad 1).
   - Resultado: **HTTP 409 Conflict** (rechazado por discordancia de huella canónica).
3. **Idempotencia de Boleta:**
   - Repetición de `POST /api/v1/boletas` con el mismo `pedidoId`.
   - Resultado: HTTP 200 OK con el mismo `id_boleta`.
4. **Idempotencia de Detalle de Boleta:**
   - Repetición de `POST /api/v1/detalles-boleta` con el mismo `boletaId`.
   - Resultado: HTTP 200 OK con las mismas 2 líneas sin duplicar registros.
5. **Aislamiento Multi-inquilino (seguridad por identidad):**
   - Se registró un segundo cliente sintético.
   - Intento de consulta de pedido ajeno: **HTTP 403 Forbidden**.
   - Intento de consulta de boleta ajena: **HTTP 404 Not Found**.
   - Intento de consulta de detalle ajeno: **HTTP 404 Not Found**.

---

## 5. Verificación Directa en los Cinco Esquemas MySQL

Se ejecutaron consultas SQL directas en el motor MySQL verificando la integridad de registros:

```sql
-- Usuarios y sesiones
SELECT id_usuario, email_usuario, rol_usuario FROM huertohogar_usuarios.usuario WHERE id_usuario = :id;
-- Resultado: registro persistido con rol CLIENTE.

SELECT usuario_id, token_hash FROM huertohogar_usuarios.refresh_sesion WHERE usuario_id = :id;
-- Resultado: hash SHA-256 del refresh token persistido.

-- Catálogo e inventario
SELECT id, sku, stock FROM huertohogar_catalogo.producto WHERE id IN (1, 2);
-- Resultado: inventario descontado exactamente en las cantidades adquiridas.

SELECT referencia, estado, total FROM huertohogar_catalogo.reserva_stock WHERE referencia = :ref;
-- Resultado: estado RESERVADA, total 490.

-- Pedidos
SELECT id, cliente_id, estado, total, referencia_reserva FROM huertohogar_pedidos.pedido WHERE id = :id;
-- Resultado: estado PENDIENTE, total 490, vinculación correcta a referencia_reserva.

SELECT id, producto_id, cantidad, precio_unitario, subtotal FROM huertohogar_pedidos.linea_pedido WHERE pedido_id = :id;
-- Resultado: 2 registros correspondientes a las líneas solicitadas.

-- Boleta
SELECT id_boleta, id_usuario_compra, pedido_id, total_venta FROM huertohogar_boleta.boleta WHERE id_boleta = :id;
-- Resultado: total_venta 490, pedido_id correspondiente.

-- Detalle de Boleta
SELECT id_detalle, id_boleta, id_producto, numero_linea, cantidad_producto, precio_unitario, total_venta 
FROM huertohogar_detalle_boleta.detalle_boleta WHERE id_boleta = :id;
-- Resultado: 2 registros con subtotal 240 y 250, totalizando 490.
```

---

## 6. Prueba de Persistencia tras Reinicio en Frío

1. **Detención:**
   - Se terminaron los procesos Java de los cinco microservicios (`taskkill /F /T`).
   - Se comprobó la liberación completa de los puertos 8080 a 8084.
   - El volumen Docker `huerto_mysql_data` y el contenedor MySQL permanecieron intactos.
2. **Reinicio en Frío:**
   - Se levantaron nuevamente las cinco JVMs apuntando a los esquemas MySQL respectivos.
   - Se esperó a que los cinco servicios alcanzaran estado operativo (`healthy` / listening).
3. **Comprobación de Persistencia Post-Reinicio:**
   - `GET http://127.0.0.1:8080/api/v1/productos`: El stock descontado se mantiene idéntico.
   - `GET http://127.0.0.1:8082/api/v1/pedidos/{id}`: El pedido se mantiene en estado `PENDIENTE` con total $490.
   - `GET http://127.0.0.1:8083/api/v1/boletas/{id}`: La boleta se mantiene con total $490.
   - `GET http://127.0.0.1:8084/api/v1/detalles-boleta/boleta/{id}`: Las 2 líneas de detalle persisten íntegras.

---

## 7. Renovación de Sesión con Refresh Token Pre-Reinicio

1. **Renovación Exitosa:**
   - Tras el reinicio de las JVMs, se envió `POST http://127.0.0.1:8081/api/v1/auth/refresh` enviando el `refreshToken` obtenido antes de reiniciar los servicios.
   - Resultado: **HTTP 200 OK**.
   - Se emitió un nuevo JWT y un nuevo `refreshToken` rotado.
2. **Uso de Nuevo JWT:**
   - Se consumió `GET http://127.0.0.1:8082/api/v1/pedidos/mios` con el nuevo JWT.
   - Resultado: HTTP 200 OK con la lista de pedidos del cliente, incluyendo el pedido creado previo al reinicio.
3. **Garantía de Un Solo Uso (Anti-Replay):**
   - Se intentó reutilizar el `refreshToken` anterior.
   - Resultado: **HTTP 401 Unauthorized**.
4. **Verificación en BD:**
   - La tabla `huertohogar_usuarios.refresh_sesion` eliminó el hash anterior e insertó el nuevo hash SHA-256.

---

## 8. Revisión de `.gitignore` y Archivos Temporales

- `.gitignore` actualizado y validado para excluir:
  - Archivos de secretos: `.env`, `**/.env`, `*.secret`, `**/*.secret` (incluyendo `.jwt_local.secret`).
  - Archivos de compilación y temporales: `**/target/`, `**/node_modules/`, `**/dist/`, `*.log`, `**/*.log`.
  - Cache de Python: `**/__pycache__/`, `*.py[cod]`.
- Se verificó mediante `git status` que ningún archivo `.secret`, `.env`, log o bytecode está preparado para seguimiento en Git.

---

## 9. Reproducibilidad

El procedimiento completo está automatizado en el script:
```powershell
python scripts/verify_mysql_gate3.py
```

Salida final del runner:
```text
======================================================================
  RESULTADO: GATE 3 PASS
  Cinco microservicios validados sobre MySQL 8.4 y HTTP real:
  - Registro, login y refresh token rotativo
  - Creacion de pedido y reserva transaccional con descuento de stock
  - Idempotencia en pedidos, boletas y detalles
  - Emision de comprobante academico inmutable y lineas de detalle
  - Aislamiento multi-inquilino estricto
  - Persistencia comprobada tras reinicio en frio de las cinco JVM
  - Renovacion de sesion exitosa post-reinicio con token previo
======================================================================
```

---

## 10. Conclusión y Estado de Gates

- **Gate 1:** Creación y acceso a los cinco esquemas MySQL -> **APROBADO (PASS)**.
- **Gate 2:** Conexión JPA/Hibernate de los cinco servicios sobre MySQL real -> **APROBADO (PASS)**.
- **Gate 3:** Transaccionalidad real, reserva de inventario, emisión de comprobantes, persistencia post-reinicio y rotación de refresh tokens sobre MySQL 8.4 -> **APROBADO (PASS)**.
- **Límites vigentes:**
  - El frontend React aún consume el controlador de Pedidos heredado del Catálogo; la conmutación al servicio independiente de Pedidos y E2E de navegador se abordará en la siguiente fase de frontend.
  - El reconciliador automático de pedidos (`RECONCILIACION_HABILITADA`) permanece inactivo (`false`) por diseño hasta pruebas específicas de desconexión.
