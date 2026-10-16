package com.veterinaria.comunicaciones.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TelefonosTest {

    @Test
    void normalizaAE164ConColombiaPorDefecto() {
        assertEquals("+573001234567", Telefonos.aE164("3001234567", "57"));
        assertEquals("+573001234567", Telefonos.aE164("300 123 4567", "57"));
        assertEquals("+573001234567", Telefonos.aE164("(300) 123-4567", "57"));
        assertEquals("+573001234567", Telefonos.aE164("+57 300 123 4567", "57"));
        assertEquals("+573001234567", Telefonos.aE164("573001234567", "57"));
        assertEquals("+573001234567", Telefonos.aE164("0057 300 1234567", "57"));
        assertEquals("+573001234567", Telefonos.aE164("03001234567", "57"));
        assertEquals("+14155552671", Telefonos.aE164("+1 415 555 2671", "57"));
        assertEquals("+14155552671", Telefonos.aE164("4155552671", "1"));
    }

    @Test
    void rechazaNumerosInvalidos() {
        assertNull(Telefonos.aE164(null, "57"));
        assertNull(Telefonos.aE164("", "57"));
        assertNull(Telefonos.aE164("abc", "57"));
        assertNull(Telefonos.aE164("123", "57"));
        assertNull(Telefonos.aE164("+57 12345", "57"));
        assertNull(Telefonos.aE164("+1234567890123456789", "57"));
        assertNull(Telefonos.aE164("300-ABC-4567", "57"));
    }

    @Test
    void enmascaraContactos() {
        assertEquals("j***@correo.com", Telefonos.enmascarar("juan@correo.com"));
        assertEquals("+57******4567", Telefonos.enmascarar("+573001234567"));
        assertEquals("-", Telefonos.enmascarar(null));
    }
}
