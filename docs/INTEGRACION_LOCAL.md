# Integración local — dos servicios y MySQL

## Perfil de demostración (sin Docker)

Con JDK 21+ y Maven:

```bash
mvn -B -ntp -f backend/catalogo/pom.xml test
mvn -B -ntp -f backend/usuarios/pom.xml test

mvn -f backend/catalogo/pom.xml spring-boot:run -Dspring-boot.run.profiles=demo
# En otra terminal:
JWT_SECRET_BASE64="$(openssl rand -base64 32)" SERVER_PORT=8081 mvn -f backend/usuarios/pom.xml spring-boot:run -Dspring-boot.run.profiles=demo
```

En PowerShell usar `$env:SERVER_PORT = '8081'` y citar `"-Dspring-boot.run.profiles=demo"`.

`curl http://localhost:8080/api/v1/productos` debe listar tres productos. Los datos H2 son volátiles: **no muestran persistencia real**.

## MySQL real para desarrollo local

1. Copiar `.env.example` a `.env`, escoger contraseñas **solo locales** y no subir `.env`.
2. Ejecutar `docker compose up -d mysql` desde la raíz.
3. Comprobar `docker compose ps`.
4. Iniciar catálogo y usuarios en terminales separadas, exportando `DB_USER=huerto_app` y `DB_PASSWORD` con el valor del `.env`.
5. El catálogo usa `jdbc:mysql://localhost:3306/huertohogar_catalogo`; Usuarios usa `jdbc:mysql://localhost:3306/huertohogar_usuarios`.
6. Fijar `SERVER_PORT=8081` para Usuarios para evitar conflicto con catálogo en puerto 8080.

La creación de dos bases MySQL y permisos mínimos de laboratorio ocurre en `infra/mysql/01-create-catalog.sql` **únicamente en la primera inicialización del volumen Docker**. Si el volumen ya existía, no se vuelven a ejecutar scripts de inicialización: aplicar migración manual o iniciar un volumen nuevo de desarrollo. **No usar `docker compose down -v` con datos importantes**, borra datos locales.

Los dos servicios son procesos Spring Boot independientes. La base MySQL es un único servidor con dos esquemas, no una API pública.

## Reglas

- Nunca exponer MySQL directamente a Internet; el puerto está vinculado a 127.0.0.1 en desarrollo.
- No subir contraseñas reales, claves Firebase, JSON de cuentas de servicio ni archivos `.env`.
- JWT de Usuarios está implementado y probado mediante MockMvc, pero no se ha realizado una auditoría de producción. Para ejecutar ese servicio se exige `JWT_SECRET_BASE64` (32+ bytes aleatorios codificados como Base64); generarlo de forma local con `openssl rand -base64 32`, exportarlo como variable y no subirlo a GitHub. Aún faltan rate limiting y revisión de seguridad para exponerlo públicamente.
- El código de Boletas sigue en Drive; hay que auditar el ZIP antes de incorporar una copia de código a este repositorio.
- Los servicios de escritura, pagos, entregas y stock **no están habilitados** en este incremento.

## Fuente del módulo Usuarios

Archivo recibido en carpeta de equipo: `HuertoHogar-Usuarios.zip` (10/11/2026), SHA256 `0ff69a6c7155dfccda79ef14208a604c8be694dab06fdeaf1b9e8fbc300bdd04`.

La importación de Usuarios se ejecutó una sola vez, verificando el SHA-256 y copiando únicamente código fuente, tests y documentación (sin `target/` ni `.env`). El ZIP de transporte fue retirado del árbol después de crear el commit. CI verificó inicialmente 6 pruebas de Usuarios sin fallos; después, con autenticación JWT, 11 tests de Usuarios también pasaron, pero **no** comprueba MySQL real ni despliegue público.
