# Decisiones de integración — 2026-11-12

## Fijadas para esta fase

- Repositorio de integración: `dante1603/Huerto_Hogar_2`.
- Código de Galaxym permanece en `dante1603/Galaxym_solar_system`; no copiar el simulador ni tocar su rama principal.
- Catálogo y Usuarios: dos Spring Boot independientes dentro de `backend/`, cada cual con su `pom.xml`; 2 + 6 tests aprobados en CI del 12/11/2026.
- Contratos REST versionados `/api/v1`; OpenAPI/Swagger.
- MySQL local compartido como **servidor**, con esquemas independientes `huertohogar_catalogo` y `huertohogar_usuarios`; JPA para pruebas de persistencia.
- Cambios por PR y CI filtrada; no desplegar ni generar infraestructura facturable sin verificar acceso y costos.
- El frontend heredado queda sin modificar; primero recuperar el código React editable y sus contratos.
- Seguridad JWT, permisos CLIENTE/VENDEDOR/ADMIN y aislamiento de telemetría antes de habilitar operaciones mutables públicas.

## Por decidir con evidencia

- Proyecto Google Cloud/Firebase y presupuesto efectivo, región, hosting.
- Modelo de pedido, claves de autorización del cliente y del juego y transacciones de stock.
- Modelo de sincronización de cofres con latencia, desconexión y no duplicación.
- Integración con el microservicio Boletas, una vez auditado su código.
- Integración y pruebas con el visor `src/ui/system-view` del juego; tiempo orbital y coordenadas deben referir a una fuente común.

## Calidad

No confundir resultados de MockMvc/H2 con pruebas de conectividad MySQL, Cloud Run ni flujos end-to-end. Fuente de verdad de compilación: GitHub Actions del commit concreto. Registrar los límites antes de fusionar.

## Seguridad JWT — 12/11/2026

- Usuarios emite JWT HS256 con firma controlada por `JWT_SECRET_BASE64` (mínimo 32 bytes), sin secreto versionado.
- Vencimiento inicial: 30 minutos; rol en claim `rol`, sujeto `sub` numérico de usuario; validación de emisor y expiración.
- Registro público genera únicamente CLIENTE. Lectura de perfiles permite únicamente propietario o ADMINISTRADOR.
- CORS se configura mediante lista explícita `APP_ALLOWED_ORIGINS`; sin orígenes abiertos por defecto.
- Cobertura en GitHub Actions: Usuarios 11/11 y catálogo 2/2. Pendientes: limitar intentos de login, control de cuentas, revocación, política de sesiones frontend, seguridad del resto de servicios y revisión antes de exponer datos reales.

## Pedidos y stock (12/11/2026)

- Se implementan en Catálogo **temporalmente como módulo interno** porque productos, pedidos y descuento de inventario requieren una misma transacción SQL. Separarlos en microservicios sin outbox/sagas expondría a sobreventas.
- Primer estado: PENDIENTE. Es una reserva, no aceptación por piloto ni prueba de pago/entrega.
- Identidad del cliente extraída exclusivamente del JWT, nunca del JSON del navegador.
- Reintentos: cabecera obligatoria `Idempotency-Key` UUID y constraint UNIQUE en (cliente, clave). Repetir el mismo pedido no vuelve a reservar; la misma clave con payload diferente produce 409.
- Stock: bloqueo pesimista por producto en orden de ID; se valida y descuenta dentro de la misma transacción, con rollback completo ante errores.
- Precio y nombre del producto se guardan como instantánea en cada línea; el navegador no decide el monto.
- GalatiCoins es la unidad ficticia del catálogo: **no se realiza cargo bancario**.
- JWT HS256: las dos APIs reciben el mismo secreto **solo en MVP local**, con emisor igual. Se recomienda migrar a claves públicas para verificadores antes de despliegue con datos reales.
- Quedan pendientes cancelación con devolución de stock, reconciliación de cofres del juego, despacho, notificaciones, OTP y E2E/MySQL real.
