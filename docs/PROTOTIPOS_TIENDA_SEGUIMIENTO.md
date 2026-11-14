# Huerto Estelar — recuperación y auditoría de prototipos (14-11-2026)

## Fuentes recuperadas

| Archivo original | Propósito | Tipo de implementación |
|---|---|---|
| `docs/prototipos/huerto-estelar/galaxym_mercado_lowpoly.html` | Mercado espacial y modelos 3D explorables | HTML autónomo con motor procedural `Sculpt` y WebGL propio; fallback Canvas 2D |
| `docs/prototipos/huerto-estelar/huerto_estelar_galaticoins.html` | Cuenta de prueba, compras, pedidos y mapa de seguimiento | HTML autónomo con 6 imágenes WebP embebidas, estado local y mapa SVG; no renderiza los productos en 3D |

No se recuperó el fichero posterior `huerto_estelar_seguimiento_galaxym.html` (referido en una conversación del 13-11-2026). Este inventario **no** sustituye ese archivo ni acredita haber inspeccionado su integración orbital. Los dos archivos anteriores son referencias conservadas íntegramente, **no código React activo** ni diseños de producto aprobados.

## Mercado low-poly: estructura observable

- Datos `PRODUCTS` con seis especies: Zorbulbo (`zorbulbo`), Lunahoja (`lunahoja`), Crisollo (`crisollo`), Solabaya (`solabaya`), Nebupoma (`nebupoma`), Miramelón (`miramelon`).
- `Sculpt`: clase de geometría procedural para triángulos, cuadriláteros, elipsoides, tubos, tallos y hojas. `makeProduct(id)` es una función monolítica con seis ramas, no seis módulos todavía.
- `MESHES`: genera una vez las mallas de los seis productos. La malla se entrega como arrays `Float32Array` de posiciones/normales/colores.
- Renderizador WebGL 1 nativo con shaders GLSL, buffers, cámara orbital e interacción mediante puntero/rueda; fallback Canvas 2D. **No usa Three.js**.
- El catálogo renderiza miniaturas precalculadas desde el canvas; el visor interactivo se abre en modal con rotación/zoom.
- Filtros y carrito son JS locales; `exportOrder` genera un JSON `DRAFT` para descarga y declara `deliveryMissionPreview.status=NOT_CREATED`. Sin `fetch`, backend ni pedido real.

## Huerto Estelar con GalatiCoins: estructura observable

- Tres pantallas principales: `shopScreen` (tienda), `ordersScreen` (historial) y `trackingScreen` (detalle y seguimiento). `authView` es la portada de acceso ficticio.
- Usa los mismos seis IDs y WebP codificados en base64 en `photos`; **los precios difieren del mercado low-poly**. No replicar precios ni cartera en BD.
- `state` y `save` persisten usuario ficticio, saldo de prueba (inicial 10 000), cesta, destinos y pedidos en `localStorage` mediante `huerto-estelar-galati-v2`.
- Login sin API y con autofill de credenciales demo; NO trasladar como autenticación real.
- `placeOrder` descuenta monedas ficticias y crea un pedido únicamente en el navegador; NO trasladarlo en lugar de `POST /api/v1/pedidos`.
- Siete etapas visuales: confirmado, preparación, carga, despegue, en ruta, aproximación y entregado. `stepForward` permite cambio manual o cada 1,7 s; NO son eventos verificados.
- `drawMap` posiciona el marcador sobre una ruta Bézier SVG a coordenadas de pantalla fijas para Bruma/Árido/Yermo; NO recibe ni transforma coordenadas orbitales del juego.
- La maqueta incorpora detalle textual de seguimiento específico para Yermo (sin atmósfera); el texto y el esquema UX son referencias aprovechables.

## Realidad del monorepo integrado, frente al prototipo

- `GET /api/v1/productos`: datos de Catálogo Spring Boot, incluidos ID numérico, SKU, precio y stock autorizados por BD.
- `POST /api/v1/pedidos`: reserva transaccional autenticada CLIENTE con idempotencia.
- `GET /api/v1/pedidos/mios`, `/pendientes` y `/{id}`: lecturas reales; `PedidoResponse.estado` es por ahora exclusivamente `PENDIENTE`.
- No hay servicios de entrega, eventos de progreso, telemetría de nave, saldo de GalatiCoins, cobros ni tracking real. `PedidoResponse` tiene `planetaDestino` pero **no** sector de dirección `zone` del prototipo.
- Los SKU de ejemplo de BD no se corresponden directamente con los IDs de las seis especies. La asociación SKU → modelo requiere un mapa explícito revisado; nunca derivarla automáticamente de nombre/precio.

## Decisión de integración por fases

**Corte visual siguiente:** adaptar la vista de tienda/consulta/historial del HTML a React, manteniendo datos reales. Separar `Sculpt` y los seis generadores en `frontend/tienda/src/3d/models/` como piezas procedurales individuales, con visor compartido, adaptador WebGL o Three.js probado y fallback no WebGL. Marcar cada modelo `provisional` hasta la aprobación visual del usuario. No cambiar datos de API ni productos desde el prototipo.

**Tracking UI inicial:** aprovechar `trackingScreen`, cronología y estilo del mapa como vista de consulta de pedido. Si el único estado real es `PENDIENTE`, mostrar **reserva confirmada / entrega aún no iniciada**. Puede existir una ilustración de ruta claramente rotulada `vista previa`, pero no debe insinuar posición, estado o tiempo de llegada real. No copiar botón de `stepForward` ni reproducción automática a la tienda conectada.

**Tracking real posterior:** añadir al dominio de entregas una identidad de despacho, asignación de repartidor/nave, estado autorizado, eventos con marca temporal/versionado y última telemetría válida en marco solar; reconciliar el marco de coordenadas y tiempo orbital con el visor canónico de Galaxym. La API deberá recibir datos autenticados desde el juego o su puente, no desde la página del cliente. Lectura autorizada por propiedad del pedido/roles. Interfaz con `SIN_SEÑAL` o `AUN_NO_ASIGNADO` cuando corresponda, nunca una nave inventada. La validación de entrega es aparte y debe exigir evidencia/confirmación real.

## Estado de validación

- Inspección estática de ambos HTML originales y contratos REST/estado backend de la rama `integration/fullstack-2026-11-14`.
- No se acreditan pruebas visuales en navegador, rendimiento móvil, integración WebGL/React ni telemetría del juego.
- El HTML posterior con seguimiento del visor solar de Galaxym aún no está disponible. No reconstruir su implementación afirmando continuidad.
