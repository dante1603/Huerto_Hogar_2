# Modelos 3D de Huerto Estelar — fuente procedural

En esta carpeta están los **seis modelos originales**, recuperados del archivo versionado `docs/prototipos/huerto-estelar/galaxym_mercado_lowpoly.html`. Se conservan sus geometrías y paletas; todavía están **provisionales y no aprobados**.

| Archivo | Modelo |
|---|---|
| `zorbulbo.js` | Zorbulbo |
| `lunahoja.js` | Lunahoja |
| `crisollo.js` | Crisollo |
| `solabaya.js` | Solabaya |
| `nebupoma.js` | Nebupoma |
| `miramelon.js` | Miramelón |

## Responsabilidades
- Cada archivo exporta una fábrica `createNombre()` sin renderizador, DOM, React ni llamadas a API. Conserva la geometría procedural original: métodos de `Sculpt` para bulbos, hojas, tubos, cristales, bayas y semillas.
- `../core/sculpt.js`: utilidades matemáticas y constructor geométrico reutilizado por las seis fábricas. Devuelve buffers de vértices, normales y colores; **no es Three.js**.
- `../core/modelRenderer.js`: reutiliza el shader original WebGL 1 y posee una alternativa de Canvas 2D, además de limpieza de buffers al desmontar.
- `../ModelCanvas.jsx`: componente que construye una sola escena/canvas interactivo, carga módulos con `import()` y libera recursos al cambiar de modelo.
- `../modelRegistry.js`: mantiene el inventario de las seis muestras y el contrato explícito `SKU → modelo`. Actualmente está **vacío**, porque los SKU reales de Spring Boot no corresponden a estas seis especies. Nunca inferir vinculación por nombre parecido.
- `../PrototypeShowcase.jsx`: galería exploratoria con rótulo de **muestra no vendible**; no genera compras ni modifica stock o precios.

## Reemplazo futuro
Una vez aprobado el arte, se puede reemplazar cada fábrica procedural por una implementación mejor, o migrar a GLB/GLTF con un adaptador de carga. Conservar el ID del modelo y la interfaz del visor/registro, sin cambiar el backend comercial. Si se aprueba un archivo `.glb`, su ubicación propuesta es `public/models3d/`.

## Verificación
`npm test` construye las seis geometrías y comprueba la integridad numérica de sus atributos; `npm run build` verifica los imports dinámicos. CI **no** sustituye la aprobación visual, las pruebas en WebGL real, mobile ni la revisión de performance.
