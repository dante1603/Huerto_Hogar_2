# AGENTS.md — HuertoHogar × Galaxym

Este repositorio es el workspace principal de integración **Fullstack II**. Sus dependencias y limitaciones importan más que propuestas de reescritura.

## Estructura y autoridad
- `backend/catalogo/`: catálogo Spring Boot + JPA, 2 tests de MockMvc.
- `backend/usuarios/`: microservicio Usuarios del equipo, origen ZIP compartido; 6 tests. No reinventarlo.
- `frontend/tienda/`: React/Vite editable. Consulta productos desde `GET /api/v1/productos`, registro/login desde Usuarios. El carrito es solo local y no debe fingir pedidos.\n- `index.html`, páginas HTML y `assets/`: frontend **legado**, no borrarlo.
- `Huerto_React.zip`, `Huerto_Hogar_3-main.zip`: artefactos empaquetados. No asumir que son el frontend vigente sin extraer e inspeccionar su código.
- `docs/CONTRATOS_API.md`: estado de rutas reales versus propuestas.
- Galaxym vive en otro repo, `dante1603/Galaxym_solar_system`; toda integración con juego es por una API controlada y autenticada.

## Calidad y alcance
- Trabajar en rama, cambios pequeños y PR contra `main`; no force-push ni sobrescribir fuentes externas.
- Java 21 + Maven, dos proyectos independientes. No unir `pom.xml` ni homogeneizar paquetes a ciegas.
- `mvn -B -ntp -f backend/catalogo/pom.xml test`; `mvn -B -ntp -f backend/usuarios/pom.xml test`.\n- Frontend: `cd frontend/tienda && npm install && npm run test && npm run build`. React usa proxy de Vite durante desarrollo; no exponer credenciales en variables `VITE_*`.
- Usar H2 en perfil demo o tests, MySQL en persistencia real. Una prueba H2 no prueba MySQL.
- Operaciones públicas de escritura requieren controles reales: JWT/roles, validación, transacciones e idempotencia cuando aplique. No publicar credenciales o tokens y no exponer `DB_PASSWORD` en repositorio.
- No inventar sincronización juego-cofre: diseñar stock con origen, versión, tiempos y política de desconexión antes de cambios persistentes.

## GitHub Actions
- CI backend limitada a cambios en `backend/**` o su workflow. CI frontend limitada a `frontend/tienda/**` o su workflow. Ambas aceptan ejecución manual.
- Agrupar commits antes de correr CI; se cancela el run anterior del mismo PR.
- No realizar commits solo para obtener logs; leer workflows y checks desde GitHub.
- No añadir deploy automático ni servicios de nube/facturación sin verificar proyecto y presupuesto.

## Criterio de finalización
Entregar commit/PR, diff revisable, tests concretos y límites claros (CI vs QA humano/MySQL/Cloud Run/visual). Actualizar la documentación del propietario ante decisiones duraderas. No confundir una tarea preparada con implementación comprobada.
