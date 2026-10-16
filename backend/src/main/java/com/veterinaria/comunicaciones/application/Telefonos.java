package com.veterinaria.comunicaciones.application;

/** Normalizacion de telefonos a E.164 (pais por defecto configurable; Colombia +57). */
public final class Telefonos {

    private Telefonos() {
    }

    /**
     * Devuelve el numero en E.164 ({@code +573001234567}) o {@code null} si no es valido.
     * Acepta espacios, guiones y parentesis, prefijo {@code +} o {@code 00}, y numeros nacionales sin codigo de pais.
     */
    public static String aE164(String crudo, String paisDefecto) {
        if (crudo == null) {
            return null;
        }
        String s = crudo.trim();
        if (!s.matches("[+0-9 ()\\-.]+")) {
            return null;
        }
        boolean internacional = s.startsWith("+");
        String d = s.replaceAll("\\D", "");
        if (d.startsWith("00")) {
            d = d.substring(2);
            internacional = true;
        }
        String cc = paisDefecto == null ? "57" : paisDefecto.replaceAll("\\D", "");
        if (!internacional) {
            d = d.replaceFirst("^0+", "");
            if (d.length() <= 10) {
                d = cc + d;
            }
        }
        if (d.length() < 8 || d.length() > 15 || d.startsWith("0")) {
            return null;
        }
        if (d.startsWith(cc) && "57".equals(cc) && d.length() != 12) {
            return null;
        }
        return "+" + d;
    }

    /** Enmascara un telefono E.164 o un correo para la bitacora. */
    public static String enmascarar(String contacto) {
        if (contacto == null || contacto.isBlank()) {
            return "-";
        }
        int arroba = contacto.indexOf('@');
        if (arroba > 0) {
            return contacto.charAt(0) + "***" + contacto.substring(arroba);
        }
        String d = contacto;
        return d.length() <= 6 ? "***" : d.substring(0, 3) + "*".repeat(d.length() - 7) + d.substring(d.length() - 4);
    }
}
