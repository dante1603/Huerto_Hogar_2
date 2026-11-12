# HuertoHogar · Microservicio de Usuario (avance Fullstack II)

Proyecto basado en **`microservicio_avance.zip`** de la carpeta de la asignatura.
Se reemplazó el ejemplo de **Vehículos** por el dominio **Usuario**, manteniendo el paquete Java `com.example.demo` para evitar cambios innecesarios en el punto de entrada.

## Qué incluye

- Spring Boot y Java **17+**, con Maven Wrapper (se conserva **Spring Boot 4.1.1** y **springdoc 3.1.0** de la plantilla original).
- Entidad `usuario` y campos de la pizarra: `id_usuario`, `nombre_usuario`, `rut_usuario`, `direccion_usuario`, `email_usuario`, `contrasena_usuario` (hash BCrypt), `fecha_hora_creacion_usuario`, `rol_usuario`.
- `POST /api/v1/usuarios` para registrar un usuario con rol `CLIENTE` y responder `201`.
- `GET /api/v1/usuarios/{id}` para leer un perfil público mínimo y responder `200` o `404`.
- Validación de campos, dígito verificador del RUT, correo único, RUT único; respuestas `400`, `404` y `409` según corresponda.
- Swagger UI: http://localhost:8080/swagger-ui.html y OpenAPI JSON: http://localhost:8080/v3/api-docs
- MySQL como base real; perfil `demo` con H2 en memoria, **solo** para comprobar el flujo en clases sin MySQL.
- Pruebas Java en `src/test` y ejemplos en `ejemplos/peticiones.http`.

## Inicio rápido 1: prueba de clase sin instalar MySQL (temporal)

**Windows (PowerShell), con Java 17 o superior y acceso a Internet para que Maven descargue dependencias por primera vez:**

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

**Linux/macOS:**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Con el perfil `demo`, H2 mantiene los datos **solo mientras la app está encendida**. El contenido se pierde al reiniciar. Es una demostración de endpoints, **no sustituye la integración MySQL exigida en la evaluación final**.

## Inicio rápido 2: MySQL real con Docker

1. Instala Java 17+ y Docker Desktop. Descomprime el ZIP.
2. Copia `.env.example` a `.env` en la raíz del proyecto. Este último no debe subirse a GitHub.
3. Abre una terminal en la carpeta raíz y ejecuta:

```bash
docker compose up -d
```

4. Espera a que MySQL esté listo; compruébalo con `docker compose ps`.
5. En Windows, ejecuta `iniciar-windows.cmd`; en Linux/macOS, `./iniciar-linux.sh` (o exporta las variables de `.env` antes de ejecutar `./mvnw spring-boot:run`).
6. La tabla `usuario` se crea automáticamente en desarrollo mediante JPA (`ddl-auto=update`).

Si ya tienes MySQL instalado, **no es obligatorio usar Docker**. Crea la BD ejecutando `sql/crear_base.sql`, usa credenciales de un usuario MySQL con permisos para esa BD y configura `DB_URL`, `DB_USER`, `DB_PASSWORD` como variables de entorno.

> El archivo `.env.example` contiene **solo credenciales de ejemplo para el laboratorio**. Cambia contraseñas para cualquier entorno compartido. No subas `.env` al repositorio.

## Probar los endpoints

Abre http://localhost:8080/swagger-ui.html o usa Postman/Thunder Client.

**1) POST** `http://localhost:8080/api/v1/usuarios` con encabezado `Content-Type: application/json` y cuerpo:

```json
{
  "nombreUsuario": "Cliente Prueba",
  "rutUsuario": "12.345.678-5",
  "direccionUsuario": "Santiago",
  "emailUsuario": "cliente.demo@example.com",
  "contrasena": "ClaveSegura123!"
}
```

Respuesta esperada `201 Created`, encabezado `Location: /api/v1/usuarios/1` (el ID puede cambiar):

```json
{
  "idUsuario": 1,
  "nombreUsuario": "Cliente Prueba",
  "rolUsuario": "CLIENTE",
  "fechaHoraCreacionUsuario": "2026-10-28T14:00:00"
}
```

**2) GET** `http://localhost:8080/api/v1/usuarios/1`, reemplazando el número por el ID devuelto. Respuesta `200 OK` y el mismo JSON. La respuesta **no contiene** RUT, dirección, correo ni contraseña.

**3) Errores:** repetir mismo RUT/correo → `409`; RUT inválido o datos incompletos → `400`; ID inexistente → `404`.

## Tests

```bash
./mvnw test
```

En Windows, `mvnw.cmd test`. Los tests usan H2 (`application-test.properties`) y no requieren MySQL. Hay pruebas de RUT, reglas del servicio y persistencia mediante JPA.

## Arquitectura

```text
Petición HTTP
  -> UsuarioController         /api/v1/usuarios
  -> UsuarioService            validación, normalización, BCrypt, rol CLIENTE
  -> UsuarioRepository         interfaz JpaRepository
  -> Usuario                   entidad JPA
  -> MySQL                     base huertohogar_usuarios
```

## Qué NO incluye y qué falta para la evaluación

- **No hay JWT, login, sesiones, roles administrados ni Spring Security HTTP**. Solo se usa el componente `spring-security-crypto` para calcular hashes BCrypt. **No usar con datos reales o exposición pública** sin incorporar autenticación, autorización, rate limiting y seguridad de despliegue.
- `GET` es **público y solo muestra cuatro campos mínimos**. Debe protegerse como parte de la etapa de seguridad de la evaluación.
- Faltan `PUT`, `DELETE`, consultas administrativas, integración React y pruebas de integración E2E.
- La pauta pide CRUD completo, Swagger, persistencia, JWT y frontend integrado **para la evaluación final**, no para el avance inicial de un endpoint descrito en clase.
- El modelo de la pizarra define `rol_usuario`, pero las reglas de autorización y asignación de otros roles quedan para la siguiente etapa. **El registro público nunca acepta el rol ADMINISTRADOR ni VENDEDOR**.

## Estado de verificación

El proyecto fue generado a partir del ZIP de ejemplo y revisado estructuralmente. **La ejecución de Maven/JUnit y la conexión a MySQL deben verificarse en un equipo con acceso a las dependencias Maven y a MySQL/Docker**. Consultar `VERIFICACION.md`.
