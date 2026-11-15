# ERS — HuertoHogar × Galaxym | Entrega grupal EP3 DSY1104

**Versión:** 0.9 (borrador técnicamente trazado, no aprobado por docente). **Fecha:** 14-11-2026. **Producto:** tienda académica React y dos microservicios Spring Boot.

## 1. Propósito, propietario y alcance

Documento de especificación de requisitos para la **Evaluación Parcial 3 de Desarrollo Fullstack II**. Referencias:
- Caso Forma A HuertoHogar: https://drive.google.com/file/d/1McH1k9AFlStwkY42rQfcTKwVsRFdor24/view
- Rúbrica EP3: https://drive.google.com/file/d/18kJBae2l4Ah65LWYEkW6U33tCqwFIUOw/view
- Anexo 1 EP3: https://drive.google.com/file/d/1U7bZVSOMwfoYaYly6IcSZ_BvYIgEn4lO/view

El caso original describe un comercio de productos agrícolas en Chile. La adaptación del equipo usa un **catálogo ficticio interplanetario** para demostrar arquitectura, catálogo, seguridad, pedidos y gestión. El caso aclara que el **alcance debe ser validado por el docente**: en esta revisión NO existe evidencia de aprobación formal de la adaptación. Antes de entregar hay que documentar dicha validación o adecuar las entidades y datos al caso tradicional.

### Incluido en EP3

React (catálogo, carrito, cuenta, historial, paneles por rol) ↔ REST con API versionada ↔ dos Spring Boot (Usuarios y Catálogo) ↔ MySQL relacional con dos esquemas.

### Fuera del alcance del MVP evaluable

Pagos reales, generación fiscal de boletas, reparto Galaxym, posición en vivo, mapa orbital operativo, sistemas de inventario del juego, reseñas, fidelización, entregas completas y renovación JWT (pendiente de definir/implementar). Los seis modelos visuales provisionales no acreditan indicadores de EP3.

## 2. Perfiles y permisos

| Actor | Operaciones |
|---|---|
| Invitado | Explorar tienda, registrarse/iniciar sesión; carrito local sin crear pedido |
| CLIENTE | Tienda, carrito, crear reserva con JWT, pedidos propios y detalle propio |
| VENDEDOR | Consultar lista y detalle de productos; consultar órdenes pendientes y su detalle; sin compra/escritura |
| ADMINISTRADOR | CRUD Productos, consulta de productos/órdenes y acceso general según el alcance implementado |

La UI restringe rutas y acciones; **Spring Security verifica realmente JWT y rol**. El registro público asigna siempre CLIENTE.

## 3. Requisitos funcionales (RFC) verificables

| ID | Requisito y criterio de aceptación | Estado de código |
|---|---|---|
| RF-01 | Registro de cliente con nombre, RUT validado, dirección, email único y contraseña segura; no puede definir su rol | Implementado, pruebas de backend |
| RF-02 | Inicio de sesión por credenciales; emitir JWT firmado con issuer, subject, rol y expiración | Implementado, pruebas de backend |
| RF-03 | Recuperar sesión en recarga de la misma pestaña; impedir rutas privadas después de caducidad; permitir cerrar sesión | Implementado, pruebas Node; revisión navegador pendiente |
| RF-04 | Consultar productos desde API, con datos reales de BD, filtros y detalle | Implementado, consulta de Vendedor por GET ID |
| RF-05 | CLIENTE agrega/quita productos del carrito, ve total y limitaciones de stock | Implementado, tests Node |
| RF-06 | CLIENTE crea pedido con destino y cantidades; servidor valida inventario e idempotencia, reserva stock de manera atómica y genera estado PENDIENTE | Implementado, tests MockMvc/H2 |
| RF-07 | CLIENTE consulta sus pedidos y detalle, sin conocer órdenes de otros clientes | Implementado, autorización server-side reforzada |
| RF-08 | VENDEDOR ve exclusivamente consultas de productos y órdenes, sin mutaciones | Implementado, pruebas de política de rutas; E2E pendiente |
| RF-09 | ADMINISTRADOR crea, actualiza y elimina productos; DELETE con historial devuelve 409 | Implementado, MockMvc/H2 y UI React |
| RF-10 | Documentar endpoints con Swagger/OpenAPI y comunicar frontend/backend por REST | Configurado; prueba visual y E2E pendiente |
| RF-11 | Mantener persistencia real en MySQL tras reiniciar servicios | **Pendiente de validar** (postergado expresamente al corte final) |
| RF-12 | Renovar credenciales/tokens de forma segura cuando corresponda | **Pendiente**; no se presenta como logrado |

## 4. Requisitos no funcionales

| ID | Requisito | Medio de verificación |
|---|---|---|
| RNF-01 | Contraseñas solo con hash BCrypt; no exponer secretos en React/repositorio | Revisión de entidades, DTO, BCrypt y CI |
| RNF-02 | API por prefijo /api/v1 y errores HTTP consistentes | Tests MockMvc / Swagger |
| RNF-03 | No permitir pedidos de otros clientes ni CRUD sin ADMINISTRADOR | Tests 401/403, rol y propiedad |
| RNF-04 | Operación de pedidos transaccional e idempotente bajo concurrencia | Tests de H2 y pendiente MySQL |
| RNF-05 | Layout responsive y estados de loading/error sin falsificar datos | Build + revisión de navegador pendiente |
| RNF-06 | Configuración local reproducible sin credenciales subidas a GitHub | compose.yaml, .env.example y manual |

## 5. Diseño y datos

Véase docs/BD_MODELO_EP3.md: esquema Usuarios, esquema Catálogo, entidad Producto, Pedido y LineaPedido, vínculo lógico por clienteId, snapshot de líneas e índices únicos.

El sistema opera con precio expresado en **GalatiCoins ficticios**. No hay pagos reales. La interfaz no debe afirmar que el pedido se entregó.

## 6. Casos de aceptación para el último corte

1. Arrancar MySQL y ambos Spring con la misma firma JWT; registro y login de CLIENTE; consultar catálogo; crear pedido y verificar reserva.
2. Registrar producto en panel ADMIN; consultar GET; modificarlo; eliminar uno sin pedidos y rechazar eliminación de otro con pedido histórico.
3. Entrar como VENDEDOR, consultar detalles de productos y órdenes; comprobar ausencia de acciones administrativas.
4. Entrar como CLIENTE B, intentar GET de pedido CLIENTE A y recibir 403.
5. Recargar React manteniendo sesión; expirar/logout; verificar que las rutas protegidas desaparecen.
6. Reiniciar Spring y comprobar persistencia MySQL; guardar evidencia de Swagger y SQL.

## 7. Trazabilidad a rúbrica

- IE3.1.1: RNF-04, RNF-06, RF-06, RF-11 y modelo de BD.
- IE3.2.1: RF-04, RF-09 y RF-10 (CRUD + Swagger).
- IE3.2.2: RF-01, RF-04 a RF-07 y RF-10 (REST integrado).
- IE3.3.1: RF-02, RNF-01 y RNF-03 (JWT/roles).
- IE3.3.2: RF-03 y RF-12 (persistencia/renovación).
- IE3.3.3: RF-07 a RF-09 (restricciones por rol).

**Deuda documental:** aprobación del alcance adaptado por docente, autoría/equipo, fecha de entrega, captura de pruebas reales y cierre de RF-11/RF-12.
