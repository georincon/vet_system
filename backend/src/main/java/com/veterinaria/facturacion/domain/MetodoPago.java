package com.veterinaria.facturacion.domain;

/** Medios de pago aceptados al registrar un pago (ANTICIPO y CREDITO existen en la BD pero no se usan por la API). */
public enum MetodoPago {
    EFECTIVO, TARJETA, TRANSFERENCIA, NEQUI, DAVIPLATA, PSE, DATAFONO, CHEQUE, OTRO
}
