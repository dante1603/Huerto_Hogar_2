# Verificación E2E: React Conectado a Microservicios Independientes con MySQL 8.4 Real

**Fecha de ejecución:** 15 de noviembre de 2026  
**Rama:** `feat/react-pedidos-independiente-2026-11-15` (contra `integration/fullstack-2026-11-14`)  
**Entorno:** Windows 10/11, PowerShell 5.1, Java 21 LTS (`21.0.12`), Node 20/22, MySQL Community Server 8.4.13 en contenedor Docker.  
**Resultado global:** **PASS** (Verificación completa sin uso de H2, sin alterar Galaxym, con MySQL real y navegador real).

---

## 1. Alcance de la Conmutación de Tráfico

En esta etapa se completó la migración del frontend React (`frontend/tienda`) desde el módulo legado de Pedidos en Catálogo hacia la suite de microservicios autónomos sobre MySQL 8.4:

| Recurso / Operación | Servicio Destino | Puerto | Esquema MySQL | Ruta Proxy Vite |
|---|---|---|---|---|
| Catálogo y Stock | Catálogo (`catalogo-service`) | 8080 | `huertohogar_catalogo` | `/api/v1/productos` |
| Registro y Auth | Usuarios (`usuarios-service`) | 8081 | `huertohogar_usuarios` | `/usuarios-api/api/v1/...` |
| Pedidos y Reservas | Pedidos (`pedidos-service`) | 8082 | `huertohogar_pedidos` | `/api/v1/pedidos` |
| Comprobante Académico | Boleta (`boleta-service`) | 8083 | `huertohogar_boleta` | `/api/v1/boletas` |
| Líneas de Comprobante | Detalle Boleta (`detalle-boleta-service`) | 8084 | `huertohogar_detalle_boleta` | `/api/v1/detalles-boleta` |

---

## 2. Cambios Implementados en el Frontend

1. **Separación de Hosts en `frontend/tienda/src/lib/api.js`**:
   - Variables de entorno desacopladas: `VITE_CATALOGO_API_URL`, `VITE_USUARIOS_API_URL`, `VITE_PEDIDOS_API_URL`, `VITE_BOLETA_API_URL`, `VITE_DETALLE_BOLETA_API_URL`.
   - Clientes HTTP `crearPedido`, `consultarPedidos` y `consultarPedido` apuntan a `ordersHost + '/api/v1/pedidos'`.
   - Incorporación de funciones para comprobante académico:
     - `emitirBoleta(pedidoId, token)`: `POST /api/v1/boletas`
     - `consultarBoleta(id, token)`: `GET /api/v1/boletas/{id}`
     - `generarDetalleBoleta(boletaId, token)`: `POST /api/v1/detalles-boleta`
     - `consultarDetalleBoleta(boletaId, token)`: `GET /api/v1/detalles-boleta/boleta/{boletaId}`

2. **Configuración de Proxy en `frontend/tienda/vite.config.js` y `.env.example`**:
   - Mapeo de rutas hacia puertos 8080–8084.
   - Sanitización de cabecera `Origin` en las peticiones proxeadas para prevenir falsos bloqueos de CORS entre el servidor local Vite y los filtros de Spring Security.
   - Documentación actualizada en `.env.example`.

3. **Interfaz en `frontend/tienda/src/pages/PedidoDetalle.jsx`**:
   - Botón explícito para el rol `CLIENTE` propietario: *"Obtener comprobante académico"*.
   - Flujo idempotente en dos fases:
     1. `POST /api/v1/boletas` enviando `pedidoId`.
     2. `POST /api/v1/detalles-boleta` enviando `boletaId`.
   - Botón *"Reverificar comprobante"* para comprobar que reconsultar no altera ni duplica registros.
   - Presentación clara y destacada de que se trata de un `COMPROBANTE_ACADEMICO_NO_TRIBUTARIO` sin valor fiscal, sin afirmar pago ni entrega.

---

## 3. Pruebas Automatizadas y de Navegador

### 3.1 Suite Unitaria Frontend (`npm test`)
- 30 pruebas ejecutadas y aprobadas al 100% (incluyendo pruebas para recuperación de comprobante incompleto, endpoints independientes de Pedidos, Boleta y Detalle Boleta).
- `npm run build` genera el bundle de producción sin advertencias ni errores.

### 3.2 Prueba E2E Automatizada con MySQL 8.4 (`scripts/verify_react_mysql_ep3.py`)
Ejecutada contra el servidor Vite (`http://127.0.0.1:5173`) y validada en MySQL 8.4:
- Registro de cliente y obtención de JWT + Refresh token: **PASS**.
- Creación de pedido idempotente con `Idempotency-Key` UUID: **PASS**.
- Reintento idéntico devuelve HTTP 200 sin nuevo descuento: **PASS**.
- Conflicto por clave idéntica con payload alterado devuelve HTTP 409: **PASS**.
- Intento de pedido con stock insuficiente devuelve HTTP 409: **PASS**.
- Consulta de producto en Catálogo verifica descuento exacto de stock: **PASS**.
- Aislamiento multi-inquilino (Cliente 2 bloqueado con HTTP 403/404): **PASS**.
- Emisión de Boleta académica (:8083) y reintento idempotente (200 OK): **PASS**.
- Generación de Líneas de Detalle (:8084) y reintento idempotente (200 OK): **PASS**.
- Verificación SQL relacional en los 5 esquemas de MySQL 8.4 con cardinalidades e importes exactos: **PASS**.

### 3.3 Prueba Visual Interactiva en Navegador Real (Subagente Browser)
El subagente de navegador interactuó directamente con la aplicación web en `http://127.0.0.1:5173`:
1. Registro de cliente `Cliente Browser` con RUT `18273645-7` -> `201 Created`.
2. Inicio de sesión exitoso -> Panel de cuenta de cliente.
3. Navegación al catálogo y adición de 2 productos al carrito.
4. Selección de destino `Bruma` y confirmación de la orden -> **Pedido #14** creado en estado `PENDIENTE`.
5. Navegación a `/pedidos` y apertura del detalle de la orden.
6. Clic en *"Obtener comprobante académico"* -> Emisión exitosa de **Boleta #13** y 2 líneas de detalle verificadas.
7. Clic en *"Reverificar comprobante"* -> Idempotencia confirmada sin duplicación.

---

## 4. Evidencia Gráfica

Las capturas tomadas durante la ejecución en navegador real se encuentran archivadas en `docs/evidencia_react_mysql_ep3/`:

1. `01_carrito_pedido_confirmado.png`: Mensaje de confirmación del Pedido #14 con estado PENDIENTE y total 370 GalatiCoins.
2. `02_mis_pedidos_listado.png`: Vista del historial de pedidos del cliente con la orden #14.
3. `03_pedido_detalle_inicial.png`: Detalle del pedido previo a la emisión del comprobante académico.
4. `04_comprobante_academico_emitido.png`: Emisión exitosa de Boleta #13 (tipo `COMPROBANTE_ACADEMICO_NO_TRIBUTARIO`) y Líneas de Detalle Verificadas.
5. `05_reverificacion_idempotente.png`: Reverificación del comprobante demostrando idempotencia total y consistencia de montos.

---

## 5. Verificación de Límites
- Galaxym no fue modificado.
- Reconciliación automática permanece deshabilitada (`RECONCILIACION_HABILITADA=false`).
- Base de datos MySQL 8.4 conservó intactos los datos y esquemas preexistentes.
- `main` no fue alterado; el corte se entrega en rama `feat/react-pedidos-independiente-2026-11-15` lista para PR hacia `integration/fullstack-2026-11-14`.

---

## 6. Correcciones de Auditoría (Revisión de Anam en PR #31)

En atención a las observaciones técnicas realizadas por Anam en la PR #31, se implementaron las siguientes mejoras y pruebas de regresión:

1. **Aislamiento de Productos de Prueba E2E (`scripts/verify_react_mysql_ep3.py`)**:
   - Se implementó aprovisionamiento efímero de un usuario administrador y elevación controlada en MySQL (`huertohogar_usuarios.usuario`).
   - Se crea dinámicamente un producto aislado con SKU `REACT-E2E-{run_id}` para realizar la orden de prueba.
   - Se realiza un snapshot previo del inventario compartido y se verifica que todos los SKUs preexistentes conserven su stock intacto tras la compra.
   - Al finalizar, se revocan las credenciales del administrador efímero.

2. **Protección contra Reutilización de PIDs (`scripts/manage_local_services.py`)**:
   - Registro de metadato `jar_name` en `.active_microservices_pids.json`.
   - Implementación de `get_process_command_line(pid)` e inspección de identidad en `verify_process_identity(pid, expected_jar)` antes de emitir cualquier terminación de proceso (`taskkill` o `SIGTERM`).
   - Si el proceso en ejecución no es Java o no corresponde al JAR esperado, la terminación se aborta inmediatamente emitiendo una advertencia de seguridad.
   - Suite unitaria `scripts/test_manage_services.py` con 6 casos de prueba (rechazo de PIDs reutilizados por otros procesos, PIDs inexistentes, mock de terminación segura).

3. **Restauración de Reproducibilidad del Frontend**:
   - Se eliminaron las reglas `.gitignore` que ignoraban `package-lock.json` y `**/package-lock.json`.
   - Se versionó `frontend/tienda/package-lock.json` para garantizar compilaciones e instalaciones deterministas en CI y entornos de prueba.

4. **Estado de Comprobante Incompleto y Reintento Seguro (`PedidoDetalle.jsx`)**:
   - Cuando el servicio Boleta (:8083) responde favorablemente pero Detalle Boleta (:8084) falla o no retorna líneas, la interfaz despliega un aviso explícito: *"Comprobante incompleto"* en lugar de afirmar erróneamente "0 líneas verificadas".
   - Se provee el botón *"Reintentar generación de detalle"*, el cual reintenta invocar exclusivamente `generarDetalleBoleta` usando el `boletaId` ya emitido, preservando la idempotencia y evitando duplicar la boleta.
   - Prueba unitaria añadida en `frontend/tienda/src/lib/api.test.js` validando la recuperación sin duplicidad.
