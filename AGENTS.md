# AGENTS.md — HuertoHogar / Fullstack II

Este repositorio es el workspace académico **Fullstack II**. Alcance vigente 24-11-2026: rúbrica EP3 y AWS; integración funcional con Galaxym descartada. `main` es la base de desarrollo validada por CI, no producción. Sus dependencias y limitaciones importan más que propuestas de reescritura.

## Estructura y autoridad
- `backend/catalogo/`: servicio Productos Spring Boot/JPA; conserva Pedidos heredado usado por React. 30 tests en CI.
- `backend/usuarios/`: registro seguro, JWT, refresh rotativo y 14 tests; no reemplazarlo con el ZIP obsoleto.
- `backend/pedidos/`: tercer servicio autónomo; reserva REST en Productos, intención durable y compensación opt-in.
- `backend/boleta/`: cuarto servicio, comprobantes académicos inmutables y vinculados a pedidos confirmados.
- `backend/detalle-boleta/`: quinto servicio, líneas verificadas por Boleta y Pedidos.
- `frontend/tienda/`: React/Vite editable. Consulta productos desde `GET /api/v1/productos`, registro/login desde Usuarios. El flujo de pedidos React sigue usando Catálogo heredado hasta la validación MySQL/navegador.
- `index.html`, páginas HTML y `assets/`: frontend **legado**, no borrarlo.
- `Huerto_React.zip`, `Huerto_Hogar_3-main.zip`: artefactos empaquetados. No asumir que son el frontend vigente sin extraer e inspeccionar su código.
- `docs/CONTRATOS_API.md`: estado de rutas reales versus propuestas.
- Galaxym vive en otro repositorio y queda fuera de alcance; no introducir nuevas integraciones, telemetría ni entregas del juego.

## Calidad y alcance
- Trabajar en ramas pequeñas con PR hacia `main`; PR #8 ya fusionada por autorización explícita del usuario. Promover cortes con pruebas y límites documentados, sin forzar ni desactivar protecciones. No declarar release, entrega ni AWS aprobados por un merge de desarrollo. PR #31 permanece abierta y debe integrar el tráfico React a cinco APIs tras cerrar el backend.
- Java 21 + Maven, cinco proyectos Spring independientes. No unir `pom.xml` ni homogeneizar paquetes a ciegas.
- CI backend ejecuta Maven para `catalogo`, `usuarios`, `pedidos`, `boleta` y `detalle-boleta`.
- Frontend: `cd frontend/tienda && npm install && npm run test && npm run build`. React usa proxy de Vite durante desarrollo; no exponer credenciales en variables `VITE_*`.
- Usar H2 en perfil demo o tests, MySQL en persistencia real. Una prueba H2 no prueba MySQL.
- Operaciones públicas de escritura requieren controles reales: JWT/roles, validación, transacciones e idempotencia cuando aplique. No publicar credenciales o tokens y no exponer `DB_PASSWORD` en repositorio.
- Priorizar backend y contratos: completar administración de usuarios, roles, pruebas Maven y REST/MySQL antes de ampliar React. Ver issue #19.

## GitHub Actions
- CI backend: cambios en `backend/**`, su workflow o scripts de verificación `scripts/e2e_http_fullstack.py` y `scripts/fault_proxy.py`. Estos scripts son parte del contrato REST backend y deben tener pruebas. Frontend CI continúa limitada a `frontend/tienda/**` o su workflow. Ambas aceptan ejecución manual.
- Agrupar commits antes de correr CI; se cancela el run anterior del mismo PR.
- No realizar commits solo para obtener logs; leer workflows y checks desde GitHub.
- No añadir deploy automático ni servicios de nube/facturación sin verificar proyecto y presupuesto.

## Criterio de finalización
Entregar commit/PR, diff revisable, tests concretos y límites claros (CI vs QA humano/MySQL/AWS/visual). Actualizar la documentación del propietario ante decisiones duraderas. No confundir una tarea preparada con implementación comprobada.

## Pedidos y reserva SQL
- Coexisten Pedidos legado de Catálogo (usado por React) y Pedidos independiente con reserva HTTP idempotente y compensación por tombstone. No eliminar legado ni redirigir React antes de MySQL real, fallos distribuidos y E2E navegador; no hay transacción global.
- `POST /api/v1/pedidos` exige JWT CLIENTE y `Idempotency-Key` UUID por intento (máx. una reserva por cliente/clave). Clave igual + payload diferente = 409.
- Los clientes solo pueden leer sus propios pedidos; vendedores y administradores leen pedidos pendientes, sin mutaciones de estado por ahora.
- Bloqueos pesimistas ordenados evitan sobreventa bajo base SQL; los reintentos nunca descuentan inventario dos veces.
- No cobrar ni simular un pedido como entregado. GalatiCoins son moneda ficticia.
- Backends Catálogo/Usuarios requieren misma clave JWT por desarrollo; no incluir secretos en Vite ni en código versionado.

## Evidencia y límites (15-11-2026)
- PR #27: cinco JVM independientes por HTTP/H2 con JWT, stock y comprobantes coherentes; PASS.
- PR #26: reconciliador `INICIADO → CANCELANDO → CANCELADO` desactivado por defecto; activación efímera solo para ensayos aislados de fallo.
- Clasificar como indeterminado cualquier fallo de transporte sin respuesta HTTP fiable, conservando la referencia para reintento. No confirmar compras ficticias ni compensar sin evidencia remota.
- Un PASS de H2/HTTP no demuestra MySQL, persistencia, navegador, despliegue ni aprobación de la entrega.
