package com.veterinaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.veterinaria.laboratorio.domain.Banderas;
import com.veterinaria.laboratorio.domain.Banderas.Rango;
import com.veterinaria.laboratorio.domain.CsvParser;
import com.veterinaria.laboratorio.domain.Hl7Parser;
import com.veterinaria.laboratorio.domain.Valores;
import com.veterinaria.shared.error.BusinessException;

/** Pruebas unitarias de las reglas puras del laboratorio (sin Spring ni base de datos). */
class LaboratorioDominioTest {

    private static Rango rango(long id, String sexo, Integer emin, Integer emax, int min, int max) {
        return new Rango(id, sexo, emin, emax, BigDecimal.valueOf(min), BigDecimal.valueOf(max), null, null);
    }

    @Test
    void elRangoMasEspecificoGana() {
        Rango general = rango(1, null, null, null, 10, 20);
        Rango macho = rango(2, "M", null, null, 12, 22);
        Rango cachorro = rango(3, null, 0, 3, 5, 8);
        Rango cachorroMacho = rango(4, "M", 0, 3, 6, 9);
        List<Rango> todos = List.of(general, macho, cachorro, cachorroMacho);

        assertThat(Banderas.elegir(todos, "F", 36)).contains(general);
        assertThat(Banderas.elegir(todos, "M", 36)).contains(macho);
        assertThat(Banderas.elegir(todos, "F", 2)).contains(cachorro);
        assertThat(Banderas.elegir(todos, "M", 2)).contains(cachorroMacho);
        // sin edad conocida no aplican los rangos con limites de edad
        assertThat(Banderas.elegir(todos, "F", null)).contains(general);
        assertThat(Banderas.elegir(todos, "M", null)).contains(macho);
        // el limite superior de edad es inclusivo
        assertThat(Banderas.elegir(todos, "F", 3)).contains(cachorro);
        assertThat(Banderas.elegir(todos, "F", 4)).contains(general);
        assertThat(Banderas.elegir(List.of(), "F", 4)).isEmpty();
        // solo rangos para otro sexo: ninguno aplica
        assertThat(Banderas.elegir(List.of(macho), "F", 10)).isEmpty();
    }

    @Test
    void banderasNumericasConLimitesInclusivosYCriticos() {
        Rango r = new Rango(1L, null, null, null, BigDecimal.TEN, BigDecimal.valueOf(20), BigDecimal.valueOf(2), BigDecimal.valueOf(40));
        assertThat(Banderas.numerica(BigDecimal.TEN, r).bandera()).isEqualTo("N");
        assertThat(Banderas.numerica(BigDecimal.valueOf(20), r).bandera()).isEqualTo("N");
        assertThat(Banderas.numerica(new BigDecimal("9.99"), r).bandera()).isEqualTo("L");
        assertThat(Banderas.numerica(new BigDecimal("20.01"), r).bandera()).isEqualTo("H");
        assertThat(Banderas.numerica(BigDecimal.valueOf(2), r).bandera()).isEqualTo("L");
        assertThat(Banderas.numerica(new BigDecimal("1.9"), r)).isEqualTo(new Banderas.Evaluacion("LL", true));
        assertThat(Banderas.numerica(BigDecimal.valueOf(40), r).bandera()).isEqualTo("H");
        assertThat(Banderas.numerica(new BigDecimal("40.1"), r)).isEqualTo(new Banderas.Evaluacion("HH", true));
        // sin rango no hay bandera; con un solo limite se evalua ese limite
        assertThat(Banderas.numerica(BigDecimal.ONE, null).bandera()).isNull();
        assertThat(Banderas.numerica(BigDecimal.ONE, new Rango(2L, null, null, null, null, null, null, null)).bandera()).isNull();
        assertThat(Banderas.numerica(BigDecimal.valueOf(30), new Rango(3L, null, null, null, null, BigDecimal.valueOf(20), null, null)).bandera()).isEqualTo("H");
    }

    @Test
    void cualitativosYUnidades() {
        assertThat(Banderas.cualitativa("negativo", "NEGATIVO").bandera()).isEqualTo("N");
        assertThat(Banderas.cualitativa(" Positivo ", "NEGATIVO").bandera()).isEqualTo("A");
        assertThat(Banderas.cualitativa("POSITIVO", null).bandera()).isNull();
        assertThat(Valores.cualitativo("POS", "NEGATIVO|POSITIVO")).isEqualTo("POSITIVO");
        assertThat(Valores.cualitativo("Negative", "NEGATIVO|POSITIVO")).isEqualTo("NEGATIVO");
        assertThat(Valores.cualitativo("trazas", "NEGATIVO|TRAZAS|+")).isEqualTo("TRAZAS");
        assertThat(Valores.permitido("TRAZAS", "NEGATIVO|TRAZAS|+")).isTrue();
        assertThat(Valores.permitido("XX", "NEGATIVO|TRAZAS|+")).isFalse();
        assertThat(Valores.numero("12,5")).contains(new BigDecimal("12.5"));
        assertThat(Valores.numero("<0.5")).contains(new BigDecimal("0.5"));
        assertThat(Valores.numero("abc")).isEmpty();
        assertThat(Valores.mismaUnidad("x10^3/uL", "10*3/uL")).isTrue();
        assertThat(Valores.mismaUnidad("x10^3/µL", "x10^3/uL")).isTrue();
        assertThat(Valores.mismaUnidad("mg/dL", "g/dL")).isFalse();
    }

    @Test
    void hl7ParseaOruR01ConEscapesLoincYNotas() {
        String msg = String.join("\r",
                "MSH|^~\\&|ANALIZADOR|LAB|VETSYS|CLINICA|20261015||ORU^R01|CTRL-77|P|2.5",
                "PID|1||PAC-1^^^CLINICA^MR||Firulais",
                "OBR|1|ORD-9|LAB55|HEMO^Hemograma^L",
                "OBX|1|NM|718-7^Hemoglobina^LN^HGB^Hb^L||13.1|g/dL|12-18|N|||F",
                "OBX|2|CE|PARVO^Parvovirus^L||NEG^Negativo^L||||||F",
                "OBX|3|ST|FROT^Frotis^L||Normocitico\\F\\normocromico||||||F",
                "OBX|4|NM|X1^Cancelado^L||1|u|||||X",
                "OBX|5|SN|WBC^Leucocitos^L||>^20|10\\S\\3/uL||||F",
                "NTE|1||Muestra \\T\\ lipemica");
        var m = Hl7Parser.parsear(msg);
        assertThat(m.controlId()).isEqualTo("CTRL-77");
        assertThat(m.advertencias()).hasSize(1);
        assertThat(m.lineas()).hasSize(4);
        var hgb = m.lineas().get(0);
        assertThat(hgb.ordenCodigo()).isEqualTo("ORD-9");
        assertThat(hgb.pacienteCodigo()).isEqualTo("PAC-1");
        assertThat(hgb.examenCodigo()).isEqualTo("HEMO");
        assertThat(hgb.loinc()).isEqualTo("718-7");
        assertThat(hgb.parametroCodigo()).isEqualTo("HGB");
        assertThat(hgb.valor()).isEqualTo("13.1");
        assertThat(hgb.unidad()).isEqualTo("g/dL");
        assertThat(hgb.observacion()).isEqualTo("Bandera del equipo: N");
        var parvo = m.lineas().get(1);
        assertThat(parvo.parametroCodigo()).isEqualTo("PARVO");
        assertThat(parvo.loinc()).isNull();
        assertThat(parvo.valor()).isEqualTo("Negativo");
        assertThat(m.lineas().get(2).valor()).isEqualTo("Normocitico|normocromico");
        var wbc = m.lineas().get(3);
        assertThat(wbc.valor()).isEqualTo(">20");
        assertThat(wbc.unidad()).isEqualTo("10^3/uL");
        assertThat(wbc.observacion()).isEqualTo("Muestra & lipemica");
    }

    @Test
    void hl7RechazaMensajesInvalidos() {
        assertThatThrownBy(() -> Hl7Parser.parsear("")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Hl7Parser.parsear("PID|1||X")).hasMessageContaining("MSH");
        assertThatThrownBy(() -> Hl7Parser.parsear("MSH|^~\\&|A|B|C|D|2026||ORM^O01|1|P|2.5")).hasMessageContaining("ORU^R01");
        assertThatThrownBy(() -> Hl7Parser.parsear("MSH|^~\\&|A|B|C|D|2026||ORU^R01|1|P|2.5\rPID|1||X")).hasMessageContaining("OBX");
    }

    @Test
    void csvParseaDelimitadoresComillasYComentarios() {
        var lineas = CsvParser.parsear("﻿Orden_Codigo;Parametro;Valor;Unidad;Observaciones\n# nota\nORD-1;HCT;41,5;%;\"a;b\"\n\nORD-1;WBC;8;;\n");
        assertThat(lineas).hasSize(2);
        assertThat(lineas.get(0).linea()).isEqualTo(3);
        assertThat(lineas.get(0).ordenCodigo()).isEqualTo("ORD-1");
        assertThat(lineas.get(0).parametroCodigo()).isEqualTo("HCT");
        assertThat(lineas.get(0).valor()).isEqualTo("41,5");
        assertThat(lineas.get(0).observacion()).isEqualTo("a;b");
        assertThat(lineas.get(1).unidad()).isNull();
        // la plantilla se interpreta tal cual
        assertThat(CsvParser.parsear(CsvParser.PLANTILLA)).hasSize(3);
        assertThatThrownBy(() -> CsvParser.parsear("a,b\n1,2")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> CsvParser.parsear("parametro,valor\nHCT,")).hasMessageContaining("falta el valor");
        assertThatThrownBy(() -> CsvParser.parsear("parametro,valor\n")).hasMessageContaining("no contiene filas");
    }
}
