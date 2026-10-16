package com.veterinaria.comunicaciones.application;

import java.util.List;
import java.util.Map;

/**
 * Puerto de salida por canal de comunicacion. Cada canal es un adaptador que se registra como bean.
 * <ul>
 *   <li>EMAIL: {@link EmailCanalEnvio} (SMTP, si {@code vet.mail.host}) o {@link EmailSimuladoCanalEnvio}; admite adjuntos.</li>
 *   <li>WHATSAPP: {@link WhatsappCloudApiCanalEnvio} (Meta WhatsApp Cloud API) o modo simulado sin credenciales. Se usa para
 *       compartir documentos con el tutor; NO para los recordatorios automaticos ({@link #admiteRecordatorios()} es false:
 *       exigen plantillas aprobadas por regla), asi las reglas de canal WHATSAPP se siguen reportando en {@code reglasSinCanal}.</li>
 * </ul>
 */
public interface CanalEnvio {

    /** Codigo del canal: EMAIL, WHATSAPP, SMS o PUSH. */
    String canal();

    /** Envia el mensaje; lanza {@link EnvioFallidoException} si no pudo entregarse al proveedor. */
    ResultadoEnvio enviar(Mensaje mensaje);

    /** Si el canal puede usarse en el motor de recordatorios automaticos (hoy solo EMAIL). */
    default boolean admiteRecordatorios() {
        return true;
    }

    /**
     * Mensaje a enviar. {@code adjuntos}: archivos que viajan en el mensaje (EMAIL). {@code documentos}: documentos que el
     * proveedor descarga por URL (WHATSAPP). {@code variables}: valores de plantilla (tutor, paciente, clinica, documento,
     * enlace) para canales con plantillas aprobadas por el proveedor.
     */
    record Mensaje(String destinatario, String asunto, String cuerpo, List<Adjunto> adjuntos, List<DocumentoEnlace> documentos,
            Map<String, String> variables) {

        public Mensaje(String destinatario, String asunto, String cuerpo) {
            this(destinatario, asunto, cuerpo, List.of(), List.of(), Map.of());
        }
    }

    record Adjunto(String nombre, String contentType, byte[] contenido) {
    }

    record DocumentoEnlace(String nombre, String url) {
    }

    /** {@code simulado}: no salio a un proveedor real (se registra en el log y se marca ENVIADO_SIMULADO). */
    record ResultadoEnvio(boolean simulado, String proveedor, String mensajeId) {
    }

    class EnvioFallidoException extends RuntimeException {
        public EnvioFallidoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
