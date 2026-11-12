package com.example.demo;

import com.example.demo.services.RutChileno;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RutChilenoTest {
    @Test
    void aceptaRutConPuntosYNormaliza() {
        assertEquals("12345678-5", RutChileno.normalizarYValidar("12.345.678-5"));
    }

    @Test
    void rechazaDigitoVerificadorIncorrecto() {
        assertThrows(IllegalArgumentException.class,
            () -> RutChileno.normalizarYValidar("12.345.678-9"));
    }
}
