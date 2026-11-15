
# Etapa EP3 — construir primero y probar MySQL al final

**Decisión actual:** completar los indicadores técnicos y los documentos exigidos usando contratos reales y pruebas automatizadas unitarias/H2. Reservar la conexión MySQL auténtica, la persistencia y el recorrido E2E del navegador para el corte final. H2 NO acredita MySQL y no se fabricarán capturas.

## Orden de implementación

1. IE3.2.1 (8 %): CRUD seguro de Productos + OpenAPI. Integrado PR #12; Swagger visual pendiente.
2. IE3.3.3 (6 %): guardas CLIENTE/VENDEDOR/ADMINISTRADOR, panel CRUD y detalles REST **implementados en PR #13**; pruebas Node y build aprobadas, navegador pendiente.
3. IE3.3.1 + IE3.3.2 (12 %): JWT firmado, controles de acceso, renovación de tokens de un solo uso, revocación y caducidad **implementados y comprobados con CI H2/Node** en PR #14/#16/#17. Aún falta provisión controlada de cuentas VENDEDOR/ADMINISTRADOR, pruebas en navegador y MySQL real.
4. IE3.2.2 (6 %): revisar flujos API/frontend, asegurar coherencia de errores y evidencia funcional.
5. IE3.1.1 (8 %): consolidar modelo relacional, scripts versionados e instrucciones de restauración; ejecución MySQL pospuesta.
6. Documentación: ERS terminada con alcance validado, manual con pantallazos reales, listado de APIs, informe de integración, enlaces GitHub y ZIP finales.

## Invariantes y criterio

- Nadie escribe datos directamente desde el frontend.
- No hay credenciales embebidas ni cuentas privilegiadas mediante registro público.
- El vendedor solo consulta; el administrador gestiona; el cliente compra y consulta lo suyo.
- No se simulan respuestas exitosas ni se crean datos comerciales falsos para ocultar desconexión.
- Tests automatizados pueden pasar antes de iniciar MySQL real; no reemplazan la prueba final.
- Galaxym orbital y la presentación individual quedan fuera del alcance actual.
- main no se modifica hasta que la entrega cumpla los criterios.

## Prueba final de MySQL, aún no realizada

1. Iniciar MySQL 8.4 mediante compose.yaml y .env local seguro.
2. Levantar Catálogo y Usuarios con el mismo JWT_SECRET_BASE64 y sus respectivos esquemas.
3. Crear, consultar, editar y borrar productos desde interfaz ADMIN; registrar evidencias Swagger.
4. Registrar cliente, iniciar sesión, reservar stock, consultar pedidos propios, visualizar órdenes como VENDEDOR y verificar 403 de cliente ajeno.
5. Reiniciar ambos Spring sin borrar el volumen, confirmar persistencia y consistencia de stock.
6. Generar capturas reales para manual/ERS/integración y ZIP de código sin secretos.

**Estado:** sin evidencia MySQL real. No declarar PASS completo antes de estos pasos.

## Corte actual integrado

PR #12 CRUD, #13 rutas por rol, #14 protección de pedidos, #16 refresh JWT y #17 renovación React están integradas. ERS, manual y modelo de datos preparados como borradores. **No hay prueba MySQL real, capturas ni ZIP definitivos aún.**


## Alcance del docente: cinco microservicios independientes

Confirmado por Dante: Usuarios, Productos, Pedidos, Boleta, Detalle Boleta. Hay dos procesos en el repo (Usuarios y Catálogo), y Pedidos todavía es parte de Catálogo. Primer corte de extracción: API interna de reservas idempotentes en Productos con JWT SERVICIO_PEDIDOS, snapshots y cancelación compensatoria. Siguiente corte: proceso Pedidos independiente con cliente REST, timeout y saga; luego asegurar e integrar ZIP Boleta y Detalle. MySQL real permanece diferido hasta validación final.
