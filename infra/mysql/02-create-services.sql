-- EP3: esquemas independientes para los microservicios 3, 4 y 5.
-- MySQL crea huertohogar_usuarios y al usuario huerto_app con MYSQL_DATABASE/MYSQL_USER.
-- 01-create-catalog.sql crea huertohogar_catalogo.
-- Este archivo se ejecuta automaticamente SOLO al inicializar un volumen vacio.
-- En un volumen existente, ejecutar manualmente este mismo SQL sin borrar datos.
CREATE DATABASE IF NOT EXISTS huertohogar_pedidos
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS huertohogar_boleta
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS huertohogar_detalle_boleta
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON huertohogar_pedidos.* TO 'huerto_app'@'%';
GRANT ALL PRIVILEGES ON huertohogar_boleta.* TO 'huerto_app'@'%';
GRANT ALL PRIVILEGES ON huertohogar_detalle_boleta.* TO 'huerto_app'@'%';
