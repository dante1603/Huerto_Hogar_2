# Tienda React — HuertoHogar × Galaxym

Fuente: se revisó el archivo `Huerto_Hogar_react.zip` del Drive personal (modificado el 28-09-2026). Esta fuente contiene un esqueleto React/Vite incompleto (componentes y rutas no desarrollados, imports ausentes). **No se reemplazaron** los archivos ZIP ni los HTML históricos: esta carpeta es una implementación mantenible nueva inspirada en su estructura React Router/Vite.

## Estado del incremento

- React + Vite, rutas Inicio, Productos, Nosotros, Carrito, Acceso y Mis Pedidos.
- **Catálogo real** desde `GET /api/v1/productos`, sin lista de productos simulada en el frontend.
- Filtro por planeta, detalle expandible, loading/error/reintento, stock reportado por API.
- Carrito local persistente y **confirmación de pedido** vía API: destino Bruma/Árido/Yermo; idempotencia por intento; comprobante de reserva PENDIENTE.
- Registro real en Usuarios `POST /api/v1/usuarios` e inicio de sesión `POST /api/v1/auth/login`, JWT en `sessionStorage` de la pestaña con vencimiento.
- Diseño responsive de interfaz galáctica, tonos crema/verde salvia.
- Tests unitarios de carrito con Node.js.

## Desarrollo

Requiere Node >=20.19 (recomendado Node 22) y npm. Abre procesos en terminales distintas con la **misma clave JWT de desarrollo**. En la práctica debes exportar la misma clave en cada sesión de terminal (o heredarlas desde un mismo shell):

```bash
# Generar una clave de desarrollo UNA sola vez y exportarla en ambas terminales:
export JWT_SECRET_BASE64="$(openssl rand -base64 32)"

# Terminal 1 (raíz, con la clave exportada)
mvn -f backend/catalogo/pom.xml spring-boot:run -Dspring-boot.run.profiles=demo

# Terminal 2 (raíz, con la MISMA clave exportada)
SERVER_PORT=8081 mvn -f backend/usuarios/pom.xml spring-boot:run -Dspring-boot.run.profiles=demo

# Terminal 3
cd frontend/tienda
npm install
npm run dev
```

Abrir el URL que anuncia Vite. Vite reenvía `/api/v1/productos` y `/api/v1/pedidos` al servicio Catálogo en 8080 y `/usuarios-api/*` al servicio Usuarios en 8081. No requiere CORS de desarrollo mediante este proxy.

```bash
npm run test
npm run build
```

## Producción (PENDIENTE)

En `.env.production` local o configuración del hosting, especificar **solo las URLs públicas**, nunca secretos:

```
VITE_CATALOGO_API_URL=https://<dominio-de-catalogo>
VITE_USUARIOS_API_URL=https://<dominio-de-usuarios>
```

Catálogo y Usuarios admiten `APP_ALLOWED_ORIGINS` separados por comas; en hosting real se deben configurar orígenes autorizados o rewrites seguros. Usuarios ya admite `APP_ALLOWED_ORIGINS` delimitado por comas. Nunca exponer el backend de Usuarios con datos reales sin controles adicionales contra abuso.

**Implementado en el MVP:** creación de pedido que reserva unidades en BD del Catálogo (H2 demo o MySQL configurado), listado de pedidos por cuenta y cola de pedidos pendientes para vendedor. No se cobra dinero: precios de ejemplo expresados en GalatiCoins.

**No implementado:** cancelaciones, cofre sincronizado con Galaxym, entrega, confirmación OTP, seguimiento orbital, panel CRUD administrativo ni integración de misiones Galaxym. No hay despliegue ni URL pública de esta tienda.

**Importante:** los dos backends deben compartir `JWT_SECRET_BASE64` y `JWT_ISSUER`. No generes dos secretos diferentes. El código frontend jamás debe conocer el secreto; sólo almacena el token de sesión. La confirmación de un pedido puede descontar inventario y no admite cancelación todavía; úsalo únicamente con datos de prueba.

## Organización interna del frontend — 14/11/2026

- `src/App.jsx`: composición de la aplicación; ya no implementa directamente la carga de productos, la persistencia del carrito o la sesión.
- `src/app/AppRoutes.jsx`: tabla única de rutas React Router y vinculación de páginas.
- `src/components/layout/`: encabezado y pie de página compartidos, sin cambios visuales.
- `src/hooks/useCatalogo.js`: catálogo real, loading/error, reintento y cancelación al desmontar.
- `src/hooks/useCarrito.js`: estado del carrito y persistencia de almacenamiento; las reglas puras siguen en `src/lib/cart.js`.
- `src/hooks/useSesion.js`: sesión React; las operaciones de almacenamiento JWT siguen en `src/lib/session.js`.
- `src/lib/api.js`: contratos de red existentes; no modificados en este corte.
- `src/3d/models/`: sitio reservado para modelos procedurales provisionales del prototipo espacial, **sin modelos recuperados todavía**. Ver su README para la política de nombres, SKU, reemplazo y estado de aprobación.

**Invariantes de este refactor:** se conservan exactamente las rutas, contratos REST, estilos CSS, componentes comerciales, clave del carrito, sesión, idempotencia y comportamiento de compra. No se integra todavía el HTML espacial ni se incorporan nuevos recursos 3D. La navegación según roles y el E2E siguen pendientes de un corte funcional posterior.
