@echo off
setlocal
cd /d "%~dp0"
if not exist ".env" (
  echo FALTA .env. Copia .env.example como .env y configura las credenciales.
  exit /b 1
)
for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do set "%%A=%%B"
call mvnw.cmd spring-boot:run
