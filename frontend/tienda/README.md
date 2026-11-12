# Tienda React — HuertoHogar × Galaxym

Fuente: se revisó el archivo `Huerto_Hogar_react.zip` del Drive personal (modificado el 28-09-2026). Esta fuente contiene un esqueleto React/Vite incompleto (componentes y rutas no desarrollados, imports ausentes). **No se reemplazaron** los archivos ZIP ni los HTML históricos: esta carpeta es una implementación mantenible nueva inspirada en su estructura React Router/Vite.

## Estado del incremento

- React + Vite, rutas Inicio, Productos, Nosotros, Carrito, Acceso.
- **Catálogo real** desde `GET /api/v1/productos`, sin lista de productos simulada en el frontend.
- Filtro por planeta, detalle expandible, loading/error/reintento, stock reportado por API.
- Carrito local persistente; no se genera una compra real y el botón de checkout está desactivado.
- Registro real en Usuarios `POST /api/v1/usuarios` e inicio de sesión `POST /api/v1/auth/login`, JWT en `sessionStorage` de la pestaña con vencimiento.
- Diseño responsive de interfaz galáctica, tonos crema/verde salvia.
- Tests unitarios de carrito con Node.js.

## Desarrollo

Requiere Node >=20.19 (recomendado Node 22) y npm. En dos terminales levanta los servicios con perfiles H2 de demo:

```bash
# Terminal 1 (raíz)
mvn -f backend/catalogo/pom.xml spring-boot:run -Dspring-boot.run.profiles=demo

# Terminal 2 (raíz)
JWT_SECRET_BASE64="$(openssl rand -base64 32)" SERVER_PORT=8081 \
  mvn -f backend/usuarios/pom.xml spring-boot:run -Dspring-boot.run.profiles=demo

# Terminal 3
cd frontend/tienda
npm install
npm run dev
```

Abrir el URL que anuncia Vite. Vite reenvía `/api/v1/productos` al servicio Catálogo en 8080 y `/usuarios-api/*` al servicio Usuarios en 8081. No requiere CORS de desarrollo mediante este proxy.

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

Por ahora Catálogo no permite CORS externo: necesitará configurar lista permitida de orígenes o publicar rutas mediante proxy seguro del hosting. Usuarios ya admite `APP_ALLOWED_ORIGINS` delimitado por comas. Nunca exponer el backend de Usuarios con datos reales sin controles adicionales contra abuso.

**No implementado:** pedidos, compra, stock sincronizado, seguimiento orbital, panel CRUD administrativo ni integración de misiones Galaxym. No hay despliegue ni URL pública de esta tienda.
