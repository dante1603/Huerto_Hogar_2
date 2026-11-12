# HuertoHogar × Galaxym — Fullstack II

Repositorio de integración del proyecto académico de tienda y entregas galácticas.

## Estructura real del repositorio

| Ruta | Contenido | Estado |
|---|---|---|
| `backend/catalogo/` | Spring Boot: consulta de productos, JPA, Swagger, tests | Implementado y comprobado por CI |
| `backend/usuarios/` | Usuarios, registro y JWT | 11 tests aprobados en CI |
| `frontend/tienda/` | Tienda React/Vite conectada a Catálogo y Usuarios | En PR #3, pendiente verificar frontend CI |\n| `index.html`, `*.html`, `assets/` | Tienda HTML original | **Legado, sin modificar** |
| `Huerto_React.zip` | Entrega React que sigue empaquetada | **Pendiente extraer/auditar** |
| `Huerto_Hogar_3-main.zip` | Entrega histórica empaquetada | No asumida como código vigente |
| `compose.yaml` | MySQL local para ambos servicios | Configurado para desarrollo |
| `docs/` | Decisiones, integración y contratos | Documentación actual |

**Regla de integración:** no reemplazar proyectos del equipo ni borrar los ZIP originales hasta haber validado su contenido y el trabajo vigente. No hay cambios en `Galaxym_solar_system` todavía.

## Ejecutar los microservicios sin MySQL (modo demostración)

Se requiere JDK 21 y Maven (o el wrapper Maven de Usuarios).

Terminal 1:

```bash
mvn -f backend/catalogo/pom.xml spring-boot:run -Dspring-boot.run.profiles=demo
```

Terminal 2:

```bash
JWT_SECRET_BASE64="$(openssl rand -base64 32)" SERVER_PORT=8081 mvn -f backend/usuarios/pom.xml spring-boot:run -Dspring-boot.run.profiles=demo
```

- Catálogo: http://localhost:8080/api/v1/productos
- Usuarios: http://localhost:8081/swagger-ui.html
- Catálogo Swagger: http://localhost:8080/swagger-ui.html

**Modo demo usa H2 en memoria, no guarda datos tras reiniciar.** Para persistencia local seguir `docs/INTEGRACION_LOCAL.md`.

## Tests

```bash
mvn -B -ntp -f backend/catalogo/pom.xml test
mvn -B -ntp -f backend/usuarios/pom.xml test
```

CI de GitHub Actions ejecuta ambos módulos sólo en PR que cambian backend o el workflow, o bajo ejecución manual. Concurrency cancela corridas anteriores del mismo PR. No ejecuta builds en cada push a main ni despliega a la nube.

## Estado de entrega

- Verificado: catálogo Java compilado, 2 tests HTTP sin errores (CI de PR #1).
- Verificado: código fuente de Usuarios importado desde `HuertoHogar-Usuarios.zip` del 10/11/2026; 6 tests aprobados en CI.
- Verificado en PR #2: login JWT, protección de perfiles y CORS; 11 tests Usuarios + 2 tests Catálogo.\n- En PR de frontend: React/Vite, catálogo consultado al backend y registro/login reales; checkout y stock sincronizado aún pendientes.\n- Pendiente: rate limiting, CRUD administrativo, pedidos, stock sincronizado, E2E, React visual y despliegue.
- La publicación en Cloud Run/Firebase/Cloud SQL requiere revisar presupuesto y vincular el proyecto Google Cloud explícitamente. **No se ha creado infraestructura facturable.**

Más contexto: [integración local](docs/INTEGRACION_LOCAL.md), [contratos](docs/CONTRATOS_API.md), [decisiones](docs/DECISIONES.md).
