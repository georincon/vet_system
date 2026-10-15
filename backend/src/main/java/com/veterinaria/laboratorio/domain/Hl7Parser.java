package com.veterinaria.laboratorio.domain;

import java.util.ArrayList;
import java.util.List;

import com.veterinaria.shared.error.BusinessException;

/**
 * Lector de mensajes HL7 v2.x de resultados (ORU^R01): segmentos MSH, PID, OBR, OBX y NTE. Respeta los delimitadores
 * declarados en MSH-1/MSH-2 y las secuencias de escape. Cada OBX se convierte en una {@link LineaEntrada}; el
 * identificador del OBX se interpreta como LOINC cuando su sistema es LN/LOINC y como codigo local en otro caso.
 */
public final class Hl7Parser {

    /** Mensaje interpretado: lineas de resultado, identificador del mensaje y avisos no fatales. */
    public record Mensaje(String controlId, List<LineaEntrada> lineas, List<String> advertencias) {
    }

    private Hl7Parser() {
    }

    public static Mensaje parsear(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new BusinessException("El mensaje HL7 esta vacio");
        }
        String limpio = texto.replace("﻿", "").replace("\u000B", "").replace("\u001C", "");
        String[] segmentos = limpio.split("\\r\\n|\\r|\\n");
        int inicio = 0;
        while (inicio < segmentos.length && segmentos[inicio].isBlank()) {
            inicio++;
        }
        if (inicio >= segmentos.length || !segmentos[inicio].startsWith("MSH") || segmentos[inicio].length() < 8) {
            throw new BusinessException("Mensaje HL7 invalido: debe comenzar con el segmento MSH");
        }
        String msh = segmentos[inicio];
        char campo = msh.charAt(3);
        String[] mshCampos = msh.split(java.util.regex.Pattern.quote(String.valueOf(campo)), -1);
        String codificacion = mshCampos.length > 1 ? mshCampos[1] : "^~\\&";
        if (codificacion.length() < 4) {
            codificacion = "^~\\&";
        }
        Delimitadores d = new Delimitadores(campo, codificacion.charAt(0), codificacion.charAt(1), codificacion.charAt(2),
                codificacion.charAt(3));
        String tipoMensaje = mshCampos.length > 8 ? mshCampos[8] : "";
        if (!tipoMensaje.toUpperCase().startsWith("ORU")) {
            throw new BusinessException("Solo se admiten mensajes de resultados ORU^R01 (MSH-9 recibido: '" + tipoMensaje + "')");
        }
        String controlId = mshCampos.length > 9 ? mshCampos[9] : null;

        List<LineaEntrada> lineas = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        String paciente = null;
        String orden = null;
        String examen = null;
        int ultimaObx = -1;
        for (int i = inicio + 1; i < segmentos.length; i++) {
            String seg = segmentos[i];
            if (seg.length() < 3) {
                continue;
            }
            String nombre = seg.substring(0, 3);
            String[] f = seg.split(java.util.regex.Pattern.quote(String.valueOf(campo)), -1);
            int numeroLinea = i + 1;
            switch (nombre) {
                case "PID" -> paciente = vacioANulo(componente(campo(f, 3), 1, d));
                case "OBR" -> {
                    // OBR-2 (numero de orden del solicitante) es el codigo de la orden de este sistema; OBR-3 es del laboratorio
                    orden = vacioANulo(componente(campo(f, 2), 1, d));
                    examen = vacioANulo(componente(campo(f, 4), 1, d));
                    ultimaObx = -1;
                }
                case "OBX" -> {
                    String estado = campo(f, 11);
                    if ("X".equalsIgnoreCase(estado) || "D".equalsIgnoreCase(estado)) {
                        avisos.add("Linea " + numeroLinea + ": OBX con estado '" + estado + "' omitido");
                        break;
                    }
                    String[] id = campo(f, 3).split(java.util.regex.Pattern.quote(String.valueOf(d.componente())), -1);
                    String id1 = at(id, 0), id2 = at(id, 1), sistema = at(id, 2), alt1 = at(id, 3), altSistema = at(id, 5);
                    String loinc = null;
                    String local = null;
                    if (esLoinc(sistema)) {
                        loinc = vacioANulo(id1);
                        local = esLoinc(altSistema) ? null : vacioANulo(alt1);
                    } else {
                        local = vacioANulo(id1);
                        loinc = esLoinc(altSistema) ? vacioANulo(alt1) : null;
                    }
                    String tipoValor = campo(f, 2);
                    String valor = valorObx(campo(f, 5), tipoValor, d);
                    String unidad = componente(campo(f, 6), 1, d);
                    if (vacioANulo(unidad) == null) {
                        unidad = componente(campo(f, 6), 2, d);
                    }
                    String banderaEquipo = vacioANulo(campo(f, 8));
                    String observacion = banderaEquipo == null ? null : "Bandera del equipo: " + banderaEquipo;
                    if (vacioANulo(valor) == null) {
                        avisos.add("Linea " + numeroLinea + ": OBX sin valor omitido");
                        break;
                    }
                    lineas.add(new LineaEntrada(numeroLinea, orden, paciente, examen, local, loinc, vacioANulo(id2), valor,
                            vacioANulo(unidad), observacion));
                    ultimaObx = lineas.size() - 1;
                }
                case "NTE" -> {
                    String nota = desescapar(campo(f, 3), d);
                    if (ultimaObx >= 0 && vacioANulo(nota) != null) {
                        LineaEntrada l = lineas.get(ultimaObx);
                        String obs = l.observacion() == null ? nota : l.observacion() + " | " + nota;
                        lineas.set(ultimaObx, new LineaEntrada(l.linea(), l.ordenCodigo(), l.pacienteCodigo(), l.examenCodigo(),
                                l.parametroCodigo(), l.loinc(), l.nombre(), l.valor(), l.unidad(), obs));
                    }
                }
                default -> {
                    // otros segmentos (ORC, SPM, etc.) no aportan resultados
                }
            }
        }
        if (lineas.isEmpty()) {
            throw new BusinessException("El mensaje HL7 no contiene segmentos OBX con resultados");
        }
        return new Mensaje(vacioANulo(controlId), lineas, avisos);
    }

    private record Delimitadores(char campo, char componente, char repeticion, char escape, char subcomponente) {
    }

    private static boolean esLoinc(String sistema) {
        return sistema != null && (sistema.equalsIgnoreCase("LN") || sistema.equalsIgnoreCase("LOINC"));
    }

    /** Campo n de un segmento (PID, OBR, OBX...): el indice 0 es el nombre del segmento. */
    private static String campo(String[] f, int n) {
        return n < f.length ? f[n] : "";
    }

    private static String at(String[] a, int i) {
        return i < a.length ? a[i] : "";
    }

    private static String componente(String campo, int n, Delimitadores d) {
        String primeraRepeticion = campo.split(java.util.regex.Pattern.quote(String.valueOf(d.repeticion())), -1)[0];
        String[] c = primeraRepeticion.split(java.util.regex.Pattern.quote(String.valueOf(d.componente())), -1);
        return desescapar(at(c, n - 1), d);
    }

    private static String valorObx(String campo5, String tipo, Delimitadores d) {
        String primera = campo5.split(java.util.regex.Pattern.quote(String.valueOf(d.repeticion())), -1)[0];
        String[] c = primera.split(java.util.regex.Pattern.quote(String.valueOf(d.componente())), -1);
        String t = tipo == null ? "" : tipo.toUpperCase();
        return switch (t) {
            case "CE", "CWE", "CNE" -> {
                String texto = desescapar(at(c, 1), d);
                yield vacioANulo(texto) != null ? texto : desescapar(at(c, 0), d);
            }
            case "SN" -> (desescapar(at(c, 0), d) + desescapar(at(c, 1), d)).trim();
            default -> desescapar(primera, d);
        };
    }

    private static String desescapar(String s, Delimitadores d) {
        if (s == null || s.indexOf(d.escape()) < 0) {
            return s == null ? "" : s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == d.escape()) {
                int fin = s.indexOf(d.escape(), i + 1);
                if (fin > i) {
                    String codigo = s.substring(i + 1, fin);
                    switch (codigo) {
                        case "F" -> sb.append(d.campo());
                        case "S" -> sb.append(d.componente());
                        case "T" -> sb.append(d.subcomponente());
                        case "R" -> sb.append(d.repeticion());
                        case "E" -> sb.append(d.escape());
                        case ".br" -> sb.append('\n');
                        default -> {
                            // secuencias de formato/hex no soportadas: se ignoran
                        }
                    }
                    i = fin;
                    continue;
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
