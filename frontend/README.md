# Frontend

La raíz del repositorio conserva la tienda HTML heredada y los archivos ZIP históricos.

**No hay fuente React descomprimida aquí todavía.** `Huerto_React.zip` en la raíz es un artefacto histórico, pendiente de:
1. extraer de manera reproducible y revisar dependencias;
2. escoger base React canónica con el equipo;
3. crear aquí `frontend/tienda/` con su `package.json`, sin sobrescribir el HTML anterior;
4. integrar el contrato `GET /api/v1/productos` mediante URL de backend configurable.

No replicar un segundo catálogo de productos en el frontend. Las imágenes son recursos de UI; el precio y stock se leerán de API.
