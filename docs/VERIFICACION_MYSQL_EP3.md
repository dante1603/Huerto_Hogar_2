# Verificación de Gate 3: Persistencia Transaccional Real Full Stack con MySQL 8.4

**Fecha de ejecución:** 15 de noviembre de 2026  
**Rama:** `fix/mysql-cinco-esquemas-2026-11-15` (PR #30 contra `integration/fullstack-2026-11-14`)  
**Commit base:** `3d55597c4f820394c7458184d1288ac8d7b70642`  
**Entorno:** Windows 10/11, PowerShell 5.1, Java 21 LTS (`21.0.12`), MySQL Community Server 8.4.13 en contenedor Docker.  
**Resultado global:** **PASS** (Verificación completa sin uso de H2, sin alterar Galaxym, sin conmutar React y sin activar reconciliación automática).

### Resoluciones de Auditoría PR #30 (Observaciones de Anam):
1. **Gestión de Procesos:** Reemplazo de cierre ciego de procesos por `ProcessManager` que audita puertos 8080–8084 previo al inicio (aborta si hay procesos externos), rastrea exclusivamente los PIDs de JVM iniciados por el runner (`managed_pids`) y los termina de forma segura sin tocar procesos de terceros.
2. **Aislamiento de Datos y Fixtures Dinámicos:** Eliminación del consumo de productos compartidos (`BRU-TUB-001`, `ARI-FRU-002`). Generación de SKUs dinámicos (`GATE3-A-{run_id}`, `GATE3-B-{run_id}`) y contraseñas dinámicas por corrida (`secrets.token_urlsafe(16)`), verificando que el stock de catálogo persistente no sea modificado.
3. **Consultas SQL Relacionales Exactas:** Eliminación de `ORDER BY creada_en DESC LIMIT 1` y búsquedas de subcadenas aproximadas; filtrado estricto por IDs exactos y llaves foráneas (`referencia_reserva`, `pedido_id`, `id_boleta`, `cliente_id`), validando cardinalidades e importes exactos.
4. **Seguridad y Documentación:** Ejecución de queries SQL conectando como `huerto_app` (no `root`); omisión de `token_hash` en logs; corrección de enlaces Markdown en documentación a rutas relativas del repositorio; aclaración de aprovisionamiento exclusivo de `ADMINISTRADOR` (sin rol `VENDEDOR`).

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

## 2. Aprovisionamiento Controlado y Aislamiento de Datos de Prueba

1. **Aprovisionamiento de ADMINISTRADOR en BD local:**
   - Para cumplir estrictamente la regla de que el registro público solo emite rol `CLIENTE` y que ningún rol privilegiado puede registrarse por HTTP público, se aprovisionó una cuenta de prueba `admin.gate3@huertohogar.cl` directamente en la base de datos local `huertohogar_usuarios.usuario` con rol `ADMINISTRADOR` y contraseña hasheada con `BCryptPasswordEncoder`. (Nota: solo se aprovisionó el rol `ADMINISTRADOR` necesario para el alta de productos de prueba; el rol `VENDEDOR` no fue requerido en Gate 3).
   - Credencial administrativa autenticada mediante `POST /api/v1/auth/login` obteniendo un Bearer JWT con claim `"rol": "ADMINISTRADOR"`.

2. **Aislamiento de Fixtures de Prueba con SKU y Claves Dinámicas:**
   - Conforme a la auditoría de PR #30, el runner no consume stock de productos compartidos o persistentes (`BRU-TUB-001`, `ARI-FRU-002`).
   - Por cada ejecución se generan fixtures aislados con identificador dinámico de corrida (`run_id` aleatorio):
     - Producto A: SKU `GATE3-A-{run_id}`, `Producto Test A ({run_id})`, precio $120, stock 10, origen `Bruma`.
     - Producto B: SKU `GATE3-B-{run_id}`, `Producto Test B ({run_id})`, precio $250, stock 10, origen `Árido`.
   - Contraseñas dinámicas y seguras generadas por ejecución vía `secrets.token_urlsafe(16)`.
   - Se valida explícitamente que los productos de catálogo compartidos conserven su stock intacto antes y después de la prueba.

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

Las consultas SQL directas son ejecutadas conectándose exclusivamente con el usuario de aplicación `huerto_app` (no `root`) mediante `docker compose exec -T -e MYSQL_PWD=... mysql mysql -uhuerto_app -s -N`.

En cumplimiento de las observaciones de auditoría, las consultas filtran estrictamente por identificadores exactos y llaves foráneas (`referencia_reserva`, `pedido_id`, `id_boleta`, `cliente_id`), sin recurrir a `ORDER BY creada_en DESC LIMIT 1` ni aserciones de subcadenas aproximadas:

```sql
-- Usuarios y sesiones (verificación por id exacto)
SELECT id_usuario, email_usuario, rol_usuario FROM huertohogar_usuarios.usuario WHERE id_usuario = :cliente_id;
-- Resultado: registro exacto persistido con rol CLIENTE.

SELECT usuario_id FROM huertohogar_usuarios.refresh_sesion WHERE usuario_id = :cliente_id;
-- Resultado: sesion activa asociada al usuario (sin imprimir token_hash en logs por seguridad).

-- Catálogo e inventario (filtrado por SKU dinámico exacto)
SELECT id, sku, stock FROM huertohogar_catalogo.producto WHERE sku = :sku_a;
SELECT id, sku, stock FROM huertohogar_catalogo.producto WHERE sku = :sku_b;
-- Resultado: inventario decrementado exactamente en las cantidades solicitadas (10 - 2 = 8, 10 - 1 = 9).

SELECT referencia, estado, total FROM huertohogar_catalogo.reserva_stock WHERE referencia = :ref_reserva;
-- Resultado: 1 registro exacto con estado RESERVADA y total 490.

-- Pedidos (búsqueda por ID exacto y referencia_reserva foránea)
SELECT id, cliente_id, estado, total, referencia_reserva FROM huertohogar_pedidos.pedido WHERE id = :pedido_id;
-- Resultado: id y cliente_id exactos, estado PENDIENTE, total 490, vinculación foránea a referencia_reserva.

SELECT id, producto_id, cantidad, precio_unitario, subtotal FROM huertohogar_pedidos.linea_pedido WHERE pedido_id = :pedido_id;
-- Resultado: exactamente 2 registros vinculados al pedido_id, validando cardinalidad e importes.

-- Boleta (búsqueda por ID exacto y relación de pedido)
SELECT id_boleta, id_usuario_compra, pedido_id, total_venta FROM huertohogar_boleta.boleta WHERE id_boleta = :boleta_id;
-- Resultado: id_boleta exacto, id_usuario_compra coherente, pedido_id verificado y total_venta 490.

-- Detalle de Boleta (verificación relacional por id_boleta exacto)
SELECT id_detalle, id_boleta, id_producto, numero_linea, cantidad_producto, precio_unitario, total_venta 
FROM huertohogar_detalle_boleta.detalle_boleta WHERE id_boleta = :boleta_id;
-- Resultado: exactamente 2 registros hijos vinculados a la boleta, con subtotales 240 y 250 sumando 490.
```

---

## 6. Prueba de Persistencia tras Reinicio en Frío y Gestión Segura de Procesos

1. **Gestión de Procesos Exclusivos (ProcessManager):**
   - El script no realiza matanza ciega de procesos (`taskkill /F /IM java.exe`) ni cierra aplicaciones preexistentes ajenas.
   - Antes de iniciar, verifica que los puertos 8080 a 8084 estén libres; si alguno está ocupado por procesos externos, aborta de inmediato solicitando intervención manual.
   - Registra y gestiona únicamente los PIDs propios (`managed_pids`) iniciados durante la prueba.
2. **Detención Controlada:**
   - Termina ordenadamente solo los procesos Java registrados bajo control del script.
   - El contenedor MySQL y el volumen Docker `huerto_mysql_data` permanecen intactos.
3. **Reinicio en Frío:**
   - Se levantan nuevamente las cinco JVMs apuntando a los esquemas MySQL respectivos.
   - Se espera con polling activo y backoff hasta que los cinco puertos respondan (hasta 90s).
4. **Comprobación de Persistencia Post-Reinicio:**
   - `GET http://127.0.0.1:8080/api/v1/productos`: El stock de los fixtures dinámicos se mantiene idéntico (8 y 9).
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
