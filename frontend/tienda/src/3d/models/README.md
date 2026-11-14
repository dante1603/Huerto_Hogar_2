# Modelos 3D de productos — fuente procedural

Esta carpeta es el **lugar reservado** para recuperar el código Three.js de las frutas y verduras del prototipo HTML espacial. **Aún no contiene modelos**: primero hay que localizar y revisar ese HTML; no sustituirlo por geometría inventada.

## Convención cuando se recuperen las fuentes

- Un archivo JavaScript por modelo procedural, con nombre concreto y estable de la fruta o verdura (por ejemplo, `frutaDeOasis.js`).
- Cada archivo exporta una función de creación de objetos/geométricas Three.js. No monta un renderer, no accede a React ni hace llamadas a la API.
- Un futuro registro `src/3d/productModelRegistry.js` asociará el SKU de Catálogo al modelo, sin usar el nombre visible como clave de base de datos.
- Marcar los modelos del prototipo como **provisionales / no aprobados** hasta la validación visual. Su reemplazo posterior podrá ser otro procedimiento o un GLB/GLTF, sin cambiar el pedido, el carrito ni la base de datos.
- La escena/visor (cámara, controles, render y liberación de recursos) irá fuera de esta carpeta. Los archivos de modelo solo describirán la geometría/materiales.
- Los archivos binarios GLB/GLTF aprobados, cuando existan, podrán vivir en `public/models3d/` para cargarse bajo demanda.

Este corte no incorpora Three.js ni modifica la apariencia, los modelos originales o el flujo comercial. El HTML de referencia permanece fuera del repositorio hasta confirmar su fuente.
