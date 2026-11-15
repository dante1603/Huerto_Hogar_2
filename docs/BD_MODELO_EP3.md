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

Procedimiento controlado de laboratorio, pendiente de ejecutar:
1. Registrar dos cuentas de prueba con RUT y email válidos (una destinada a VENDEDOR y otra a ADMINISTRADOR), sin usar credenciales de producción.
2. Un operador con permisos sobre la BD local asigna manualmente rol_usuario VENDEDOR / ADMINISTRADOR exclusivamente a los IDs identificados y documenta el cambio. **Nunca** exponer esta operación como formulario público ni incluir contraseñas en Git.
3. Iniciar sesión nuevamente; el JWT nuevo debe contener el rol vigente. El token antiguo no adquiere permisos por editar sessionStorage.
4. Probar permisos de lectura/escritura y guardar evidencia. Una actualización de rol en la BD no invalida automáticamente JWT anteriormente emitidos; considerar esa limitación de este MVP.

Este procedimiento requiere ejecución humana autorizada y no está automatizado ni realizado.

## 6. Prueba final reservada (no ejecutar todavía)

- Iniciar contenedor, comprobar healthy, esquemas, columnas, índices y constraints. SQL de observación: docs/sql/verificacion_mysql_ep3.sql.
- Ejecutar CRUD administrativo y consultar tablas; reiniciar servicios y verificar supervivencia de filas.
- Emitir pedido; comparar producto.stock antes/después; confirmar que línea histórica no cambia si se edita el producto.
- Comprobar idempotencia con misma clave y rechazo del pedido si no hay stock.
- Validar contraseñas hash y permisos 401/403. Guardar capturas de Swagger y UI real.
- Comparar estructuras reales con JPA; corregir discrepancias ANTES de reemplazar ddl-auto=update por migraciones/validate.

**Pendiente de aceptación:** MySQL no se ha iniciado ni verificado como parte de esta preparación.
