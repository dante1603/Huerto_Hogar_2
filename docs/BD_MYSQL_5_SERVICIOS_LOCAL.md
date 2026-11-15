# EP3 — MySQL real para los cinco microservicios (Windows / PowerShell)

**Estado:** **Gate 1, Gate 2 y Gate 3 APROBADOS (PASS)** con Docker y MySQL 8.4.13 real.
Evidencia completa de ejecución, transaccionalidad, persistencia tras reinicio y renovación de refresh tokens documentada en [VERIFICACION_MYSQL_EP3.md](VERIFICACION_MYSQL_EP3.md).

Rama de preparación: `fix/mysql-cinco-esquemas-2026-11-15` (PR contra `integration/fullstack-2026-11-14`).

## Alcance exacto

Se utiliza un **único servidor MySQL 8.4 local** con cinco esquemas independientes, uno por servicio. Los servicios comparten la instancia de base de datos, **no las tablas**.

| Microservicio | Puerto HTTP local | Esquema |
| --- | --- | --- |
| Productos (`backend/catalogo`) | 8080 | `huertohogar_catalogo` |
| Usuarios (`backend/usuarios`) | 8081 | `huertohogar_usuarios` |
| Pedidos (`backend/pedidos`) | 8082 | `huertohogar_pedidos` |
| Boleta (`backend/boleta`) | 8083 | `huertohogar_boleta` |
| Detalle Boleta (`backend/detalle-boleta`) | 8084 | `huertohogar_detalle_boleta` |

`compose.yaml` crea Usuarios a través de `MYSQL_DATABASE`, Catálogo con `infra/mysql/01-create-catalog.sql`, y Pedidos/Boleta/Detalle con `infra/mysql/02-create-services.sql`. El usuario `huerto_app` recibe permisos sobre los cinco esquemas; no se utiliza root en Spring.

## Gate 0 — comprobar herramientas (PowerShell en la raíz del repositorio)

```powershell
docker --version
docker compose version
java -version
mvn -version
```

Docker Desktop debe estar abierto (motor Linux). Java 21 es el entorno previsto por CI, y los cinco proyectos compilan desde Maven. Si algún comando falla, anotar el error exacto **sin exponer credenciales** antes de seguir.

## Gate 1 — crear cinco esquemas, sin perder datos

1. Usar la rama indicada en la cabecera de este documento.
2. Preparar `.env` **solo local** a partir de `.env.example`, si no existe:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

Reemplazar `DB_PASSWORD` y `DB_ROOT_PASSWORD` por **dos contraseñas locales robustas distintas**. No subir `.env` a GitHub ni pegar sus contenidos en un chat. No cambiar las contraseñas del archivo si ya existe un volumen MySQL asociado sin planear la rotación: las variables del contenedor **no actualizan** credenciales de un volumen previamente inicializado.

3. Arrancar MySQL; comprobar estado y esperar `healthy`:

```powershell
docker compose config --quiet
docker compose up -d mysql
docker compose ps
```

4. Ejecutar este paso **también si ya existía un volumen**. El script `02` es idempotente, y Docker **solo corre los scripts de inicialización automáticamente al crear un volumen vacío**:

```powershell
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot < /docker-entrypoint-initdb.d/02-create-services.sql'
```

Este comando usa la clave de root **dentro del contenedor**, sin escribirla en el comando o historial del equipo.

5. Verificar acceso **como usuario de aplicación**, no como root:

```powershell
docker compose exec mysql mysql -uhuerto_app -p -e "SHOW DATABASES;"
```

MySQL pedirá la contraseña `DB_PASSWORD` de tu archivo `.env`. La salida debe mostrar los cinco nombres de la tabla anterior, además de `information_schema`. **No basta con que aparezca el contenedor en estado running.**

Si se listan menos de cinco: revisar `docker compose logs mysql` y el resultado del paso 4. Si aparece `Access denied`: distinguir si la clave de `.env` cambió con un volumen previo o si falta la concesión de permisos. **No ejecutar `docker compose down -v`: destruye el volumen y los datos.**

## Gate 2 — JPA sobre MySQL real (después del PASS del Gate 1)

Levantar cada Spring Boot **sin perfil demo/H2**, con el usuario `huerto_app`, su contraseña local y `DB_URL` por esquema. Puertos 8080–8084. Todos comparten `JWT_SECRET_BASE64` y `JWT_ISSUER` para el MVP local; nunca van a React. Para Usuarios usar `SERVER_PORT=8081`; los otros servicios usan `PORT` según sus `application.properties`.

Por cada servicio, comprobar que inicia sin error de conexión/JPA. Luego verificar en MySQL que Hibernate creó sus tablas reales, índices, claves y relaciones locales. Un servicio que arranca no demuestra aún que los cinco estén completos.

## Gate 3 — persistencia y flujo real (APROBADO — PASS)

Con las cinco JVM funcionando sobre MySQL 8.4 real:
1. Se registró un cliente y se autenticó obteniendo JWT y refresh token previo al reinicio.
2. Se crearon pedidos con bloqueo pesimista y descuento de inventario en Catálogo.
3. Se emitieron boletas académicas inmutables y líneas de detalle verificadas con importes exactos.
4. Se validó la idempotencia (reintentos idénticos devuelven 200 sin duplicar pedidos ni stock; reintentos con payload alterado devuelven 409).
5. Se reiniciaron en frío los cinco servicios; la persistencia de stock, pedidos, boletas y detalles se confirmó intacta.
6. Se renovó la sesión exitosamente usando el refresh token previo al reinicio, y se comprobó el bloqueo anti-replay (401 en reuso).

Evidencia completa y script ejecutable: `scripts/verify_mysql_gate3.py` y [VERIFICACION_MYSQL_EP3.md](VERIFICACION_MYSQL_EP3.md).

El reconciliador de Pedidos permanece **deshabilitado** (`RECONCILIACION_HABILITADA=false`) hasta que la recuperación ante fallos de red se valide explícitamente en el siguiente corte.

## Evidencia para cerrar este corte

Fecha: 15-11-2026. Rama: `fix/mysql-cinco-esquemas-2026-11-15`. MySQL: 8.4.13. Esquemas: `huertohogar_catalogo`, `huertohogar_usuarios`, `huertohogar_pedidos`, `huertohogar_boleta`, `huertohogar_detalle_boleta`.
Resultado: **Gate 1 PASS, Gate 2 PASS, Gate 3 PASS**.
