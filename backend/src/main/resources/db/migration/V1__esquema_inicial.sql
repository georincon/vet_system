-- ============================================================================
-- sistema veterinario - Migracion V1: esquema del MVP clinico-operativo
--
-- Flyway es el unico dueno del esquema (spring.jpa.hibernate.ddl-auto=validate).
-- Identificadores en espanol, snake_case y sin acentos. Las columnas de auditoria
-- usan TIMESTAMP WITH TIME ZONE (Instant en JPA).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- SEGURIDAD
-- ----------------------------------------------------------------------------
CREATE TABLE usuario (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(255) NOT NULL,
    password      VARCHAR(255) NOT NULL,
    nombre        VARCHAR(255) NOT NULL,
    email         VARCHAR(255),
    rol           VARCHAR(20)  NOT NULL,
    activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    actualizado_en TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_usuario_username UNIQUE (username),
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('ADMIN', 'VETERINARIO', 'RECEPCION', 'AUXILIAR'))
);

-- Trazabilidad de accesos y cambios (normativa historia clinica: reserva y auditoria)
CREATE TABLE auditoria (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT,
    accion     VARCHAR(100) NOT NULL,
    entidad    VARCHAR(100) NOT NULL,
    entidad_id BIGINT,
    detalle    JSONB,
    ip         VARCHAR(45),
    ocurrido_en TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auditoria_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);
CREATE INDEX ix_auditoria_entidad ON auditoria (entidad, entidad_id);
CREATE INDEX ix_auditoria_fecha ON auditoria (ocurrido_en);

-- ----------------------------------------------------------------------------
-- CATALOGOS DE LA CLINICA
-- ----------------------------------------------------------------------------
CREATE TABLE especie (
    id     BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_especie_nombre UNIQUE (nombre)
);

CREATE TABLE raza (
    id         BIGSERIAL PRIMARY KEY,
    especie_id BIGINT NOT NULL,
    nombre     VARCHAR(100) NOT NULL,
    activo     BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_raza_especie FOREIGN KEY (especie_id) REFERENCES especie (id),
    CONSTRAINT uq_raza_especie_nombre UNIQUE (especie_id, nombre)
);

CREATE TABLE tipo_cita (
    id                    BIGSERIAL PRIMARY KEY,
    nombre                VARCHAR(100) NOT NULL,
    duracion_minutos      INT NOT NULL DEFAULT 30,
    color                 VARCHAR(20),
    requiere_confirmacion BOOLEAN NOT NULL DEFAULT FALSE,
    activo                BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_tipo_cita_nombre UNIQUE (nombre),
    CONSTRAINT ck_tipo_cita_duracion CHECK (duracion_minutos > 0)
);

CREATE TABLE categoria_item (
    id     BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    tipo   VARCHAR(20) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_categoria_item_nombre UNIQUE (nombre),
    CONSTRAINT ck_categoria_item_tipo CHECK (tipo IN ('SERVICIOS', 'PRODUCTOS', 'MEDICAMENTOS', 'ALIMENTOS', 'EXAMENES'))
);

-- Item unico de catalogo: un servicio, producto, medicamento, alimento o examen
CREATE TABLE item (
    id              BIGSERIAL PRIMARY KEY,
    codigo          VARCHAR(50) NOT NULL,
    nombre          VARCHAR(255) NOT NULL,
    tipo            VARCHAR(20) NOT NULL,
    categoria_id    BIGINT,
    precio_venta    NUMERIC(12,2) NOT NULL DEFAULT 0,
    costo           NUMERIC(12,2) NOT NULL DEFAULT 0,
    impuesto_pct    NUMERIC(5,2)  NOT NULL DEFAULT 0,
    controla_stock  BOOLEAN NOT NULL DEFAULT FALSE,
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_item_codigo UNIQUE (codigo),
    CONSTRAINT fk_item_categoria FOREIGN KEY (categoria_id) REFERENCES categoria_item (id),
    CONSTRAINT ck_item_tipo CHECK (tipo IN ('SERVICIO', 'PRODUCTO', 'MEDICAMENTO', 'ALIMENTO', 'EXAMEN_LAB', 'EXAMEN_IMAGEN')),
    CONSTRAINT ck_item_precio CHECK (precio_venta >= 0 AND costo >= 0 AND impuesto_pct >= 0)
);
CREATE INDEX ix_item_categoria ON item (categoria_id);

-- Vacuna del carnetero (duracion de eficacia en dias para calcular proxima dosis)
CREATE TABLE vacuna (
    id                   BIGSERIAL PRIMARY KEY,
    nombre               VARCHAR(255) NOT NULL,
    codigo               VARCHAR(50) NOT NULL,
    especie_id           BIGINT,
    periodo_eficacia_dias INT NOT NULL,
    activo               BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_vacuna_codigo UNIQUE (codigo),
    CONSTRAINT fk_vacuna_especie FOREIGN KEY (especie_id) REFERENCES especie (id),
    CONSTRAINT ck_vacuna_periodo CHECK (periodo_eficacia_dias > 0)
);

-- ----------------------------------------------------------------------------
-- TUTORES Y PACIENTES
-- ----------------------------------------------------------------------------
CREATE TABLE tutor (
    id               BIGSERIAL PRIMARY KEY,
    tipo_documento   VARCHAR(10) NOT NULL,
    numero_documento VARCHAR(30) NOT NULL,
    nombres          VARCHAR(120) NOT NULL,
    apellidos        VARCHAR(120),
    telefono         VARCHAR(30),
    telefono_alt     VARCHAR(30),
    email            VARCHAR(255),
    direccion        VARCHAR(255),
    ciudad           VARCHAR(120),
    notas            TEXT,
    activo           BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en        TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en   TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tutor_documento UNIQUE (tipo_documento, numero_documento),
    CONSTRAINT ck_tutor_tipo_doc CHECK (tipo_documento IN ('CC', 'CE', 'NIT', 'PASAPORTE', 'OTRO'))
);
CREATE INDEX ix_tutor_apellidos ON tutor (apellidos);
CREATE INDEX ix_tutor_email ON tutor (email);

-- Paciente: historia clínica y datos de identificacion
CREATE TABLE paciente (
    id                  BIGSERIAL PRIMARY KEY,
    codigo              VARCHAR(20) NOT NULL,
    nombre              VARCHAR(120) NOT NULL,
    especie_id          BIGINT NOT NULL,
    raza_id             BIGINT,
    sexo                VARCHAR(1)  NOT NULL,
    estado_reproductivo VARCHAR(30),
    color               VARCHAR(60),
    fecha_nacimiento    DATE,
    microchip           VARCHAR(30),
    peso_actual         NUMERIC(6,2),
    esterilizado        BOOLEAN NOT NULL DEFAULT FALSE,
    fallecido           BOOLEAN NOT NULL DEFAULT FALSE,
    alertas             TEXT,
    notas               TEXT,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en           TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_paciente_codigo UNIQUE (codigo),
    CONSTRAINT uq_paciente_microchip UNIQUE (microchip),
    CONSTRAINT fk_paciente_especie FOREIGN KEY (especie_id) REFERENCES especie (id),
    CONSTRAINT fk_paciente_raza FOREIGN KEY (raza_id) REFERENCES raza (id),
    CONSTRAINT ck_paciente_sexo CHECK (sexo IN ('M', 'F'))
);
CREATE INDEX ix_paciente_especie ON paciente (especie_id);
CREATE INDEX ix_paciente_nombre ON paciente (nombre);

-- Relacion M:N paciente <-> tutor (co-tutores; al menos uno principal)
CREATE TABLE paciente_tutor (
    paciente_id  BIGINT NOT NULL,
    tutor_id     BIGINT NOT NULL,
    es_principal BOOLEAN NOT NULL DEFAULT FALSE,
    parentesco   VARCHAR(60),
    CONSTRAINT pk_paciente_tutor PRIMARY KEY (paciente_id, tutor_id),
    CONSTRAINT fk_pt_paciente FOREIGN KEY (paciente_id) REFERENCES paciente (id),
    CONSTRAINT fk_pt_tutor FOREIGN KEY (tutor_id) REFERENCES tutor (id)
);

-- Historial de peso (registro por consulta)
CREATE TABLE peso_registro (
    id             BIGSERIAL PRIMARY KEY,
    paciente_id    BIGINT NOT NULL,
    peso_kg        NUMERIC(6,2) NOT NULL,
    fecha          DATE NOT NULL,
    registrado_por BIGINT,
    CONSTRAINT fk_peso_paciente FOREIGN KEY (paciente_id) REFERENCES paciente (id),
    CONSTRAINT fk_peso_usuario FOREIGN KEY (registrado_por) REFERENCES usuario (id),
    CONSTRAINT ck_peso_positivo CHECK (peso_kg > 0)
);
CREATE INDEX ix_peso_paciente_fecha ON peso_registro (paciente_id, fecha);

CREATE TABLE alergia (
    id          BIGSERIAL PRIMARY KEY,
    paciente_id BIGINT NOT NULL,
    descripcion VARCHAR(255) NOT NULL,
    severidad   VARCHAR(10) NOT NULL DEFAULT 'BAJA',
    activo      BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_alergia_paciente FOREIGN KEY (paciente_id) REFERENCES paciente (id),
    CONSTRAINT ck_alergia_severidad CHECK (severidad IN ('BAJA', 'MEDIA', 'ALTA'))
);
CREATE INDEX ix_alergia_paciente ON alergia (paciente_id);

-- ----------------------------------------------------------------------------
-- AGENDA
-- ----------------------------------------------------------------------------
CREATE TABLE veterinario (
    id                  BIGSERIAL PRIMARY KEY,
    usuario_id          BIGINT,
    nombres             VARCHAR(120) NOT NULL,
    apellidos           VARCHAR(120),
    numero_profesional  VARCHAR(50),
    telefono            VARCHAR(30),
    email               VARCHAR(255),
    especialidad        VARCHAR(120),
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en           TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_veterinario_usuario UNIQUE (usuario_id),
    CONSTRAINT fk_veterinario_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);

CREATE TABLE cita (
    id                  BIGSERIAL PRIMARY KEY,
    codigo              VARCHAR(20) NOT NULL,
    paciente_id         BIGINT NOT NULL,
    veterinario_id      BIGINT NOT NULL,
    tipo_cita_id        BIGINT,
    fecha_hora_inicio   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    fecha_hora_fin      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    estado              VARCHAR(20) NOT NULL DEFAULT 'AGENDADA',
    motivo              TEXT,
    notas               TEXT,
    creado_en           TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cita_codigo UNIQUE (codigo),
    CONSTRAINT fk_cita_paciente FOREIGN KEY (paciente_id) REFERENCES paciente (id),
    CONSTRAINT fk_cita_veterinario FOREIGN KEY (veterinario_id) REFERENCES usuario (id),
    CONSTRAINT fk_cita_tipo FOREIGN KEY (tipo_cita_id) REFERENCES tipo_cita (id),
    -- Red de seguridad ante doble reserva en el mismo instante exacto.
    CONSTRAINT uq_cita_vet_inicio UNIQUE (veterinario_id, fecha_hora_inicio),
    CONSTRAINT ck_cita_estado CHECK (estado IN ('AGENDADA', 'CONFIRMADA', 'EN_SALA', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO'))
);
CREATE INDEX ix_cita_vet_fecha ON cita (veterinario_id, fecha_hora_inicio);
CREATE INDEX ix_cita_paciente_fecha ON cita (paciente_id, fecha_hora_inicio);
CREATE INDEX ix_cita_estado ON cita (estado);

-- Bloques de indisponibilidad (vacaciones, capacitacion, mantenimiento)
CREATE TABLE bloqueo_agenda (
    id              BIGSERIAL PRIMARY KEY,
    veterinario_id  BIGINT NOT NULL,
    inicio          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    fin             TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    motivo          VARCHAR(255),
    CONSTRAINT fk_bloqueo_veterinario FOREIGN KEY (veterinario_id) REFERENCES usuario (id),
    CONSTRAINT ck_bloqueo_rango CHECK (fin > inicio)
);
CREATE INDEX ix_bloqueo_vet_fecha ON bloqueo_agenda (veterinario_id, inicio);

-- ----------------------------------------------------------------------------
-- HISTORIA CLINICA (SOAP)
-- ----------------------------------------------------------------------------
CREATE TABLE consulta (
    id                    BIGSERIAL PRIMARY KEY,
    cita_id               BIGINT,
    paciente_id           BIGINT NOT NULL,
    veterinario_id        BIGINT NOT NULL,
    fecha_hora            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    motivo_consulta       TEXT,
    anamnesis             TEXT,
    subjetivo             TEXT,
    objetivo              TEXT,
    evaluacion            TEXT,
    plan                  TEXT,
    diagnostico_principal TEXT,
    codigo_cie            VARCHAR(20),
    pronostico            TEXT,
    estado                VARCHAR(10) NOT NULL DEFAULT 'ABIERTA',
    creado_en             TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en        TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_consulta_cita UNIQUE (cita_id),
    CONSTRAINT fk_consulta_cita FOREIGN KEY (cita_id) REFERENCES cita (id),
    CONSTRAINT fk_consulta_paciente FOREIGN KEY (paciente_id) REFERENCES paciente (id),
    CONSTRAINT fk_consulta_veterinario FOREIGN KEY (veterinario_id) REFERENCES usuario (id),
    CONSTRAINT ck_consulta_estado CHECK (estado IN ('ABIERTA', 'CERRADA'))
);
CREATE INDEX ix_consulta_paciente_fecha ON consulta (paciente_id, fecha_hora);

-- Signos vitales tomados en la consulta
CREATE TABLE signo_vital (
    id                        BIGSERIAL PRIMARY KEY,
    consulta_id               BIGINT NOT NULL,
    temperatura               NUMERIC(4,1),
    frecuencia_cardiaca       INT,
    frecuencia_respiratoria   INT,
    peso_kg                   NUMERIC(6,2),
    condicion_corporal        VARCHAR(30),
    mucosas                   VARCHAR(60),
    tiempo_llenado_capilar    NUMERIC(3,1),
    notas                     TEXT,
    CONSTRAINT fk_sv_consulta FOREIGN KEY (consulta_id) REFERENCES consulta (id)
);

-- Diagnostico(s): presuntivo / diferencial / definitivo
CREATE TABLE diagnostico (
    id          BIGSERIAL PRIMARY KEY,
    consulta_id BIGINT NOT NULL,
    descripcion TEXT NOT NULL,
    codigo_cie  VARCHAR(20),
    tipo        VARCHAR(20) NOT NULL,
    orden       INT NOT NULL DEFAULT 1,
    CONSTRAINT fk_dx_consulta FOREIGN KEY (consulta_id) REFERENCES consulta (id),
    CONSTRAINT ck_dx_tipo CHECK (tipo IN ('PRESUNTIVO', 'DIFERENCIAL', 'DEFINITIVO'))
);
CREATE INDEX ix_dx_consulta ON diagnostico (consulta_id);

-- Adjuntos (imagenes, radiografias, PDF de laboratorio)
CREATE TABLE adjunto (
    id             BIGSERIAL PRIMARY KEY,
    paciente_id    BIGINT NOT NULL,
    consulta_id    BIGINT,
    nombre_original VARCHAR(255) NOT NULL,
    tipo           VARCHAR(50),
    ruta           VARCHAR(500) NOT NULL,
    content_type   VARCHAR(100),
    tamano_bytes   BIGINT,
    subido_por_id  BIGINT,
    subido_en      TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_adjunto_paciente FOREIGN KEY (paciente_id) REFERENCES paciente (id),
    CONSTRAINT fk_adjunto_consulta FOREIGN KEY (consulta_id) REFERENCES consulta (id),
    CONSTRAINT fk_adjunto_usuario FOREIGN KEY (subido_por_id) REFERENCES usuario (id)
);

-- Recetas / prescripciones
CREATE TABLE receta (
    id                    BIGSERIAL PRIMARY KEY,
    consulta_id           BIGINT NOT NULL,
    paciente_id           BIGINT NOT NULL,
    veterinario_id        BIGINT NOT NULL,
    fecha                 DATE NOT NULL,
    indicaciones_generales TEXT,
    estado                VARCHAR(10) NOT NULL DEFAULT 'ACTIVA',
    creado_en             TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_receta_consulta FOREIGN KEY (consulta_id) REFERENCES consulta (id),
    CONSTRAINT fk_receta_paciente FOREIGN KEY (paciente_id) REFERENCES paciente (id),
    CONSTRAINT fk_receta_veterinario FOREIGN KEY (veterinario_id) REFERENCES usuario (id),
    CONSTRAINT ck_receta_estado CHECK (estado IN ('ACTIVA', 'CUMPLIDA', 'CANCELADA'))
);

CREATE TABLE receta_item (
    id           BIGSERIAL PRIMARY KEY,
    receta_id    BIGINT NOT NULL,
    item_id      BIGINT,
    medicamento  VARCHAR(255) NOT NULL,
    dosis        VARCHAR(100),
    via          VARCHAR(60),
    frecuencia   VARCHAR(100),
    duracion     VARCHAR(100),
    cantidad     INT,
    indicaciones TEXT,
    CONSTRAINT fk_ri_receta FOREIGN KEY (receta_id) REFERENCES receta (id),
    CONSTRAINT fk_ri_item FOREIGN KEY (item_id) REFERENCES item (id)
);

-- ----------------------------------------------------------------------------
-- VACUNACION (carnetero del paciente)
-- ----------------------------------------------------------------------------
CREATE TABLE inmunizacion (
    id               BIGSERIAL PRIMARY KEY,
    paciente_id      BIGINT NOT NULL,
    vacuna_id        BIGINT NOT NULL,
    consulta_id      BIGINT,
    fecha_aplicacion DATE NOT NULL,
    fecha_proxima_dosis DATE,
    lote             VARCHAR(60),
    laboratorio      VARCHAR(120),
    veterinario_id   BIGINT,
    observaciones    TEXT,
    CONSTRAINT fk_inm_paciente FOREIGN KEY (paciente_id) REFERENCES paciente (id),
    CONSTRAINT fk_inm_vacuna FOREIGN KEY (vacuna_id) REFERENCES vacuna (id),
    CONSTRAINT fk_inm_consulta FOREIGN KEY (consulta_id) REFERENCES consulta (id),
    CONSTRAINT fk_inm_veterinario FOREIGN KEY (veterinario_id) REFERENCES usuario (id)
);
CREATE INDEX ix_inm_paciente ON inmunizacion (paciente_id);
CREATE INDEX ix_inm_proxima_dosis ON inmunizacion (fecha_proxima_dosis);

-- ----------------------------------------------------------------------------
-- INVENTARIO Y FARMACIA
-- ----------------------------------------------------------------------------
CREATE TABLE bodega (
    id     BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    tipo   VARCHAR(20) NOT NULL DEFAULT 'PRINCIPAL',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_bodega_nombre UNIQUE (nombre),
    CONSTRAINT ck_bodega_tipo CHECK (tipo IN ('PRINCIPAL', 'FARMACIA', 'QUIROFANO', 'LABORATORIO', 'VEHICULO'))
);

CREATE TABLE lote (
    id                BIGSERIAL PRIMARY KEY,
    item_id           BIGINT NOT NULL,
    bodega_id         BIGINT NOT NULL,
    numero_lote       VARCHAR(60),
    fecha_vencimiento DATE,
    cantidad_inicial  NUMERIC(12,3) NOT NULL,
    cantidad_actual   NUMERIC(12,3) NOT NULL,
    costo_unitario    NUMERIC(12,2),
    creado_en         TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lote_item FOREIGN KEY (item_id) REFERENCES item (id),
    CONSTRAINT fk_lote_bodega FOREIGN KEY (bodega_id) REFERENCES bodega (id),
    CONSTRAINT uq_lote_item_bodega UNIQUE (item_id, bodega_id, numero_lote),
    CONSTRAINT ck_lote_cantidad CHECK (cantidad_inicial >= 0 AND cantidad_actual >= 0)
);
CREATE INDEX ix_lote_item ON lote (item_id);
CREATE INDEX ix_lote_vencimiento ON lote (fecha_vencimiento);

-- Kardex: toda salida de stock queda trazada (cita o consulta como referencia)
CREATE TABLE movimiento_stock (
    id          BIGSERIAL PRIMARY KEY,
    lote_id     BIGINT NOT NULL,
    tipo        VARCHAR(20) NOT NULL,
    cantidad    NUMERIC(12,3) NOT NULL,
    motivo      TEXT,
    referencia  VARCHAR(100),
    usuario_id  BIGINT,
    ocurrido_en TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ms_lote FOREIGN KEY (lote_id) REFERENCES lote (id),
    CONSTRAINT fk_ms_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_ms_tipo CHECK (tipo IN ('ENTRADA', 'SALIDA', 'AJUSTE', 'TRASLADO', 'VENCIMIENTO', 'DEVOLUCION'))
);
CREATE INDEX ix_ms_lote ON movimiento_stock (lote_id);
CREATE INDEX ix_ms_fecha ON movimiento_stock (ocurrido_en);

-- ----------------------------------------------------------------------------
-- FACTURACION, CAJA Y PRESUPUESTOS
-- ----------------------------------------------------------------------------
CREATE TABLE factura (
    id            BIGSERIAL PRIMARY KEY,
    numero        VARCHAR(20) NOT NULL,
    tutor_id      BIGINT NOT NULL,
    paciente_id   BIGINT,
    fecha         TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subtotal      NUMERIC(12,2) NOT NULL DEFAULT 0,
    descuento     NUMERIC(12,2) NOT NULL DEFAULT 0,
    impuestos     NUMERIC(12,2) NOT NULL DEFAULT 0,
    total         NUMERIC(12,2) NOT NULL DEFAULT 0,
    saldo         NUMERIC(12,2) NOT NULL DEFAULT 0,
    estado        VARCHAR(10) NOT NULL DEFAULT 'EMITIDA',
    notas         TEXT,
    creado_en     TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_factura_numero UNIQUE (numero),
    CONSTRAINT fk_factura_tutor FOREIGN KEY (tutor_id) REFERENCES tutor (id),
    CONSTRAINT fk_factura_paciente FOREIGN KEY (paciente_id) REFERENCES paciente (id),
    CONSTRAINT ck_factura_estado CHECK (estado IN ('BORRADOR', 'EMITIDA', 'PARCIAL', 'PAGADA', 'ANULADA'))
);
CREATE INDEX ix_factura_tutor ON factura (tutor_id);
CREATE INDEX ix_factura_fecha ON factura (fecha);

CREATE TABLE factura_item (
    id             BIGSERIAL PRIMARY KEY,
    factura_id     BIGINT NOT NULL,
    consulta_id    BIGINT,
    item_id        BIGINT,
    descripcion    VARCHAR(255) NOT NULL,
    cantidad       NUMERIC(10,2) NOT NULL DEFAULT 1,
    precio_unitario NUMERIC(12,2) NOT NULL DEFAULT 0,
    impuesto_pct   NUMERIC(5,2) NOT NULL DEFAULT 0,
    descuento      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total          NUMERIC(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_fi_factura FOREIGN KEY (factura_id) REFERENCES factura (id),
    CONSTRAINT fk_fi_consulta FOREIGN KEY (consulta_id) REFERENCES consulta (id),
    CONSTRAINT fk_fi_item FOREIGN KEY (item_id) REFERENCES item (id),
    CONSTRAINT ck_fi_cantidad CHECK (cantidad > 0)
);
CREATE INDEX ix_fi_factura ON factura_item (factura_id);

CREATE TABLE pago (
    id         BIGSERIAL PRIMARY KEY,
    factura_id BIGINT NOT NULL,
    fecha      TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    monto      NUMERIC(12,2) NOT NULL,
    metodo     VARCHAR(20) NOT NULL,
    referencia VARCHAR(100),
    usuario_id BIGINT,
    creado_en  TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pago_factura FOREIGN KEY (factura_id) REFERENCES factura (id),
    CONSTRAINT fk_pago_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_pago_monto CHECK (monto > 0),
    CONSTRAINT ck_pago_metodo CHECK (metodo IN ('EFECTIVO', 'TARJETA', 'TRANSFERENCIA', 'NEQUI', 'DAVIPLATA', 'OTRO'))
);
CREATE INDEX ix_pago_factura ON pago (factura_id);

CREATE TABLE presupuesto (
    id           BIGSERIAL PRIMARY KEY,
    codigo       VARCHAR(20) NOT NULL,
    tutor_id     BIGINT NOT NULL,
    paciente_id  BIGINT,
    fecha        TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado       VARCHAR(10) NOT NULL DEFAULT 'BORRADOR',
    total        NUMERIC(12,2) NOT NULL DEFAULT 0,
    notas        TEXT,
    creado_en    TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_presupuesto_codigo UNIQUE (codigo),
    CONSTRAINT fk_presupuesto_tutor FOREIGN KEY (tutor_id) REFERENCES tutor (id),
    CONSTRAINT fk_presupuesto_paciente FOREIGN KEY (paciente_id) REFERENCES paciente (id),
    CONSTRAINT ck_presupuesto_estado CHECK (estado IN ('BORRADOR', 'ENVIADO', 'ACEPTADO', 'RECHAZADO', 'VENCIDO'))
);

CREATE TABLE presupuesto_item (
    id              BIGSERIAL PRIMARY KEY,
    presupuesto_id  BIGINT NOT NULL,
    item_id         BIGINT,
    descripcion     VARCHAR(255) NOT NULL,
    cantidad        NUMERIC(10,2) NOT NULL DEFAULT 1,
    precio_unitario NUMERIC(12,2) NOT NULL DEFAULT 0,
    impuesto_pct    NUMERIC(5,2) NOT NULL DEFAULT 0,
    descuento       NUMERIC(12,2) NOT NULL DEFAULT 0,
    total           NUMERIC(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_pi_presupuesto FOREIGN KEY (presupuesto_id) REFERENCES presupuesto (id),
    CONSTRAINT fk_pi_item FOREIGN KEY (item_id) REFERENCES item (id),
    CONSTRAINT ck_pi_cantidad CHECK (cantidad > 0)
);
CREATE INDEX ix_pi_presupuesto ON presupuesto_item (presupuesto_id);