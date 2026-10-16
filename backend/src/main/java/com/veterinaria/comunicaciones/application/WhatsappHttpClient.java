package com.veterinaria.comunicaciones.application;

import java.io.IOException;

/** Cliente HTTP inyectable del adaptador de WhatsApp (permite sustituirlo en pruebas). */
public interface WhatsappHttpClient {

    record Respuesta(int status, String cuerpo) {
    }

    /** POST JSON con Bearer; lanza IOException ante errores de red/tiempo. */
    Respuesta post(String url, String bearer, String json) throws IOException;
}
