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
