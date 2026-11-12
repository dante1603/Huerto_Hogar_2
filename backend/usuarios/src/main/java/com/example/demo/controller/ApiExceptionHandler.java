package com.example.demo.controller;

import com.example.demo.services.UsuarioDuplicadoException;
import com.example.demo.services.UsuarioNoEncontradoException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validarCampos(MethodArgumentNotValidException ex) {
        List<String> errores = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .distinct()
            .toList();
        return ResponseEntity.badRequest().body(Map.of(
            "codigo", "DATOS_INVALIDOS", "errores", errores));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> rutInvalido(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("codigo", "DATOS_INVALIDOS", "mensaje", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> jsonInvalido(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("codigo", "JSON_INVALIDO", "mensaje", "Revisa el cuerpo JSON de la solicitud"));
    }

    @ExceptionHandler(UsuarioDuplicadoException.class)
    public ResponseEntity<Map<String, String>> duplicado(UsuarioDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("codigo", "USUARIO_DUPLICADO", "mensaje", ex.getMessage()));
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(UsuarioNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("codigo", "USUARIO_NO_ENCONTRADO", "mensaje", ex.getMessage()));
    }
}
