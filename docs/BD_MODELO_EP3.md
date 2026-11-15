# Modelo de datos — entrega grupal DSY1104 EP3

**Estado:** construcción respaldada por entidades JPA de la rama integration/fullstack-2026-11-14, revisión 14-11-2026. **NO verificado todavía en MySQL real.** Autoridad técnica: backend/catalogo y backend/usuarios; autoridad de rúbrica: DSY1104 Evaluación Parcial 3 y Anexo 1 del Drive Duoc.

## 1. Arquitectura y propiedad de datos

MySQL 8.4 (compose.yaml) tiene un solo servidor local y dos esquemas independientes:

- huertohogar_usuarios — propiedad del microservicio Usuarios. Entidad Usuario.
- huertohogar_catalogo — propiedad del microservicio Catálogo. Entidades Producto, Pedido y LineaPedido.

React **nunca** se conecta directamente a MySQL: solo realiza solicitudes REST a los servicios Spring Boot. Los dos servicios verifican JWT emitidos por Usuarios y comparten en desarrollo el mismo JWT_SECRET_BASE64 y JWT_ISSUER.

La relación entre pedido.cliente_id y usuario.id_usuario es una **referencia lógica entre esquemas** y no es una Foreign Key JPA entre servicios. Cada servicio gestiona su propio esquema.

~~~mermaid
erDiagram
  USUARIO {
    BIGINT id_usuario PK
    VARCHAR nombre_usuario
    VARCHAR rut_usuario UK
    VARCHAR email_usuario UK
    VARCHAR direccion_usuario
    VARCHAR contrasena_usuario
    DATETIME fecha_hora_creacion_usuario
    VARCHAR rol_usuario
  }
  PRODUCTO {
    BIGINT id PK
    VARCHAR sku UK
    VARCHAR nombre
    VARCHAR descripcion
    DECIMAL precio
    INT stock
    VARCHAR planeta_origen
  }
  PEDIDO {
    BIGINT id PK
    BIGINT cliente_id
    VARCHAR clave_idempotencia
    VARCHAR planeta_destino
    VARCHAR estado
    DECIMAL total
    DATETIME creado_en
  }
  LINEA_PEDIDO {
    BIGINT id PK
    BIGINT pedido_id FK
    BIGINT producto_id
    VARCHAR nombre_producto
    INT cantidad
    DECIMAL precio_unitario
    DECIMAL subtotal
  }
  USUARIO ||..o{ PEDIDO : "clienteId logico (sin FK)"
  PEDIDO ||--|{ LINEA_PEDIDO : contiene
  PRODUCTO ||..o{ LINEA_PEDIDO : "referencia e instantanea (sin FK)"
~~~

La notación discontinua indica referencia por ID, no FK SQL. En la tabla LINEA_PEDIDO se copia nombre/precio para preservar el historial; el servicio impide borrar un producto con pedidos anteriores.

## 2. Campos y restricciones implementados

| Tabla | Campos relevantes | Restricciones |
|---|---|---|
| usuario | id_usuario, nombre_usuario, rut_usuario, direccion_usuario, email_usuario, contrasena_usuario (hash), fecha_hora_creacion_usuario, rol_usuario | PK autoincremental; RUT y email únicos; no nulos; rol enum CLIENTE/VENDEDOR/ADMINISTRADOR |
| producto | id, sku, nombre, descripcion, precio decimal(12,0), stock, planeta_origen | PK autoincremental; SKU único; atributos no nulos; DTO de escritura exige precio y stock >= 0 |
| pedido | id, cliente_id, clave_idempotencia, planeta_destino, estado, total decimal(14,0), creado_en | PK autoincremental; UNIQUE(cliente_id, clave_idempotencia); estado PENDIENTE (único actual) |
| linea_pedido | id, pedido_id, producto_id, nombre_producto, cantidad, precio_unitario decimal(12,0), subtotal decimal(14,0) | PK; pedido_id FK por @ManyToOne; cantidad validada por endpoint |

**Precisión:** el tamaño SQL exacto de ID, índices y tipos de tiempo debe contrastarse con el DDL generado por Hibernate sobre MySQL antes de cerrar la documentación. La tabla describe el mapeo JPA, no un resultado de INFORMATION_SCHEMA ya ejecutado.

## 3. Reglas de negocio y transacción

1. El cliente se registra; la contraseña se transforma en hash BCrypt y el rol del registro público es CLIENTE (no permite autoasignar privilegios).
2. El cliente se autentica; Usuarios emite JWT HS256 con subject=idUsuario, issuer, rol y caducidad.
3. El cliente envía su pedido al Catálogo con un UUID Idempotency-Key. El servidor valida cantidades, destino y JWT.
4. En una misma transacción SQL, PedidoService ordena los IDs, bloquea productos con PESSIMISTIC_WRITE, comprueba inventario, descuenta stock, guarda Pedido y LineaPedido.
5. Mismo cliente + mismo UUID + mismo contenido no duplica descuentos; UUID reutilizado con distinta carga da 409.
6. Solo ADMINISTRADOR crea, modifica o elimina productos. No se elimina un producto que ya esté referenciado por líneas históricas: 409.

El estado único de pedidos es **PENDIENTE**; no existen pagos, facturas, despacho ni seguimiento real. No documentarlos como implementados.

## 4. Inicialización disponible, no verificada

- compose.yaml: mysql:8.4, volumen persistente huerto_mysql_data, puerto local 127.0.0.1:3306.
- infra/mysql/01-create-catalog.sql: crea el segundo esquema y concede acceso al usuario huerto_app. El primero lo crea MYSQL_DATABASE.
- Spring: application.properties apunta a MySQL; la URL real de cada servicio varía por esquema. En perfil demo se usa H2 en memoria, sin persistencia.
- **Situación actual:** spring.jpa.hibernate.ddl-auto=update para ambos servicios en perfil real, y create-drop con H2 demo. Este esquema automático es práctico para laboratorio, pero no sustituye migraciones versionadas ni pruebas de actualización desde una BD existente.

El contenedor ejecuta los scripts de docker-entrypoint-initdb.d **solo en la primera inicialización del volumen**. No eliminar volúmenes con datos importantes.

## 5. Preparación segura de roles para la validación final

La UI permite crear CLIENTE, pero aún no existe un endpoint público de provisión de usuarios privilegiados (y no debe existir sin una autorización robusta).

Procedimiento controlado de laboratorio ejecutado y validado en Gate 3 (ver docs/VERIFICACION_MYSQL_EP3.md):
1. Aprovisionamiento controlado de una cuenta de prueba `ADMINISTRADOR` efímera con credencial dinámica generada en memoria (sin hashes ni contraseñas estáticas en git), inscrita con rol base `CLIENTE` y posterior elevación en la base de datos local `huertohogar_usuarios.usuario`, garantizando que el registro público continúe restringido exclusivamente a `CLIENTE`. El runner es compatible tanto con bases de datos preexistentes (donde revoca y deshabilita la cuenta histórica `admin.gate3@huertohogar.cl` a rol `CLIENTE` y verifica rechazo HTTP 401) como con bases de datos limpias (donde verifica su ausencia y rechazo 401 sin abortar).
2. Iniciar sesión mediante `POST /api/v1/auth/login` para emitir el JWT con claim `"rol": "ADMINISTRADOR"`.
3. Crear productos de prueba aislados mediante la API REST de Catálogo (`POST /api/v1/productos`) usando dicho JWT.
4. Al concluir la suite, el administrador efímero es degradado a rol `CLIENTE` y su credencial invalidada.
5. **Limitación de seguridad de JWT emitidos:** La degradación de rol en la tabla `usuario` y el bloqueo de contraseña invalidan inmediatamente nuevos logins y renovaciones (`POST /api/v1/auth/refresh`), pero **no revocan de forma instantánea los JWT Bearer ya emitidos** mientras no expire su vigencia (30 minutos en `LoginService`), debido a la naturaleza autocontenida y sin blacklist de los tokens JWT en esta arquitectura. En este entorno de laboratorio, la parada completa de los procesos JVM gestionados al finalizar la prueba asegura el cese efectivo de cualquier sesión activa.
6. El rol `VENDEDOR` permanece reservado para futuras fases donde se incorporen transiciones operativas de despacho de pedidos.

## 6. Estado de validación con MySQL 8.4 (Gate 3 PASS)

- Contenedor `huertohogar2-mysql-1` ejecutado con cinco esquemas independientes para `huerto_app`.
- Aprovisionamiento controlado de ADMINISTRADOR en BD local y alta de productos de prueba por API REST con JWT de administrador.
- Ejecución de compra E2E completa: Pedidos -> Catálogo -> Boleta -> Detalle Boleta.
- Idempotencia validada: 200 en reintento con misma clave, 409 con payload alterado, sin doble descuento de stock.
- Persistencia confirmada tras reinicio en frío de las cinco JVMs sin eliminar el volumen MySQL.
- Renovación de sesión con refresh token emitido antes del reinicio y rechazo anti-replay comprobado.
- Evidencia detallada registrada en `docs/VERIFICACION_MYSQL_EP3.md` y suite automatizada en `scripts/verify_mysql_gate3.py`.
- **Estado:** **Gate 1, Gate 2 y Gate 3 APROBADOS (PASS)**. Integración y conmutación de tráfico en React pendientes para la siguiente fase.

## 7. Sesiones de renovación añadidas a Usuarios

La tabla JPA refresh_sesion (microservicio Usuarios) agrega: id autoincremental PK, token_hash varchar(64) único y no nulo, usuario_id bigint no nulo, expira_en timestamp/fecha no nulo. El token aleatorio de 256 bits se entrega una sola vez y se persiste únicamente su digest SHA-256. La renovación usa lock pesimista, consume la sesión anterior y registra otra en una transacción. El vínculo usuario_id es lógico (sin @ManyToOne/FK explícita).

**Pendiente final:** comprobar creación real de refresh_sesion en MySQL, índices, fecha de expiración, rotación bajo solicitudes concurrentes y comportamiento después de reiniciar Spring.


## Nueva persistencia de reservas de Productos (corte 5 microservicios)

Entidades JPA: **reserva_stock** (referencia UUID PK, huella_solicitud SHA-256, estado RESERVADA/CANCELADA, total, creada_en) y **linea_reserva** (FK reserva_referencia, referencia lógica producto_id, nombre_producto, cantidad, precio_unitario, subtotal). Un PUT crea reserva, disminuye stock y guarda snapshots en la **misma transacción local de Productos**. DELETE marca CANCELADA y restaura stock una sola vez; cancelación previa crea tombstone. No es una transacción distribuida Pedidos-Productos. Esquema físico MySQL pendiente de validar al final.
