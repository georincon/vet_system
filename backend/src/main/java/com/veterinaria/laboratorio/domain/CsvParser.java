package com.veterinaria.laboratorio.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.veterinaria.shared.error.BusinessException;

/**
 * Lector de CSV de resultados (coma o punto y coma; comillas dobles; comentarios con '#'). Columnas reconocidas:
 * orden_codigo, paciente_codigo, examen_codigo, parametro_codigo, loinc, valor (obligatoria), unidad, observacion.
 */
public final class CsvParser {

    /** Plantilla descargable con ejemplos de las columnas. */
    public static final String PLANTILLA = String.join("\r\n",
            "orden_codigo,examen_codigo,parametro_codigo,loinc,valor,unidad,observacion",
            "# Una fila por resultado. Las lineas que empiezan con # se ignoran.",
            "# orden_codigo puede omitirse si al importar se indica ordenId. examen_codigo es opcional.",
            "# Identifique el parametro con parametro_codigo (codigo local) y/o loinc.",
            "# Decimales con punto o coma; cualitativos: NEGATIVO/POSITIVO (tambien NEG/POS).",
            "ORD-20260101-00001,HEMO,HCT,4544-3,41.5,%,",
            "ORD-20260101-00001,HEMO,WBC,6690-2,22.3,x10^3/uL,Leucocitosis marcada",
            "ORD-20260101-00001,PARVO,PARVO,,NEGATIVO,,",
            "");

    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("orden", "orden"), Map.entry("orden_codigo", "orden"), Map.entry("ordencodigo", "orden"),
            Map.entry("paciente", "paciente"), Map.entry("paciente_codigo", "paciente"), Map.entry("pacientecodigo", "paciente"),
            Map.entry("examen", "examen"), Map.entry("examen_codigo", "examen"), Map.entry("examencodigo", "examen"),
            Map.entry("parametro", "parametro"), Map.entry("parametro_codigo", "parametro"), Map.entry("parametrocodigo", "parametro"),
            Map.entry("codigo", "parametro"), Map.entry("loinc", "loinc"), Map.entry("valor", "valor"),
            Map.entry("resultado", "valor"), Map.entry("unidad", "unidad"), Map.entry("observacion", "observacion"),
            Map.entry("observaciones", "observacion"), Map.entry("nombre", "nombre"));

    private CsvParser() {
    }

    public static List<LineaEntrada> parsear(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new BusinessException("El CSV esta vacio");
        }
        String[] lineas = texto.replace("﻿", "").split("\\r\\n|\\r|\\n", -1);
        char sep = ',';
        Map<String, Integer> columnas = null;
        List<LineaEntrada> salida = new ArrayList<>();
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i];
            if (linea.isBlank() || linea.trim().startsWith("#")) {
                continue;
            }
            if (columnas == null) {
                sep = contar(linea, ';') > contar(linea, ',') ? ';' : ',';
                columnas = cabecera(dividir(linea, sep));
                continue;
            }
            List<String> c = dividir(linea, sep);
            String valor = celda(c, columnas, "valor");
            if (valor == null) {
                throw new BusinessException("Linea " + (i + 1) + ": falta el valor");
            }
            salida.add(new LineaEntrada(i + 1, celda(c, columnas, "orden"), celda(c, columnas, "paciente"),
                    celda(c, columnas, "examen"), celda(c, columnas, "parametro"), celda(c, columnas, "loinc"),
                    celda(c, columnas, "nombre"), valor, celda(c, columnas, "unidad"), celda(c, columnas, "observacion")));
        }
        if (columnas == null) {
            throw new BusinessException("El CSV no tiene fila de encabezado");
        }
        if (salida.isEmpty()) {
            throw new BusinessException("El CSV no contiene filas de resultados");
        }
        return salida;
    }

    private static Map<String, Integer> cabecera(List<String> nombres) {
        Map<String, Integer> m = new java.util.HashMap<>();
        for (int i = 0; i < nombres.size(); i++) {
            String clave = ALIAS.get(nombres.get(i).trim().toLowerCase(Locale.ROOT).replace(" ", "_"));
            if (clave != null) {
                m.putIfAbsent(clave, i);
            }
        }
        if (!m.containsKey("valor")) {
            throw new BusinessException("El CSV debe tener la columna 'valor'");
        }
        if (!m.containsKey("parametro") && !m.containsKey("loinc")) {
            throw new BusinessException("El CSV debe tener la columna 'parametro_codigo' o 'loinc'");
        }
        return m;
    }

    private static String celda(List<String> c, Map<String, Integer> columnas, String clave) {
        Integer i = columnas.get(clave);
        if (i == null || i >= c.size()) {
            return null;
        }
        String v = c.get(i).trim();
        return v.isEmpty() ? null : v;
    }

    private static int contar(String s, char ch) {
        return (int) s.chars().filter(x -> x == ch).count();
    }

    /** Divide una linea respetando comillas dobles y "" como comilla escapada. */
    static List<String> dividir(String linea, char sep) {
        List<String> r = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char ch = linea.charAt(i);
            if (comillas) {
                if (ch == '"') {
                    if (i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        comillas = false;
                    }
                } else {
                    sb.append(ch);
                }
            } else if (ch == '"') {
                comillas = true;
            } else if (ch == sep) {
                r.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        r.add(sb.toString());
        return r;
    }
}
