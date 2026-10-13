package com.veterinaria.shared.web;

import jakarta.validation.groups.Default;

/**
 * Grupo de validacion para altas: exige los campos obligatorios ademas de las
 * restricciones por defecto. Las ediciones parciales solo validan el grupo por defecto.
 */
public interface Alta extends Default {
}
