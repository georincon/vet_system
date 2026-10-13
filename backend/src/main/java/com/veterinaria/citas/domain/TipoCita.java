package com.veterinaria.citas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Tipo de cita (consulta, vacunacion, cirugia...) con su duracion por defecto. */
@Entity
@Table(name = "tipo_cita")
@Getter
@Setter
public class TipoCita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "duracion_minutos", nullable = false)
    private int duracionMinutos = 30;

    @Column(length = 20)
    private String color;

    @Column(name = "requiere_confirmacion", nullable = false)
    private boolean requiereConfirmacion;

    @Column(nullable = false)
    private boolean activo = true;
}
