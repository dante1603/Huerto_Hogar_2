# HuertoHogar × Galaxym — Catálogo API (primer incremento)

Microservicio **Spring Boot 4.1.1 / Java 17+** y API REST **GET de productos**.
Este directorio es independiente del frontend HTML/React y del microservicio `Usuarios` que ya existe en los archivos del equipo.

## Qué funciona en este incremento

- `GET /api/v1/productos`: lista productos desde la base de datos.
- `GET /api/v1/productos/{id}`: devuelve un producto o `404`.
- Entidad `Producto` + repositorio Spring Data JPA.
- MySQL como base relacional en configuración normal; H2 efímero **solo** en perfil `demo`.
- Tres productos de ejemplo en el perfil `demo`, **no** importados a MySQL automáticamente.
- Swagger UI en `/swagger-ui.html`; especificación OpenAPI en `/v3/api-docs`.
- Tests HTTP con MockMvc + persistencia H2.

**No incluye aún:** CRUD administrativo, autenticación JWT, sincronización de cofres de Galaxym, compras, reservas de stock, pagos o tracking. No exponer operaciones de escritura sin seguridad.

## Ejecutar localmente

Requisitos: JDK 17+ y Maven 3.9+.

```bash
cd backend/catalogo
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

En PowerShell, si Maven interpreta el argumento incorrectamente, usar `mvn spring-boot:run "-Dspring-boot.run.profiles=demo"`.

Abrir:
- http://localhost:8080/api/v1/productos
- http://localhost:8080/swagger-ui.html

Probar:

```bash
mvn test
```

## Base de datos real (MySQL)

El servicio acepta variables de entorno `DB_URL`, `DB_USER`, `DB_PASSWORD`; por ejemplo:

```text
DB_URL=jdbc:mysql://localhost:3306/huertohogar_catalogo
DB_USER=huerto_app
DB_PASSWORD=<secreto local>
```

Crear la base y usuario MySQL antes de arrancar sin el perfil `demo`. Nunca publicar contraseñas en GitHub. El perfil normal usa `ddl-auto=update` **solo para desarrollo**; definir migraciones antes de producción.

## Cómo se conectará a Galaxym

React y Galaxym actuarán como clientes de esta API HTTP; **no tendrán acceso directo a MySQL**. La API futura de pedidos/entregas necesitará JWT, autorización por rol, consistencia de inventario y estados controlados por backend.

No se incluye todavía ningún cambio en el repositorio `Galaxym_solar_system`, por seguridad de integración.

## Despliegue (pendiente)

Este proyecto es desplegable en Cloud Run por su puerto configurable `PORT`, pero **aún no se ha desplegado**. La siguiente ronda validará tests, presupuesto y conexión MySQL/Cloud SQL antes de crear infraestructura o facturación. El perfil `demo` almacena información solo en memoria, por lo que NO sirve como persistencia real de pedidos o stock.
