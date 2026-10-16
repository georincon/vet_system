/**
 * Compartir documentos clinicos con el tutor: valida permisos, consentimiento del canal y autorizacion de datos, genera los
 * PDF a traves del puerto {@code shared.documentos.DocumentoCompartible} (sin depender de laboratorio, clinica, etc.), los envia
 * por correo (adjuntos) o WhatsApp (enlaces seguros con token, vencimiento y limite de descargas) usando el puerto
 * {@code comunicaciones.CanalEnvio}, y deja bitacora, instantanea del PDF enviado y accesos publicos.
 */
@ApplicationModule(displayName = "Compartir")
package com.veterinaria.compartir;

import org.springframework.modulith.ApplicationModule;
