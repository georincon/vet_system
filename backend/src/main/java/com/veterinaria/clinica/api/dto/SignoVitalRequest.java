package com.veterinaria.clinica.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Toma de signos vitales. Todos los campos son opcionales pero debe venir al menos uno. Si trae
 * {@code pesoKg} tambien se registra en el historial de peso del paciente y actualiza su peso actual.
 * En PUT los campos ausentes (null) no se modifican y una cadena vacia limpia mucosas/condicionCorporal/notas.
 */
public record SignoVitalRequest(
        @DecimalMin(value = "20.0", message = "La temperatura minima es 20") @DecimalMax(value = "50.0", message = "La temperatura maxima es 50") BigDecimal temperatura,
        @Min(value = 0, message = "La frecuencia cardiaca no puede ser negativa") @Max(value = 500, message = "La frecuencia cardiaca excede el maximo") Integer frecuenciaCardiaca,
        @Min(value = 0, message = "La frecuencia respiratoria no puede ser negativa") @Max(value = 300, message = "La frecuencia respiratoria excede el maximo") Integer frecuenciaRespiratoria,
        @DecimalMin(value = "0.0", inclusive = false, message = "El peso debe ser positivo") @DecimalMax(value = "999.99", message = "El peso excede el maximo") BigDecimal pesoKg,
        @Size(max = 30, message = "La condicion corporal no puede exceder 30 caracteres") String condicionCorporal,
        @Min(value = 1, message = "El puntaje de condicion corporal va de 1 a 9") @Max(value = 9, message = "El puntaje de condicion corporal va de 1 a 9") Integer puntajeCondicionCorporal,
        @Size(max = 60, message = "Las mucosas no pueden exceder 60 caracteres") String mucosas,
        @DecimalMin(value = "0.0", message = "El llenado capilar no puede ser negativo") @DecimalMax(value = "9.9", message = "El llenado capilar excede el maximo") BigDecimal tiempoLlenadoCapilar,
        @Min(value = 0, message = "La escala de dolor va de 0 a 10") @Max(value = 10, message = "La escala de dolor va de 0 a 10") Integer escalaDolor,
        @DecimalMin(value = "0.0", message = "La hidratacion no puede ser negativa") @DecimalMax(value = "100.0", message = "La hidratacion maxima es 100") BigDecimal hidratacionPct,
        @Min(value = 0, message = "La presion sistolica no puede ser negativa") @Max(value = 400, message = "La presion sistolica excede el maximo") Integer presionSistolica,
        @DecimalMin(value = "0.0", message = "La SpO2 no puede ser negativa") @DecimalMax(value = "100.0", message = "La SpO2 maxima es 100") BigDecimal spo2,
        @DecimalMin(value = "0.0", message = "La glucosa no puede ser negativa") @DecimalMax(value = "9999.9", message = "La glucosa excede el maximo") BigDecimal glucosaMgDl,
        @Size(max = 2000, message = "Las notas no pueden exceder 2000 caracteres") String notas) {
}
