# Verificacion HTTP interprocesos — Fullstack EP3

Prueba de integracion en `scripts/e2e_http_fullstack.py`, vinculada al workflow `.github/workflows/catalogo-ci.yml`.

A diferencia de los tests con mocks, inicia **cinco procesos Spring Boot** distintos:
Productos (8080), Usuarios (8081), Pedidos (8082), Boleta (8083), Detalle Boleta (8084). Cada uno utiliza su H2 en memoria y perfiles `demo`. El cliente de prueba consume HTTP real y JWT emitido por Usuarios; Pedidos llama a Productos, Boleta llama a Pedidos, y Detalle a Boleta y Pedidos.

Cubre: registro/autenticacion de dos clientes, autorizacion transversal, compra con reserva de stock, idempotencia de Pedidos y Boleta/Detalle, consistencia de lineas e importes, lectura y aislamiento por propietario, y verificacion final de inventario. Todos los procesos se terminan tras la prueba (incluso en fallo). Un secreto JWT efimero se genera en memoria, sin publicar valores.

Para reproducir en un entorno con Java 21, Maven y Python 3:

```bash
for servicio in catalogo usuarios pedidos boleta detalle-boleta; do
  mvn -B -ntp -f "backend/$servicio/pom.xml" -DskipTests package
done
python3 scripts/e2e_http_fullstack.py
```

**No prueba** MySQL real, reinicio con persistencia, despliegue, carrera/timeout distribuidos ni el flujo React. No habilita reconciliador, no cambia la ruta de pedidos en frontend ni toca Galaxym. Es un paso previo al corte MySQL/E2E navegador.

## Resultado verificado (15-11-2026 UTC)

PR [#27](https://github.com/dante1603/Huerto_Hogar_2/pull/27), SHA de pruebas `c0d169a7a6820352499c44660b862f8ca7320624`, merge de integración `9e5b992d6289226cb99838849fa2ccf48e51e0a3`.

[GitHub Actions 43668577173](https://github.com/dante1603/Huerto_Hogar_2/actions/runs/43668577173): **PASS** — 71/71 pruebas Java (Productos 30, Usuarios 14, Pedidos 12, Boleta 6, Detalle Boleta 9), cinco JAR compilados, cinco procesos `[READY]` en puertos 8080–8084 y salida `[PASS]` del contrato HTTP. Se comprobó login/JWT real, reserva Pedidos→Productos, Boleta→Pedidos y Detalle→Boleta/Pedidos, idempotencia sin stock duplicado y acceso entre dos clientes aislados.

**Nudo corregido:** el primer ensayo (run [43668279559](https://github.com/dante1603/Huerto_Hogar_2/actions/runs/43668279559)) devolvió 403 en lugar de 409 a un conflicto de idempotencia. Se permitió solo el `DispatcherType.ERROR` interno en Spring Security; el segundo run recuperó los 409/404 sin hacer públicas las rutas HTTP protegidas.

**No cubre:** MySQL real, persistencia tras reinicios, red degradada/races distribuidas, React/browser ni capturas institucionales. Reconciliador desactivado.
