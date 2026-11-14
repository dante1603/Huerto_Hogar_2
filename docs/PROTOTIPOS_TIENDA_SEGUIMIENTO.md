# Huerto Estelar — recuperación y auditoría de prototipos (14-11-2026)

## Fuentes recuperadas

| Archivo original | Propósito | Tipo de implementación |
|---|---|---|
| `docs/prototipos/huerto-estelar/galaxym_mercado_lowpoly.html` | Mercado espacial y modelos 3D explorables | HTML autónomo con motor procedural `Sculpt` y WebGL propio; fallback Canvas 2D |
| `docs/prototipos/huerto-estelar/huerto_estelar_galaticoins.html` | Cuenta de prueba, compras, pedidos y mapa de seguimiento | HTML autónomo con 6 imágenes WebP embebidas, estado local y mapa SVG; no renderiza los productos en 3D |
| `docs/prototipos/huerto-estelar/huerto_estelar_seguimiento_galaxym.html` | Evolución del mapa del cliente al sistema orbital Galaxym | Canvas 2D con vista compacta/métrica y parámetros orbitales copiados; admite inyección de telemetría por una API local `window`, pero por defecto simula la ruta |
| `docs/prototipos/huerto-estelar/galaxym_terminal_repartidor.html` | Terminal diegética del repartidor | HTML autónomo, mapa Canvas, misiones y transiciones demo guardadas en `localStorage`; sin backend ni runtime de Galaxym |

Se recuperaron las cuatro fuentes nombradas arriba, incluidas la versión orbital posterior y la terminal del repartidor del 13-11-2026. Son **referencias de diseño y código aisladas del runtime**, no código React activo ni diseños de producto aprobados. La etiqueta `Galaxym` y las constantes orbitales embebidas no acreditan fidelidad con el HEAD actual del repositorio del juego.

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

## Evolución orbital: seguimiento Galaxym

- Mantiene la misma sesión ficticia, seis productos WebP, carrito, compras y siete etapas simuladas del HTML anterior.
- Sustituye el mapa SVG por Canvas 2D interactivo `galaxyOrbitMap`, con `Carta estelar` (compacta) / `Escala física` (métrica), rotación, zoom y centrar nave.
- `GALAXYM_RECIPE` codifica la estrella, gigante, Bruma, Árido, Yermo y anillo. `sampleGalaxym(timeSeconds)` evalúa posiciones/velocidades orbitales con una adaptación local de `authored-orbits.json`, `circular-orbit.ts` y `system-snapshot.ts` citados por el propio HTML. Es una **copia de parámetros**, que necesita contrastarse con el proyecto Galaxym vigente.
- `sourceStation` sitúa una estación ficticia sobre el plano del anillo; `routeSample` produce una curva Bézier en metros solares, no una trayectoria física del vehículo. El modelo de destino usa un punto de llegada ficticio anclado al cuerpo.
- `window.GalaxymDelivery` expone `getRecipe`, `sampleSystem`, `getState`, `setTelemetry(payload)` y `clearTelemetry`. La telemetría inyectada exige `sourceId`, tiempo, `orderId` coincidente y XYZ numéricos, pero **carece de autenticación, frescura, persistencia, validación de marcos y puente API**. Cualquier script en la página podría inyectarla: nunca confiar en ella para producción.
- Sin inyección, el reloj y la nave avanzan como simulación. Incluso los rótulos de coordenadas solares reales de los cuerpos no convierten la posición simulada de la nave en una posición verificada.

## Contraste de parámetros con el repositorio de Galaxym

- En `integration/solar-system-unified-2026-11-11`, `src/simulation/system/authored-orbits.json` usa los mismos desplazamientos orbitales, ejes, períodos y radios incluidos en la receta embebida del HTML orbital (la fuente que el propio HTML declara).
- En el **`main` consultado el 14-11-2026**, los ejes de rotación propios (`spin.axis`) de tres cuerpos son distintos: Yermo `[0,1,0]`, Bruma `[0.12,1,0.08]`, Árido `[0,1,0]`, mientras que el prototipo usa `[0,3300,2400]` en los tres. La ubicación del punto de aterrizaje calculado mediante `spinLocalToSolar` puede divergir aunque las órbitas posicionales sean idénticas.
- Por ello **no** copiar `GALAXYM_RECIPE` como dato fijo de producción; consumir un manifiesto orbital versionado generado por el canon del juego y asociar el reloj y el marco espacial a cada muestra. El mapa conceptual y sus controles siguen siendo reutilizables.

## Terminal diegética del repartidor

- Vistas de cola, encargo, manifiesto de carga, mapa orbital y salida JSON demo, con constantes orbitales similares a la tienda.
- Estados locales de misión `new → accepted → loaded → enroute → near → delivered`; un flujo de prueba de confirmación por código. No recibe pedidos reales, no envía actualizaciones al backend y no registra entregas autoritativas.
- `shipPosition` interpola una ruta simulada entre cuerpos y `telemetry` actualiza etiquetas locales de coordenadas, no publica datos remotos.
- Conservar la UX y las transiciones candidatas; **no** reutilizar los códigos demo, datos de cliente o controles locales como seguridad/autorización.

## Realidad del monorepo integrado, frente al prototipo

- `GET /api/v1/productos`: datos de Catálogo Spring Boot, incluidos ID numérico, SKU, precio y stock autorizados por BD.
- `POST /api/v1/pedidos`: reserva transaccional autenticada CLIENTE con idempotencia.
- `GET /api/v1/pedidos/mios`, `/pendientes` y `/{id}`: lecturas reales; `PedidoResponse.estado` es por ahora exclusivamente `PENDIENTE`.
- No hay servicios de entrega, eventos de progreso, telemetría de nave, saldo de GalatiCoins, cobros ni tracking real. `PedidoResponse` tiene `planetaDestino` pero **no** sector de dirección `zone` del prototipo.
- Los SKU de ejemplo de BD no se corresponden directamente con los IDs de las seis especies. La asociación SKU → modelo requiere un mapa explícito revisado; nunca derivarla automáticamente de nombre/precio.

## Decisión de integración por fases

**Corte visual siguiente:** adaptar la vista de tienda/consulta/historial del HTML a React, manteniendo datos reales. Separar `Sculpt` y los seis generadores en `frontend/tienda/src/3d/models/` como piezas procedurales individuales, con visor compartido, adaptador WebGL o Three.js probado y fallback no WebGL. Marcar cada modelo `provisional` hasta la aprobación visual del usuario. No cambiar datos de API ni productos desde el prototipo.

**Tracking UI inicial:** aprovechar `trackingScreen`, la cronología y el visor Canvas orbital más reciente como vista de consulta de pedido. Si el único estado real es `PENDIENTE`, mostrar **reserva confirmada / entrega aún no iniciada**. Se puede mostrar la carta de planetas/órbitas con el reloj de simulación rotulado explícitamente como `vista previa`; **ocultar la nave en movimiento** mientras no haya telemetría válida. No copiar `stepForward` ni reproducción automática de estado a la tienda conectada.

**Tracking real posterior:** añadir al dominio de entregas una identidad de despacho, asignación de repartidor/nave, estado autorizado, eventos con marca temporal/versionado y última telemetría válida en marco solar; reconciliar el marco de coordenadas y tiempo orbital con el visor canónico de Galaxym. La API deberá recibir datos autenticados desde el juego o su puente, no desde la página del cliente, y filtrar por propietario del pedido/roles. Interfaz con `SIN_SEÑAL` o `AUN_NO_ASIGNADO` cuando corresponda, nunca una nave inventada. Conectar la terminal diegética como **emisor autenticado** de eventos e información de nave, y la tienda como **consumidor de solo lectura**. Un código de entrega de un solo uso debe validarse en servidor, no en JavaScript público.

**Contrato mínimo sugerido, NO implementado:** `pedidoId`, `entregaId`, `estadoEntrega`, `naveId`, `repartidorId`, `posicionSolarMetros` (X/Y/Z), `frameId`, `simTimeSeconds`, `observadoEn` UTC, `version` / secuencia y `sourceId`. Nunca equiparar `etapa` o `% del viaje` con la posición real; estado logístico y telemetría son fuentes distintas.

## Estado de validación

- Inspección estática de ambos HTML originales y contratos REST/estado backend de la rama `integration/fullstack-2026-11-14`.
- No se acreditan pruebas visuales en navegador, rendimiento móvil, integración WebGL/React ni telemetría del juego.
- El HTML posterior con seguimiento orbital y la terminal del repartidor **sí fueron inspeccionados**. Su simulación orbital y su telemetría local son verificables en el código del prototipo, pero falta validación contra repo/runtime actual de Galaxym y pruebas E2E.
