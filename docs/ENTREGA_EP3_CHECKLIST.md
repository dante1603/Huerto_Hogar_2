# Manifiesto de entrega — EP3 DSY1104 | Encargo grupal

**Estado:** preparación, no entrega final. Este manifiesto corresponde solo al **40 % interno del encargo EP3**, no a la presentación individual.

Rúbrica: https://drive.google.com/file/d/18kJBae2l4Ah65LWYEkW6U33tCqwFIUOw/view
Anexo: https://drive.google.com/file/d/1U7bZVSOMwfoYaYly6IcSZ_BvYIgEn4lO/view

## Matriz de requisitos técnicos y evidencias

| IE | Peso | Código | Falta antes de calificarlo PASS |
|---|---:|---|---|
| IE3.1.1 backend + BD | 8% | Entidades JPA, repositorios, servicios, Docker MySQL | MySQL real y persistencia reinicio |
| IE3.2.1 CRUD/Swagger | 8% | PR #12 integrado | Swagger abierto y captura de operaciones |
| IE3.2.2 API REST integrada | 6% | React fetch + cinco servicios comprobados vía HTTP real/H2 en PR #27 | Falta E2E navegador + MySQL y conmutación de React |
| IE3.3.1 JWT y roles | 6% | Spring Security/BCrypt, PR #14; renovación rotativa PR #16 | Provisión controlada de cuentas de rol y pruebas finales con MySQL |
| IE3.3.2 sesión persistente | 6% | sessionStorage, expiración, refresh auto PR #17 y tests Node | Prueba en navegador real, seguridad de almacenamiento y MySQL |
| IE3.3.3 guardas frontend | 6% | PR #13: admin/vendedor/cliente | Prueba real de tres usuarios y capturas |

## Entregables institucionales

| Archivo o enlace | Fuente actual | ¿Listo para entregar? |
|---|---|---|
| GitHub público frontend | https://github.com/dante1603/Huerto_Hogar_2/tree/integration/fullstack-2026-11-14/frontend/tienda | CÓDIGO DISPONIBLE; confirmar monorepo con docente |
| GitHub público backend | https://github.com/dante1603/Huerto_Hogar_2/tree/integration/fullstack-2026-11-14/backend | CÓDIGO DISPONIBLE; confirmar monorepo con docente |
| ZIP limpio frontend | frontend/tienda | PENDIENTE: empaquetar versión definitiva |
| ZIP limpio backend | backend/catalogo, backend/usuarios, backend/pedidos y backend/boleta y backend/detalle-boleta | PENDIENTE: empaquetar los cinco servicios finales sin secretos |
| ERS terminada | docs/ERS_EP3.md | BORRADOR: requiere aprobación de alcance y evidencia |
| Manual de usuario | docs/MANUAL_EP3.md | BORRADOR: faltan pantallazos |
| Documentación de API | docs/INFORME_INTEGRACION_EP3.md y docs/CONTRATOS_API.md | PARCIAL: falta volcar al Anexo 3 y capturas Swagger |
| Documento APIs e integración | docs/INFORME_INTEGRACION_EP3.md | PARCIAL: REST real/H2 PASS; faltan MySQL y navegador |
| Modelo de datos | docs/BD_MODELO_EP3.md | DERIVADO DE JPA, pendiente comparar con INFORMATION_SCHEMA |

El caso HuertoHogar original requiere **validación docente del alcance**. La adaptación Galaxym no cuenta aquí con un comprobante de aceptación; confirmar antes de cerrar ERS.

## Último corte, por decisión del usuario

MySQL auténtico y las capturas solo se prueban después de terminar código y documentos. No declarar la entrega completa por pruebas H2 ni por los modelos 3D experimentales.

Checklist final: comprobar el mismo commit de GitHub en ZIP, eliminar target/node_modules/.env, arrancar todos los servicios exigidos con credenciales locales, tomar capturas reales, actualizar enlaces, completar Anexo 3 y dejar documentación sin marcadores PENDIENTE.

## Seguimiento de los cinco microservicios confirmados

| Servicio | Estado real de código | Faltante |
|---|---|---|
| Usuarios | Integrado + CI | MySQL, navegador, cuentas de prueba |
| Productos | HTTP real con Pedidos, reserva y stock verificados en PR #27 | MySQL, concurrencia/red degradada, ruta legada |
| Pedidos | Independiente PR #21, compensación opt-in PR #26, HTTP real PR #27 | MySQL, activar/verificar compensación, React y navegador |
| Boleta | PR #23; HTTP real con Pedidos PASS PR #27 | MySQL y React; comprobante no tributario |
| Detalle Boleta | PR #25; HTTP real con Boleta/Pedidos PASS PR #27 | MySQL, React, E2E navegador y cierre documental |

Se mantiene abierta la issue #19 y la PR #8 draft hacia main; no confundir avance del código con entrega comprobada.


**Seguimiento de consistencia distribuida:** el corte de compensación opt-in de Pedidos evita confirmaciones tardías tras expirar una intención y usa DELETE idempotente con tombstone en Productos. El programador no se activa hasta completar MySQL y E2E con fallos de red reales. No cambiar proxy de React ni `main` por un PASS en H2.


**Evidencia transversal:** [GitHub Actions 43668577173](https://github.com/dante1603/Huerto_Hogar_2/actions/runs/43668577173) confirma 71 tests y circuito REST de cinco JVM con H2 independientes. No demuestra MySQL ni funcionamiento de la tienda React en navegador. Ver `docs/VERIFICACION_HTTP_EP3.md`.
