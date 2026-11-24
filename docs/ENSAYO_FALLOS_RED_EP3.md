# Ensayo de red degradada y concurrencia — corte EP3 15-11-2026

Este corte amplía `scripts/e2e_http_fullstack.py` mediante `scripts/fault_proxy.py`: proxy local `127.0.0.1:8090` **solo para la comunicación Pedidos → Productos**, con Productos real en 8080 y el resto de aplicaciones en 8081–8084. Todos mantienen H2 **distintas** y JVM independientes.

## Escenarios verificables

1. **Respuesta PUT perdida tras commit:** el proxy reenvía y recibe del Catálogo la reserva confirmada, pero corta la respuesta. Pedidos consulta GET y confirma con la misma clave. Stock se descuenta exactamente una vez.
2. **PUT confirmado y GET perdido:** se corta la respuesta de PUT después de reservar, y ambos intentos GET de recuperación antes de llegar a Productos (incluido el reintento automático de HttpURLConnection). Pedidos devuelve 503 y no publica una orden `INICIADO`.
3. **Misma Idempotency-Key simultánea:** seis llamadas POST a Pedidos en paralelo; solo una reserva efectiva, un 201 y cinco 200 con el mismo `pedido.id`.
4. **Competencia por stock:** cuatro claves distintas intentan reservar tres unidades de un producto con siete disponibles; dos transacciones obtienen 201, dos responden 409, y stock nunca queda negativo.
5. **Compensación con DELETE remoto ejecutado pero respuesta perdida:** en el runner **efímero**, `E2E_RECONCILIACION=1` habilita el worker cada 1000 ms y TTL de **1 minuto**. Una intención abandonada debe terminar CANCELADA tras reintentar DELETE a la misma referencia, sin reponer stock dos veces. El endpoint público con su clave original responde 409.

No se reutiliza una variable de sesión ni se registran tokens: el test genera un JWT secreto efímero y el proxy solo registra contadores de métodos/inyecciones. Las fallas se aplican a **una única llamada** por instrucción. Los cinco JAR, proxy y bases H2 viven exclusivamente durante la acción de CI.

**Fuera de alcance:** MySQL y restauración tras reinicio, falla de base local en medio de commit, caídas prolongadas de proceso/contenedor, load balancer real, React/browser y deploy. `RECONCILIACION_HABILITADA` sigue **false por defecto** fuera del runner; el worker del repositorio no se activa por esta prueba.

## Ejecutar

```bash
for servicio in catalogo usuarios pedidos boleta detalle-boleta; do
  mvn -B -ntp -f "backend/$servicio/pom.xml" -DskipTests package
done
E2E_RECONCILIACION=1 python3 scripts/e2e_http_fullstack.py
```

Comprobar logs y SHA del run antes de declarar PASS; el documento no constituye evidencia por sí mismo.

**Optimización CI:** empaquetado y circuito HTTP de fallos se ejecutan antes de la suite 71 tests para detectar y corregir errores de transporte temprano; la PR solo tendrá PASS completo si ambos tramos terminan en verde.

### Rigor del caso DELETE

El cliente HTTP de Java puede reintentar una petición DELETE dentro de **la misma llamada**. Para acreditar recuperación entre rondas del reconciliador, el proxy corta **ambas respuestas** de DELETE tras ejecutar el upstream. El ensayo exige **al menos tres DELETE** para la misma intención: dos respuestas perdidas y una respuesta posterior que confirma la cancelación. El servidor de Productos usa un tombstone, por lo que no devuelve stock dos veces. El programador continúa habilitado solo durante la prueba efímera.
