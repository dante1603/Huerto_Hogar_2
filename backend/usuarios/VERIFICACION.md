# Verificación del avance

## Alcance de la entrega

- Microservicio de Usuario, basado en el código de Vehículos de la asignatura.
- La imagen de pizarra se usó como fuente para los ocho campos de Usuario.
- Rutas versionadas `/api/v1/usuarios`.
- POST de registro + GET por ID; seguridad avanzada pendiente (JWT).

## Criterios de aceptación en tu computador

1. Java 17+ detectado: `java -version`.
2. El proyecto abre y compila: `mvnw.cmd test` (Windows) o `./mvnw test` (Linux/macOS).
3. En perfil `demo`, la aplicación inicia y Swagger abre `/swagger-ui.html`.
4. `POST /api/v1/usuarios` con JSON de `ejemplos/peticiones.http` responde **201**, devuelve ID, rol CLIENTE y no devuelve la contraseña.
5. `GET /api/v1/usuarios/{id}` con el ID creado responde **200**.
6. Una segunda creación con correo o RUT repetido responde **409**.
7. En MySQL, la tabla `usuario` existe; tras reiniciar la aplicación, el `GET` sigue encontrando al usuario. **Esto NO ocurre con el perfil demo (H2).**

## Evidencia disponible y pendiente

- Código de entidad, repositorio, servicio, controlador, documentación OpenAPI y pruebas presentes en el ZIP.
- Validación estática realizada: `pom.xml` se analizó como XML, `compose.yml` como YAML y el ZIP superó prueba de integridad.
- **Validación ejecutada parcialmente**: con `javac` / `java` (JDK 21) se compiló `RutChileno.java` y se verificó que aceptara `12.345.678-5` y rechazara `12.345.678-9`.
- Las versiones de Maven 3.10.0, Spring Boot 4.1.1 y springdoc 3.1.0 se contrastaron con documentación pública oficial (sin compilar la app completa).
- **PENDIENTE:** ejecución real de Maven y de los tests, dependencias inaccesibles desde el entorno de creación del ZIP.
- **PENDIENTE:** conexión real a MySQL del laboratorio y prueba HTTP desde el computador del estudiante.

## Seguridad y deuda conocida

No está disponible autenticación JWT. El endpoint GET público solo devuelve datos no confidenciales del perfil, pero aun así no está listo para producción. Antes de exponerlo públicamente hay que implementar autenticación/autorización y políticas contra abuso. Aún no existe un CRUD completo ni integración con React.
