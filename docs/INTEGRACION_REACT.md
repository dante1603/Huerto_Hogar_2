# Integración React ↔ APIs — 12/11/2026

## Evidencia de procedencia

- Google Drive personal contiene `Huerto_Hogar_react.zip` (modificado 28/09/2026). Es un proyecto Vite/React con esqueleto no funcional; `App.jsx` hace referencia a páginas sin importarlas y varios componentes solo contienen placeholders.
- Repositorio mantiene `Huerto_React.zip` (commit 05/10/2026). No se reemplaza ni elimina.
- Informe de trabajo del 14/10/2026 menciona `Huerto_React_corregido_entrega.zip`; ese ZIP no pudo localizarse en los archivos accesibles de Drive/Biblioteca durante esta revisión.
- `frontend/tienda/` es una **implementación nueva sobre el patrón React/Vite del Drive**, no una afirmación de migración exacta de la versión corregida.

## Contratos usados

1. `GET /api/v1/productos` (Catálogo, puerto 8080).
2. `POST /api/v1/usuarios` (Usuarios, puerto 8081).
3. `POST /api/v1/auth/login` (Usuarios, puerto 8081) devuelve `accessToken`, `expiresInSeconds`, `idUsuario` y `rolUsuario`.

## Fronteras

- Carrito en localStorage; no descontar stock ni generar órdenes sin servicio de pedidos.
- JWT en sessionStorage, caducidad respetada. No incorporar tokens privados en el build.
- Dev Vite proxy elimina CORS cruzados para localhost; en hosting real deben configurarse URLs y CORS o rewrites de hosting.
- API fuera de servicio debe mostrar error y reintento; no sustituir por productos falsos.
- Ninguna actualización en Galaxym hasta estabilizar compras y autenticación.
