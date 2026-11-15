# Verificacion HTTP interprocesos — Fullstack EP3

Prueba de integracion en \`scripts/e2e_http_fullstack.py\`, vinculada al workflow \`.github/workflows/catalogo-ci.yml\`.

A diferencia de los tests con mocks, inicia **cinco procesos Spring Boot** distintos:
Productos (8080), Usuarios (8081), Pedidos (8082), Boleta (8083), Detalle Boleta (8084). Cada uno utiliza su H2 en memoria y perfiles \`demo\`. El cliente de prueba consume HTTP real y JWT emitido por Usuarios; Pedidos llama a Productos, Boleta llama a Pedidos, y Detalle a Boleta y Pedidos.

Cubre: registro/autenticacion de dos clientes, autorizacion transversal, compra con reserva de stock, idempotencia de Pedidos y Boleta/Detalle, consistencia de lineas e importes, lectura y aislamiento por propietario, y verificacion final de inventario. Todos los procesos se terminan tras la prueba (incluso en fallo). Un secreto JWT efimero se genera en memoria, sin publicar valores.

Para reproducir en un entorno con Java 21, Maven y Python 3:

\`\`\`bash
for servicio in catalogo usuarios pedidos boleta detalle-boleta; do
  mvn -B -ntp -f "backend/$servicio/pom.xml" -DskipTests package
done
python3 scripts/e2e_http_fullstack.py
\`\`\`

**No prueba** MySQL real, reinicio con persistencia, despliegue, carrera/timeout distribuidos ni el flujo React. No habilita reconciliador, no cambia la ruta de pedidos en frontend ni toca Galaxym. Es un paso previo al corte MySQL/E2E navegador.
