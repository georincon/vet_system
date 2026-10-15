package com.veterinaria.inventario.domain;

/** Motivos de salida manual de stock. MERMA, DANIO y DESECHO se registran como DESECHO en el libro de controlados. */
public enum MotivoSalida {
    USO_CLINICO(false), CONSUMO_INTERNO(false), MUESTRA(false), MERMA(true), DANIO(true), DESECHO(true);

    private final boolean desecho;

    MotivoSalida(boolean desecho) {
        this.desecho = desecho;
    }

    public boolean esDesecho() {
        return desecho;
    }
}
