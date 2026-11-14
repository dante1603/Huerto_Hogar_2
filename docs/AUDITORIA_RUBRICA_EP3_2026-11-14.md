# Auditoría de cumplimiento — DSY1104 Evaluación Parcial 3

Fecha de corte: **14-11-2026**. Evaluación del commit `4c64a557db37a3d5d47d8d2943923451150b3103` en `integration/fullstack-2026-11-14` del repositorio público `dante1603/Huerto_Hogar_2`.

**Dictamen actual: CORRECCIÓN REQUERIDA.** La integración automatizada funciona, pero faltan funciones evaluadas, pruebas de extremo a extremo y entregables documentales. Esta auditoría **no representa calificación oficial** ni valida la aplicación abierta en un navegador real.

## Fuentes propietarias y alcance

1. [Rúbrica DSY1104 Evaluación Parcial 3 — Estudiante](https://drive.google.com/file/d/18kJBae2l4Ah65LWYEkW6U33tCqwFIUOw/view) (Duoc UC; evaluaciones IE3.1.1 a IE3.3.6).
2. [Anexo 1 — Instrucciones Evaluación 3](https://drive.google.com/file/d/1U7bZVSOMwfoYaYly6IcSZ_BvYIgEn4lO/view) (roles, BD, entregables, tiempos).
3. [Caso original Forma A HuertoHogar](https://drive.google.com/file/d/1McH1k9AFlStwkY42rQfcTKwVsRFdor24/view) (requisitos de cliente: **el documento declara que el docente debe validar el alcance y no todos los requisitos se deben realizar**).
4. [Anexo 2 documentación APIs e integración](https://docs.google.com/document/d/1XeIyYQBkUGaMiNiX1WvhYu_E97LG2Sjk/edit) y [Anexo 3 planilla API](https://docs.google.com/spreadsheets/d/1yD7VMAcNg9VvztfmPqXuJL9RT1Uwo_zQ/edit): son plantillas de ejemplo **Pokémon**, no documentación de este sistema.
5. [Anexo 4 manual de usuario](https://docs.google.com/document/d/1WmY2QFZvhzkvhwPuoJM6bsYMNPjrSZwN/edit): plantilla con exigencia de capturas numeradas, título, borde, nota y descripción.

La rúbrica EP3 equivale al **40 % de la asignatura**. Dentro de EP3: **encargo grupal 40 %** y **presentación individual 60 %**. No confundir con EP2 (React) ni con la lista extensiva del caso; las funcionalidades opcionales o transformadas a Galaxym necesitan validación docente del alcance.

## Matriz: encargo grupal (40 % del puntaje EP3)

| Indicador | Ponderación | Estado verificado | Evidencia y brecha |
|---|---:|---|---|
| **IE3.1.1**: backend + BD + modelos/lógica de negocio | **8 %** | **PARCIAL** | Catálogo y Usuarios usan JPA, entidades, repositorios y servicios; `PedidoService` reserva inventario transaccional; tests backend pasan con H2. `compose.yaml` define MySQL con dos esquemas, **pero no se ha demostrado conexión, persistencia ni esquema de MySQL real**. Se necesita demo de crear/consultar/reiniciar/probar persistencia |
| **IE3.2.1**: endpoints CRUD REST Spring Boot y Swagger | **8 %** | **NO CUMPLE AÚN** | `ProductoController` solo `GET /productos` y `GET /productos/{id}`; `UsuarioController` solo POST y GET; `PedidoController` POST y GET. No hay `PUT/PATCH` ni `DELETE` de entidades administradas. Swagger está configurado en los dos servicios, pero falta capturar su despliegue real. Hace falta al menos CRUD completo de Productos con autorización ADMINISTRADOR y tests |
| **IE3.2.2**: integración backend/frontend REST | **6 %** | **PARCIAL** | React llama realmente a `GET /api/v1/productos`, registro/login y reserva de pedidos. Proxy Vite y CORS configurados. Falta prueba E2E de navegador + ambos Spring Boot + MySQL, errores de red y respuestas verificables |
| **IE3.3.1**: autenticación backend con roles y JWT | **6 %** | **PARCIAL, AVANZADO** | Usuarios emite JWT HS256 con rol y expiración; permisos server-side protegen POST pedidos, lectura de órdenes y perfiles; BCrypt y tests. Falta demostrar perfiles VENDEDOR y ADMINISTRADOR sin contraseñas embebidas; la pauta también pide comprobar renovación de JWT, que no aparece implementada. **Hallazgo de seguridad:** `GET /api/v1/pedidos/{id}` se autoriza con `.authenticated()`, y `PedidoService.detalle` solo niega explícitamente a CLIENTE ajeno; se requiere lista positiva de roles válidos también en detalle |
| **IE3.3.2**: sesión segura/persistente ante recarga | **6 %** | **PARCIAL** | `frontend/tienda/src/lib/session.js` guarda JWT en `sessionStorage`, lo recupera tras recarga en la misma pestaña y comprueba `expiresAt`. Falta prueba de recarga/expiración/logout y definir comportamiento de renovación. No afirmar persistencia entre cierres del navegador |
| **IE3.3.3**: restricciones funcionales por rol en frontend | **6 %** | **NO CUMPLE AÚN** | `AppRoutes.jsx` define rutas sin guardas por rol; la navegación solo oculta `/pedidos` cuando no hay sesión, pero permite tienda para VENDEDOR. `MisPedidos.jsx` impide consultar sin sesión válida, pero eso no equivale a layout/rutas por rol. La pauta exige ADMINISTRADOR acceso total, VENDEDOR solo lista/detalle de productos y órdenes, CLIENTE solo tienda. Faltan UI, protección de rutas, navegación y pruebas para esos tres perfiles |

### Matriz: presentación individual (60 % del puntaje EP3)

No es posible marcarla **PASS** con CI; es **VALIDACIÓN HUMANA PENDIENTE**. La rúbrica evalúa que cada integrante pueda explicar y demostrar estos seis puntos:

| Indicador | Ponderación | Evidencia de defensa que preparar |
|---|---:|---|
| **IE3.1.2** | **12 %** | Modelo relacional MySQL, entidades, repositorios, transacciones y reglas de reserva |
| **IE3.2.3** | **12 %** | CRUD y contratos HTTP en Swagger; mostrar solicitudes/respuestas y errores |
| **IE3.2.4** | **10 %** | Flujo React → API Spring → BD → respuesta React, con capturas y pruebas |
| **IE3.3.4** | **10 %** | JWT: emisión, firma/verificación, claims, expiración, roles y HTTP 401/403 |
| **IE3.3.5** | **8 %** | Recargar la vista manteniendo sesión; expirar, cerrar y reabrir la cuenta con seguridad |
| **IE3.3.6** | **8 %** | Tres recorridos por rol en la UI; demostrar que un rol no accede a las vistas ni acciones prohibidas |

**Formato declarado por Anexo 1:** 15 minutos de presentación por equipo más 5 minutos de preguntas. La rúbrica general exige entender el código y cambios GitHub, no solo mostrar pantallas.

## Entregables documentales exigidos en Anexo 1

| Entregable | Estado a 14-11-2026 | Criterio de cierre |
|---|---|---|
| GitHub público frontend | **DISPONIBLE** (monorepo) | Entregar URL pública exacta a `frontend/tienda/`; confirmar si el docente acepta monorepo en vez de dos repos separados |
| GitHub público backend | **DISPONIBLE** (monorepo) | Entregar URLs a `backend/catalogo/` y `backend/usuarios/`, y explicar que son dos procesos Spring independientes |
| Frontend comprimido | **PENDIENTE** | Generar ZIP limpio desde la versión final React, excluyendo node_modules/secrets |
| Backend comprimido | **PENDIENTE** | Generar ZIP limpio de los dos servicios, sin target/secrets |
| ERS terminado | **NO VERIFICADO COMO VIGENTE** | Existe una ERS histórica de HuertoHogar en Biblioteca, pero no se ha validado una revisión final ajustada al alcance Galaxym y a lo efectivamente entregado |
| Manual de usuario con pantallazos | **PENDIENTE** | Manual real por rol, instalación, uso, errores, imágenes numeradas + notas según Anexo 4 |
| Documentación de APIs (planilla) | **PENDIENTE** | Instanciar Anexo 3 con rutas reales, HTTP, payloads, códigos, autenticación, permisos, Swagger |
| Documento de APIs e integración | **PARCIAL** | Hay `docs/CONTRATOS_API.md`, `INTEGRACION_LOCAL.md` y `INTEGRACION_REACT.md`; falta documento final adaptado al Anexo 2 y evidencias visuales de llamadas y respuestas |

Las plantillas institucionales contienen ejemplos de *Pokémon Go*, una URL ajena y fechas de ejemplo: **no entregarlas sin sustituir íntegramente su contenido**.

## Criterios de salida: ningún PASS por suposición

### P0 — Impedimentos directos de la rúbrica

1. **CRUD de Productos con seguridad**: `GET/POST/PUT/DELETE` documentados en Swagger, con DTO validado y pruebas de éxito/error/autorización. Definir eliminación frente a pedidos reservados para preservar integridad; no borrar pedidos históricos ni permitir cambios de stock que invaliden reservas.
2. **UI y rutas por rol**: ADMINISTRADOR gestiona productos, usuarios según alcance autorizado y pedidos; VENDEDOR visualiza exclusivamente lista/detalle de productos y órdenes; CLIENTE solo tienda/carrito/pedidos propios. Mantener seguridad real en servidor y no confiar en el menú como frontera de autorización.
3. **JWT y permisos de detalle**: exigir explícitamente roles en `GET /pedidos/{id}` y comprobar propiedad del cliente; incorporar tests 401/403 para cada endpoint y rol. Establecer una provisión segura y reproducible de cuentas VENDEDOR y ADMINISTRADOR para demo, sin cuentas privilegiadas por registro público.
4. **Política de sesión/JWT**: demostrar persistencia tras recarga, expiración y logout; definir estrategia segura de renovación o documentar y consultar la expectativa del docente, pues el enunciado menciona renovación de tokens.

### P1 — Pruebas integrales y evidencia

5. **MySQL real**: iniciar `docker compose` y los dos servicios con el mismo JWT de desarrollo; crear/leer un producto, hacer reserva autenticada, reiniciar los servicios y confirmar persistencia; registrar estructura/tablas y resultados. Las pruebas MockMvc/H2 actuales **no** cubren MySQL.
6. **REST E2E**: login por rol, catálogo, CRUD permitido/prohibido, carrito y pedido, reacción ante stock insuficiente, idempotencia, restauración de sesión, Swagger vivo. Guardar evidencia de solicitud, respuesta, BD y UI con fecha y commit.
7. **Verificación visual**: navegador y móvil para las rutas obligatorias, carga, errores, formulario, tabla administrativa y mensajes. Los seis modelos procedurales experimentales son **no aprobados**; no son requisito de EP3.

### P2 — Paquete evaluable y defensa

8. **Cerrar ERS** con el alcance aprobado: original Forma A vs adaptación Galaxym, trazando qué requisito se cubre/transforma/posterga. El original explícitamente requiere validación de alcance por docente.
9. **Completar Anexos 2, 3 y 4** con datos reales, capturas, endpoint/método/códigos/rol, manual de instalación y navegación.
10. **Empaquetar y ensayar** GitHub público + ZIP frontend/backend + documentos + presentación de 15 minutos + preguntas individuales. Tener cuentas/BD de prueba reproducibles sin secretos versionados.

## Misiones técnicas sugeridas para agente implementador

**Misión A — CRUD Catálogo (P0).** Propiedad: `backend/catalogo/`. Objetivo: operaciones CRUD de producto con DTO/validación, roles ADMINISTRADOR, Swagger y pruebas de flujos 200/201/204/400/401/403/404/409. Invariantes: carrito/pedidos idempotentes, stock transaccional, acceso de lectura al cliente, compatibilidad con MySQL/H2. Fuera de alcance: Galaxym, inventario del juego, cambios visuales, pagos y telemetría. Evidencia: PR hacia integración + diff + CI + verificación manual Swagger.

**Misión B — Permisos UI (P0).** Propiedad: `frontend/tienda/`. Objetivo: rutas y layout por rol con guardas, menú y vistas ADMINISTRADOR/VENDEDOR/CLIENTE, componentes admin para operaciones realmente existentes. Fuera de alcance: rediseño 3D o seguimiento orbital. Invariantes: no exponer acciones por UI sin permiso; backend revalida siempre; no simular éxito CRUD. Evidencia: pruebas de navegación por los tres roles, 401/403 y E2E.

**Misión C — Seguridad/identidad (P0).** Propiedad: `backend/usuarios`, `backend/catalogo`, frontend sesión. Objetivo: pruebas exhaustivas de JWT/roles/propiedad, forma controlada de crear cuentas demo de VENDEDOR/ADMINISTRADOR y decisión sobre renovación. Fuera de alcance: credenciales hardcodeadas, token en URL/localStorage, endpoints privilegiados públicos.

## Fuera del alcance evaluado directo

La **telemetría orbital real**, la **terminal del repartidor Galaxym**, los **seis modelos 3D provisionales**, el **cofre del juego**, pagos ficticios avanzados y el seguimiento de nave **no sustituyen** los indicadores CRUD, JWT, BD relacional ni la documentación. Pueden mantenerse como ampliaciones posteriores sin bloquear la rúbrica EP3. Los requerimientos extra del Caso Forma A solo pasan a obligatorios si forman parte del alcance validado por el docente.

## Evidencia y límites actuales

- `4c64a55` tiene CI backend y frontend `success`, pero esa CI ejecuta pruebas unitarias/MockMvc/H2 y compilación React; **no** demuestra MySQL, navegador, Swagger visible, roles completos ni E2E.
- Consultados explícitamente `ProductoController`, `UsuarioController`, `PedidoController`, `PedidosSecurityConfig`, `SecurityConfig`, `AppRoutes.jsx`, `MisPedidos.jsx`, `session.js`, `LoginService`, `PedidoService` y archivos de configuración.
- No hay un grado porcentual de cumplimiento válido hasta recopilar evidencia por indicador. Usar **PASS**, **CORRECCIÓN REQUERIDA**, **BLOQUEADO** o **VALIDACIÓN HUMANA PENDIENTE** por subcriterio.

**Siguiente movimiento técnico permitido:** entregar Misión A a un agente de código, luego auditar su PR y pasar a Misión B. No fusionar a `main` ni realizar deploy público hasta completar las comprobaciones y documentación.
