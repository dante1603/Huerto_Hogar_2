# Manual de usuario — HuertoHogar EP3

**Versión 0.8, borrador operativo.** La plantilla institucional exige número, título, imagen y nota para cada captura (con borde negro). **Las capturas aún no existen**: deben obtenerse del navegador durante la validación final. No entregar este archivo como manual final sin imágenes.

Fuente de formato: Anexo 4 DSY1104 (Drive institucional), https://docs.google.com/document/d/1WmY2QFZvhzkvhwPuoJM6bsYMNPjrSZwN/edit.

## 1. Introducción

Tienda académica de productos ficticios con perfiles CLIENTE, VENDEDOR y ADMINISTRADOR, desarrollada con React + Spring Boot + MySQL. El objetivo es aprender a operar el catálogo, el carrito, las reservas y las interfaces restringidas. No procesa pagos ni despachos.

## 2. Requisitos previos y configuración

- Equipo de desarrollo con Node.js >=20.19 (recomendado 22), npm, JDK 21, Maven y Docker/Compose para MySQL.
- GitHub público: https://github.com/dante1603/Huerto_Hogar_2
- Copiar .env.example a .env y definir DB_ROOT_PASSWORD, DB_PASSWORD, DB_USER y DB_PORT. Nunca subir .env.
- En validación final: docker compose up -d mysql; comprobar healthy y esquemas antes de iniciar Spring.
- Servicios independientes: Catálogo en puerto 8080 y Usuarios en puerto 8081. Configurar el mismo JWT_SECRET_BASE64 y JWT_ISSUER en ambos; DB_URL debe apuntar al esquema correcto de cada uno.
- En la raíz, ejecutar Maven para ambos servicios. En frontend/tienda, npm install y npm run dev; abrir la URL indicada por Vite.
- **Para preparar las pantallas sin MySQL:** Spring usa perfil demo con H2 temporal; los datos se borran al detenerse. Esto NO es prueba de persistencia.
- Rutas OpenAPI: http://localhost:8080/swagger-ui.html y http://localhost:8081/swagger-ui.html cuando estén arrancados los servicios.

## 3. CLIENTE — compra sin pago real

1. En Acceso, crear cuenta con nombre, RUT, dirección, correo y contraseña; el rol será CLIENTE.
2. Iniciar sesión. Si el backend está apagado, aparecerá un error explícito.
3. Abrir Productos, filtrar por planeta, ampliar detalles y añadir productos disponibles.
4. Abrir Carrito, ajustar selección y destino. Confirmar pedido: el servicio reserva stock y devuelve un número de orden con estado PENDIENTE.
5. En Pedidos, consultar órdenes propias y abrir detalle. La operación no equivale a un pago ni a una entrega.
6. Recargar la pestaña: el access JWT se conserva y, si caduca, React intenta renovarlo con el refresh token vigente. Al cerrar sesión se limpia la pestaña y se intenta revocar el refresh en el backend. Esta política es de laboratorio, no una garantía de seguridad de producción.

## 4. VENDEDOR — consulta sin escritura

1. Entrar con una cuenta preaprovisionada como VENDEDOR. El registro público no permite seleccionar ese rol.
2. En Consulta de productos, abrir el listado y consultar el detalle real mediante el endpoint GET de cada producto.
3. En Órdenes, consultar pendientes y abrir detalle.
4. El VENDEDOR no debe encontrar carrito ni controles de crear/editar/eliminar productos. Las rutas directas prohibidas muestran acceso restringido.

## 5. ADMINISTRADOR — mantenimiento de productos

1. Iniciar sesión con una cuenta preaprovisionada como ADMINISTRADOR.
2. Abrir Administrar productos; consultar listado.
3. Completar SKU, nombre, descripción, precio no negativo, stock no negativo y planeta. Crear producto: Spring responde con código 201 y Location.
4. Seleccionar un producto y editar; guardar cambios para ejecutar PUT.
5. Solicitar eliminación; confirmar acción. El backend devuelve 204 si se eliminó o 409 si existe una orden histórica referenciada.
6. Consultar órdenes de clientes sin tener permiso para simular entregas o cambios de estado.

## 6. Mensajes y errores

- 400: campos inválidos.
- 401: credenciales incorrectas, token ausente o caducado.
- 403: rol sin autorización, o cliente intentando consultar una orden ajena.
- 404: recurso ya inexistente.
- 409: SKU duplicado, stock insuficiente, conflicto de idempotencia o intento de eliminar producto con pedidos.
- Sin conexión: comprobar estado de los servicios; la UI no debe sustituir respuestas con contenido ficticio.

## 7. Registro de capturas institucionales pendientes

| Nº | Título de la captura | Qué debe demostrar | Estado |
|---|---|---|---|
| 1 | Inicio y catálogo real | React con GET /api/v1/productos | PENDIENTE |
| 2 | Registro CLIENTE | Validaciones del formulario, resultado real | PENDIENTE |
| 3 | Inicio de sesión | Rol CLIENTE y menú correspondiente | PENDIENTE |
| 4 | Carrito y pedido | Total y respuesta POST de pedido | PENDIENTE |
| 5 | Mis pedidos | Consulta propia y detalle | PENDIENTE |
| 6 | Vista VENDEDOR | Menú restringido, lista y detalle | PENDIENTE |
| 7 | Vista ADMINISTRADOR | Formulario CRUD con resultado real | PENDIENTE |
| 8 | Protección de acceso | Ruta denegada por rol/HTTP 403 | PENDIENTE |
| 9 | Swagger productos | GET/POST/PUT/DELETE visibles | PENDIENTE |
| 10 | Persistencia MySQL | Filas antes/después de reiniciar Spring | PENDIENTE |

**Formato exigido por cada figura:** número, título, captura con borde negro de 1px y nota descriptiva. No se han creado imágenes sintéticas en sustitución de evidencias.
