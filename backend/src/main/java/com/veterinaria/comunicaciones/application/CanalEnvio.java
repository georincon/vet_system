package com.veterinaria.comunicaciones.application;

/**
 * Puerto de salida por canal de comunicacion. Cada canal es un adaptador que se registra como bean.
 * <ul>
 *   <li>EMAIL: {@link EmailCanalEnvio} (SMTP, si {@code vet.mail.host}) o {@link EmailSimuladoCanalEnvio}.</li>
 *   <li>WHATSAPP (pendiente, NO implementado): un adaptador {@code WhatsappCanalEnvio} contra WhatsApp Business API
 *       con plantillas aprobadas ({@code plantilla_mensaje.plantilla_externa_id}); requiere proveedor. Mientras no
 *       exista, las reglas de canal WHATSAPP/SMS/PUSH se ignoran (se reportan en {@code reglasSinCanal}).</li>
 * </ul>
 */
public interface CanalEnvio {

    /** Codigo del canal: EMAIL, WHATSAPP, SMS o PUSH. */
    String canal();

    /** Envia el mensaje; lanza {@link EnvioFallidoException} si no pudo entregarse al proveedor. */
    ResultadoEnvio enviar(Mensaje mensaje);

    record Mensaje(String destinatario, String asunto, String cuerpo) {
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
