# React / estado de fuentes

## Decisión 12/11/2026

La raíz del repositorio mantiene la tienda HTML original y los ZIP previos intactos.

En Drive personal se encontró `Huerto_Hogar_react.zip` (28/09/2026), que contiene React + Vite pero sólo un esqueleto con componentes sin implementar e imports ausentes. **No es una entrega React completa**. Hay otro ZIP `Huerto_React.zip` en la raíz de GitHub (05/10/2026), y un informe de revisión del 14/10/2026 describe una entrega corregida adicional; no se han recuperado esos bytes íntegros para migrarlos y por eso no se afirma haberlos fusionado.

Se añade `frontend/tienda/` con React Router y un recorrido funcional de catálogo, carrito local y autenticación real, preparado como nuevo frontend editable y verificable. Se preservan todos los artefactos históricos para posterior comparación.

Para ejecución y limitaciones ver [frontend/tienda/README.md](tienda/README.md).

## Estado de API

`GET /api/v1/productos`: la única fuente de datos de catálogo.
`POST /api/v1/usuarios`, `POST /api/v1/auth/login`: servicio Usuarios.

La app sólo presenta compra como futura: carrito en `localStorage` no reserva stock ni crea pedidos.
