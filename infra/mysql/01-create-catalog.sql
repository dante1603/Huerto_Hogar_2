-- Laboratorio local: MySQL crea 'huertohogar_usuarios' mediante MYSQL_DATABASE.
CREATE DATABASE IF NOT EXISTS huertohogar_catalogo
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- El entrypoint oficial de mysql:8.4 crea primero al usuario definido por MYSQL_USER.
GRANT ALL PRIVILEGES ON huertohogar_catalogo.* TO 'huerto_app'@'%';
