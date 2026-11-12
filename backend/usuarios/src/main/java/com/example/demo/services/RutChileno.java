package com.example.demo.services;

import java.util.Locale;

public final class RutChileno {
    private RutChileno() {}

    // Devuelve formato 12345678-5, si el digito verificador es valido.
    public static String normalizarYValidar(String rut) {
        if (rut == null) {
            throw new IllegalArgumentException("El RUT es obligatorio");
        }
        String limpio = rut.strip().replace(".", "").replace("-", "").toUpperCase(Locale.ROOT);
        if (!limpio.matches("[0-9]{7,8}[0-9K]")) {
            throw new IllegalArgumentException("El formato del RUT no es valido");
        }
        String numero = limpio.substring(0, limpio.length() - 1);
        char dv = limpio.charAt(limpio.length() - 1);
        int suma = 0;
        int factor = 2;
        for (int i = numero.length() - 1; i >= 0; i--) {
            suma += (numero.charAt(i) - '0') * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }
        int resto = 11 - (suma % 11);
        char esperado = resto == 11 ? '0' : resto == 10 ? 'K' : (char) ('0' + resto);
        if (dv != esperado) {
            throw new IllegalArgumentException("El digito verificador del RUT no es valido");
        }
        return numero + "-" + dv;
    }
}
