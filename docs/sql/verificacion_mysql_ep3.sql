-- EP3: observación de la BD; no modifica datos.
-- Ejecutar solo al final, después de arrancar MySQL y los dos Spring Boot.
-- Estas consultas fallarán si el esquema/tablas todavía no existen.
SELECT table_schema, table_name
  FROM information_schema.tables
 WHERE table_schema IN ('huertohogar_usuarios','huertohogar_catalogo')
 ORDER BY table_schema, table_name;

SELECT table_schema, table_name, constraint_name, constraint_type
  FROM information_schema.table_constraints
 WHERE table_schema IN ('huertohogar_usuarios','huertohogar_catalogo')
 ORDER BY table_schema, table_name, constraint_type;

SELECT 'usuarios' AS entidad, COUNT(*) AS registros
  FROM huertohogar_usuarios.usuario
UNION ALL
SELECT 'productos', COUNT(*) FROM huertohogar_catalogo.producto
UNION ALL
SELECT 'pedidos', COUNT(*) FROM huertohogar_catalogo.pedido
UNION ALL
SELECT 'lineas', COUNT(*) FROM huertohogar_catalogo.linea_pedido;

SELECT 'stock_negativo' AS verificacion, COUNT(*) AS incidencias
  FROM huertohogar_catalogo.producto WHERE stock < 0
UNION ALL
SELECT 'sku_duplicado', COUNT(*) FROM (
  SELECT sku FROM huertohogar_catalogo.producto GROUP BY sku HAVING COUNT(*) > 1
) AS x
UNION ALL
SELECT 'pedido_reintentado', COUNT(*) FROM (
  SELECT cliente_id, clave_idempotencia FROM huertohogar_catalogo.pedido
  GROUP BY cliente_id, clave_idempotencia HAVING COUNT(*) > 1
) AS y
UNION ALL
SELECT 'rol_invalido', COUNT(*) FROM huertohogar_usuarios.usuario
 WHERE rol_usuario NOT IN ('CLIENTE','VENDEDOR','ADMINISTRADOR');

-- Un resultado 0 de las incidencias esperadas NO reemplaza pruebas E2E.
