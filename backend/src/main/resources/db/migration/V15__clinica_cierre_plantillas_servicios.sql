-- ============================================================================
-- V15 (PROPUESTA) - Clinica: cierre inmutable + addendum, plantillas SOAP, procedimientos,
-- servicios con precio (item), signos vitales extendidos, catalogo de diagnosticos.
-- Nota: la tabla consulta real (V1+V4+V6+V7) tiene columnas heredadas duplicadas
-- (fecha_hora/fecha, motivo_consulta/motivo, etc.); aqui no se tocan.
-- ============================================================================

-- ---------------------------------------------------------------- consulta: cierre y firma
ALTER TABLE consulta ADD COLUMN IF NOT EXISTS tipo_consulta     VARCHAR(20) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE consulta ADD COLUMN IF NOT EXISTS plantilla_id      BIGINT;
ALTER TABLE consulta ADD COLUMN IF NOT EXISTS cerrada_en        TIMESTAMPTZ;
ALTER TABLE consulta ADD COLUMN IF NOT EXISTS cerrada_por       BIGINT REFERENCES usuario (id);
ALTER TABLE consulta ADD COLUMN IF NOT EXISTS firma_hash        VARCHAR(64);
ALTER TABLE consulta ADD COLUMN IF NOT EXISTS cierre_automatico BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE consulta ADD COLUMN IF NOT EXISTS generado_por_ia   BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE consulta ADD COLUMN IF NOT EXISTS triaje            VARCHAR(10);
ALTER TABLE consulta ADD COLUMN IF NOT EXISTS proxima_revision  DATE;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_consulta_tipo') THEN
    ALTER TABLE consulta ADD CONSTRAINT ck_consulta_tipo
      CHECK (tipo_consulta IN ('GENERAL','CONTROL','URGENCIA','VACUNACION','PREQUIRURGICA','POSTQUIRURGICA','TELEMEDICINA','DOMICILIO','HOSPITALIZACION'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_consulta_triaje') THEN
    ALTER TABLE consulta ADD CONSTRAINT ck_consulta_triaje CHECK (triaje IS NULL OR triaje IN ('ROJO','NARANJA','AMARILLO','VERDE','AZUL'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_consulta_cierre') THEN
    ALTER TABLE consulta ADD CONSTRAINT ck_consulta_cierre
      CHECK (estado <> 'CERRADA' OR cerrada_en IS NOT NULL) NOT VALID;  -- NOT VALID: no exige filas historicas
  END IF;
END $$;

-- Inmutabilidad: una consulta CERRADA no se modifica ni se borra; las correcciones van en consulta_addendum.
-- Transicion permitida: ABIERTA -> CERRADA (el UPDATE que cierra). Sin reapertura.
CREATE OR REPLACE FUNCTION fn_consulta_inmutable() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'La consulta % no se puede eliminar fisicamente (use activo=false si esta ABIERTA)', OLD.id USING ERRCODE = 'P0001';
  END IF;
  IF OLD.estado = 'CERRADA' THEN
    RAISE EXCEPTION 'La consulta % esta CERRADA y es inmutable; registre un addendum', OLD.id USING ERRCODE = 'P0001';
  END IF;
  RETURN NEW;
END
$fn$;
CREATE OR REPLACE TRIGGER trg_consulta_inmutable
  BEFORE UPDATE OR DELETE ON consulta FOR EACH ROW EXECUTE FUNCTION fn_consulta_inmutable();

-- Hijos directos de consulta: no se pueden insertar/modificar/borrar si la consulta esta CERRADA
CREATE OR REPLACE FUNCTION fn_hijo_consulta_inmutable() RETURNS trigger LANGUAGE plpgsql AS $fn$
DECLARE v_consulta BIGINT; v_estado VARCHAR;
BEGIN
  v_consulta := CASE WHEN TG_OP = 'DELETE' THEN OLD.consulta_id ELSE NEW.consulta_id END;
  SELECT estado INTO v_estado FROM consulta WHERE id = v_consulta;
  IF v_estado = 'CERRADA' THEN
    RAISE EXCEPTION 'La consulta % esta CERRADA; % en % no permitido (use addendum)', v_consulta, TG_OP, TG_TABLE_NAME USING ERRCODE = 'P0001';
  END IF;
  RETURN COALESCE(NEW, OLD);
END
$fn$;
CREATE OR REPLACE TRIGGER trg_sv_inmutable   BEFORE INSERT OR UPDATE OR DELETE ON signo_vital         FOR EACH ROW EXECUTE FUNCTION fn_hijo_consulta_inmutable();
CREATE OR REPLACE TRIGGER trg_dx_inmutable   BEFORE INSERT OR UPDATE OR DELETE ON diagnostico         FOR EACH ROW EXECUTE FUNCTION fn_hijo_consulta_inmutable();
CREATE OR REPLACE TRIGGER trg_cm_inmutable   BEFORE INSERT OR UPDATE OR DELETE ON consulta_medicamento FOR EACH ROW EXECUTE FUNCTION fn_hijo_consulta_inmutable();
CREATE OR REPLACE TRIGGER trg_cv_inmutable   BEFORE INSERT OR UPDATE OR DELETE ON consulta_vacuna     FOR EACH ROW EXECUTE FUNCTION fn_hijo_consulta_inmutable();

CREATE TABLE IF NOT EXISTS consulta_addendum (
    id          BIGSERIAL PRIMARY KEY,
    consulta_id BIGINT NOT NULL REFERENCES consulta (id),
    autor_id    BIGINT NOT NULL REFERENCES usuario (id),
    tipo        VARCHAR(15) NOT NULL DEFAULT 'ACLARACION',
    texto       TEXT NOT NULL,
    generado_por_ia BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_addendum_tipo CHECK (tipo IN ('ACLARACION','CORRECCION','RESULTADO_TARDIO','SEGUIMIENTO'))
);
CREATE INDEX IF NOT EXISTS ix_addendum_consulta ON consulta_addendum (consulta_id, creado_en);
COMMENT ON TABLE consulta_addendum IS 'Notas posteriores al cierre. Append-only (trigger bloquea UPDATE/DELETE).';

CREATE OR REPLACE FUNCTION fn_append_only() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  RAISE EXCEPTION 'La tabla % es append-only (% no permitido)', TG_TABLE_NAME, TG_OP USING ERRCODE = 'P0001';
END
$fn$;
CREATE OR REPLACE TRIGGER trg_addendum_append_only
  BEFORE UPDATE OR DELETE ON consulta_addendum FOR EACH ROW EXECUTE FUNCTION fn_append_only();

-- ---------------------------------------------------------------- plantillas SOAP
CREATE TABLE IF NOT EXISTS plantilla_soap (
    id            BIGSERIAL PRIMARY KEY,
    nombre        VARCHAR(150) NOT NULL,
    especie_id    BIGINT REFERENCES especie (id),
    tipo_consulta VARCHAR(20),
    subjetivo     TEXT,
    objetivo      TEXT,
    evaluacion    TEXT,
    plan          TEXT,
    activo        BOOLEAN NOT NULL DEFAULT TRUE,
    creado_por    BIGINT REFERENCES usuario (id),
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_plantilla_soap_nombre UNIQUE (nombre)
);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_consulta_plantilla') THEN
    ALTER TABLE consulta ADD CONSTRAINT fk_consulta_plantilla FOREIGN KEY (plantilla_id) REFERENCES plantilla_soap (id);
  END IF;
END $$;

INSERT INTO plantilla_soap (nombre, tipo_consulta, subjetivo, objetivo, evaluacion, plan) VALUES
 ('Consulta general', 'GENERAL',
  E'Motivo de consulta:\nHistoria / anamnesis: apetito, sed, vomito, diarrea, tos, miccion, defecacion, actividad.\nVacunas y desparasitacion al dia: \nAlimentacion: ',
  E'Estado mental / actitud:\nCondicion corporal (1-9):\nMucosas / TLLC:\nHidratacion:\nAuscultacion cardiopulmonar:\nPalpacion abdominal:\nPiel y pelaje:\nOjos / oidos / boca:\nLinfonodulos:\nSistema musculoesqueletico / neurologico:',
  E'Diagnostico presuntivo:\nDiagnosticos diferenciales:\nPronostico:',
  E'Examenes complementarios:\nTratamiento:\nIndicaciones al tutor:\nControl en:'),
 ('Vacunacion / control preventivo', 'VACUNACION',
  E'Antecedentes de vacunacion y reacciones previas:\nContacto con otros animales / viajes:',
  E'Peso:\nExamen fisico general sin hallazgos de enfermedad activa: ',
  E'Apto para vacunar: SI / NO',
  E'Vacuna(s) aplicada(s), lote y sitio:\nProxima dosis:\nDesparasitacion:'),
 ('Control postquirurgico', 'POSTQUIRURGICA',
  E'Evolucion segun el tutor (apetito, dolor, actividad, lamido de herida):',
  E'Herida quirurgica (aspecto, puntos, secrecion):\nDolor (escala):\nTemperatura:',
  E'Evolucion: favorable / desfavorable',
  E'Medicacion:\nCuidados de herida:\nRetiro de puntos:'),
 ('Urgencia', 'URGENCIA',
  E'Motivo de urgencia y tiempo de evolucion:\nToxicos / trauma / medicacion previa:',
  E'ABC (via aerea, respiracion, circulacion):\nTriaje:\nSignos vitales:\nDolor:',
  E'Problemas identificados:\nPrioridad:',
  E'Estabilizacion:\nExamenes de emergencia:\nHospitalizar: SI / NO')
ON CONFLICT (nombre) DO NOTHING;

-- ---------------------------------------------------------------- signos vitales extendidos
ALTER TABLE signo_vital ADD COLUMN IF NOT EXISTS escala_dolor        SMALLINT;
ALTER TABLE signo_vital ADD COLUMN IF NOT EXISTS hidratacion_pct     NUMERIC(4,1);
ALTER TABLE signo_vital ADD COLUMN IF NOT EXISTS presion_sistolica   INT;
ALTER TABLE signo_vital ADD COLUMN IF NOT EXISTS spo2                NUMERIC(4,1);
ALTER TABLE signo_vital ADD COLUMN IF NOT EXISTS glucosa_mg_dl       NUMERIC(6,1);
ALTER TABLE signo_vital ADD COLUMN IF NOT EXISTS puntaje_condicion_corporal SMALLINT;
ALTER TABLE signo_vital ADD COLUMN IF NOT EXISTS registrado_en       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE signo_vital ADD COLUMN IF NOT EXISTS registrado_por      BIGINT REFERENCES usuario (id);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_sv_rangos') THEN
    ALTER TABLE signo_vital ADD CONSTRAINT ck_sv_rangos CHECK (
      (escala_dolor IS NULL OR escala_dolor BETWEEN 0 AND 10) AND
      (puntaje_condicion_corporal IS NULL OR puntaje_condicion_corporal BETWEEN 1 AND 9) AND
      (spo2 IS NULL OR spo2 BETWEEN 0 AND 100));
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_sv_consulta ON signo_vital (consulta_id);

-- ---------------------------------------------------------------- diagnosticos
ALTER TABLE diagnostico ADD COLUMN IF NOT EXISTS codigo_venom   VARCHAR(30);
ALTER TABLE diagnostico ADD COLUMN IF NOT EXISTS catalogo_id    BIGINT;
ALTER TABLE diagnostico ADD COLUMN IF NOT EXISTS lateralidad    VARCHAR(10);
ALTER TABLE diagnostico ADD COLUMN IF NOT EXISTS resuelto       BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS catalogo_diagnostico (
    id          BIGSERIAL PRIMARY KEY,
    codigo      VARCHAR(30)  NOT NULL,
    nombre      VARCHAR(255) NOT NULL,
    sistema     VARCHAR(30)  NOT NULL DEFAULT 'INTERNO',
    categoria   VARCHAR(60),
    activo      BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_cat_dx UNIQUE (sistema, codigo)
);
COMMENT ON TABLE catalogo_diagnostico IS 'Catalogo propio de diagnosticos frecuentes. Codigos VeNom/SNOMED-CT Vet requieren licencia: cargar en sistema=VENOM si se obtiene.';
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dx_catalogo') THEN
    ALTER TABLE diagnostico ADD CONSTRAINT fk_dx_catalogo FOREIGN KEY (catalogo_id) REFERENCES catalogo_diagnostico (id);
  END IF;
END $$;

INSERT INTO catalogo_diagnostico (codigo, nombre, categoria) VALUES
 ('DX-GAS-001','Gastroenteritis aguda','Digestivo'),
 ('DX-GAS-002','Parvovirosis canina','Infeccioso'),
 ('DX-INF-001','Moquillo canino','Infeccioso'),
 ('DX-INF-002','Panleucopenia felina','Infeccioso'),
 ('DX-INF-003','Complejo respiratorio felino','Infeccioso'),
 ('DX-INF-004','Tos de las perreras (traqueobronquitis infecciosa)','Infeccioso'),
 ('DX-PAR-001','Parasitosis intestinal','Parasitario'),
 ('DX-PAR-002','Ectoparasitosis (pulgas / garrapatas)','Parasitario'),
 ('DX-PAR-003','Hemoparasitosis (erliquiosis / babesiosis / anaplasmosis)','Parasitario'),
 ('DX-DER-001','Dermatitis alergica','Dermatologico'),
 ('DX-DER-002','Piodermitis','Dermatologico'),
 ('DX-DER-003','Otitis externa','Dermatologico'),
 ('DX-URI-001','Enfermedad renal cronica','Urinario'),
 ('DX-URI-002','Infeccion del tracto urinario','Urinario'),
 ('DX-URI-003','Enfermedad del tracto urinario inferior felino','Urinario'),
 ('DX-ODO-001','Enfermedad periodontal','Odontologico'),
 ('DX-END-001','Diabetes mellitus','Endocrino'),
 ('DX-END-002','Hipotiroidismo','Endocrino'),
 ('DX-CAR-001','Enfermedad valvular mitral','Cardiovascular'),
 ('DX-MSK-001','Displasia de cadera','Musculoesqueletico'),
 ('DX-MSK-002','Osteoartritis','Musculoesqueletico'),
 ('DX-TRA-001','Trauma / herida','Trauma'),
 ('DX-ONC-001','Neoplasia (pendiente de tipificar)','Oncologico'),
 ('DX-NUT-001','Obesidad','Nutricional'),
 ('DX-PRE-001','Control preventivo / paciente sano','Preventivo')
ON CONFLICT (sistema, codigo) DO NOTHING;

-- ---------------------------------------------------------------- procedimientos y servicios (item)
ALTER TABLE item ADD COLUMN IF NOT EXISTS duracion_minutos     INT;
ALTER TABLE item ADD COLUMN IF NOT EXISTS requiere_veterinario BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE item ADD COLUMN IF NOT EXISTS especie_id           BIGINT REFERENCES especie (id);
ALTER TABLE item ADD COLUMN IF NOT EXISTS codigo_venom         VARCHAR(30);
ALTER TABLE item ADD COLUMN IF NOT EXISTS descripcion          TEXT;
ALTER TABLE item ADD COLUMN IF NOT EXISTS precio_por_peso      BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS item_precio_sede (
    item_id      BIGINT NOT NULL REFERENCES item (id),
    sede_id      BIGINT NOT NULL REFERENCES sede (id),
    precio_venta NUMERIC(12,2) NOT NULL,
    vigente_desde DATE NOT NULL DEFAULT CURRENT_DATE,
    PRIMARY KEY (item_id, sede_id, vigente_desde),
    CONSTRAINT ck_ips_precio CHECK (precio_venta >= 0)
);
COMMENT ON TABLE item_precio_sede IS 'Precio por sede con vigencia; si no hay fila se usa item.precio_venta.';

CREATE TABLE IF NOT EXISTS consulta_procedimiento (
    id            BIGSERIAL PRIMARY KEY,
    consulta_id   BIGINT NOT NULL REFERENCES consulta (id),
    item_id       BIGINT REFERENCES item (id),
    descripcion   VARCHAR(255) NOT NULL,
    cantidad      NUMERIC(10,2) NOT NULL DEFAULT 1,
    precio_unitario NUMERIC(12,2),
    realizado_por BIGINT REFERENCES usuario (id),
    realizado_en  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notas         TEXT,
    CONSTRAINT ck_cp_cantidad CHECK (cantidad > 0)
);
CREATE INDEX IF NOT EXISTS ix_cp_consulta ON consulta_procedimiento (consulta_id);
CREATE OR REPLACE TRIGGER trg_cp_inmutable BEFORE INSERT OR UPDATE OR DELETE ON consulta_procedimiento
  FOR EACH ROW EXECUTE FUNCTION fn_hijo_consulta_inmutable();

ALTER TABLE receta ADD COLUMN IF NOT EXISTS valida_hasta   DATE;
ALTER TABLE receta ADD COLUMN IF NOT EXISTS es_controlada  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE receta_item ADD COLUMN IF NOT EXISTS orden INT;

-- Semilla: categorias y servicios tipicos (precios COP de ejemplo, ajustar por clinica)
INSERT INTO categoria_item (nombre, tipo) VALUES
 ('Consultas', 'SERVICIOS'), ('Procedimientos', 'SERVICIOS'), ('Cirugias', 'SERVICIOS'),
 ('Hospitalizacion', 'SERVICIOS'), ('Peluqueria y bano', 'SERVICIOS'), ('Guarderia y hotel', 'SERVICIOS'),
 ('Laboratorio', 'EXAMENES'), ('Imagenes diagnosticas', 'EXAMENES'),
 ('Vacunas', 'MEDICAMENTOS'), ('Antiparasitarios', 'MEDICAMENTOS'), ('Medicamentos', 'MEDICAMENTOS'),
 ('Alimentos', 'ALIMENTOS'), ('Accesorios', 'PRODUCTOS')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO item (codigo, nombre, tipo, categoria_id, precio_venta, duracion_minutos, requiere_veterinario)
SELECT v.codigo, v.nombre, v.tipo, (SELECT id FROM categoria_item WHERE nombre = v.cat), v.precio, v.dur, v.vet
FROM (VALUES
 ('SRV-CONS-GEN', 'Consulta general',               'SERVICIO',      'Consultas',            70000, 30, TRUE),
 ('SRV-CONS-CTL', 'Control / seguimiento',          'SERVICIO',      'Consultas',            40000, 20, TRUE),
 ('SRV-CONS-URG', 'Consulta de urgencia',           'SERVICIO',      'Consultas',           120000, 45, TRUE),
 ('SRV-CONS-NOC', 'Recargo nocturno / festivo',     'SERVICIO',      'Consultas',            60000, NULL, FALSE),
 ('SRV-CONS-TEL', 'Teleconsulta',                   'SERVICIO',      'Consultas',            50000, 20, TRUE),
 ('SRV-CONS-DOM', 'Consulta a domicilio',           'SERVICIO',      'Consultas',           130000, 60, TRUE),
 ('SRV-PRC-VAC',  'Aplicacion de vacuna (acto)',    'SERVICIO',      'Procedimientos',       15000, 10, FALSE),
 ('SRV-PRC-DES',  'Desparasitacion (acto)',         'SERVICIO',      'Procedimientos',       10000, 10, FALSE),
 ('SRV-PRC-CHIP', 'Implantacion de microchip',      'SERVICIO',      'Procedimientos',       90000, 15, TRUE),
 ('SRV-PRC-CURA', 'Curacion simple',                'SERVICIO',      'Procedimientos',       35000, 20, FALSE),
 ('SRV-PRC-UNAS', 'Corte de unas',                  'SERVICIO',      'Procedimientos',       15000, 10, FALSE),
 ('SRV-PRC-GLAN', 'Vaciado de glandulas anales',    'SERVICIO',      'Procedimientos',       25000, 10, FALSE),
 ('SRV-PRC-PROF', 'Profilaxis dental con ultrasonido','SERVICIO',    'Procedimientos',      220000, 60, TRUE),
 ('SRV-PRC-FLUI', 'Terapia de fluidos (por dia)',   'SERVICIO',      'Procedimientos',       60000, NULL, TRUE),
 ('SRV-PRC-EUTA', 'Eutanasia',                      'SERVICIO',      'Procedimientos',      150000, 45, TRUE),
 ('SRV-PRC-CERT', 'Certificado de salud / viaje',   'SERVICIO',      'Procedimientos',       60000, 20, TRUE),
 ('SRV-CIR-CAST-M','Castracion canino/felino macho','SERVICIO',      'Cirugias',            250000, 60, TRUE),
 ('SRV-CIR-ESTE-H','Ovariohisterectomia (hembra)',  'SERVICIO',      'Cirugias',            380000, 90, TRUE),
 ('SRV-CIR-TUM',  'Extirpacion de masa / tumor',    'SERVICIO',      'Cirugias',            450000, 90, TRUE),
 ('SRV-CIR-CESA', 'Cesarea',                        'SERVICIO',      'Cirugias',            700000, 120, TRUE),
 ('SRV-HOS-DIA',  'Hospitalizacion (dia)',          'SERVICIO',      'Hospitalizacion',     120000, NULL, FALSE),
 ('SRV-HOS-UCI',  'Hospitalizacion intensiva (dia)','SERVICIO',      'Hospitalizacion',     250000, NULL, FALSE),
 ('SRV-PEL-BAN-P','Bano - raza pequena',            'SERVICIO',      'Peluqueria y bano',    45000, 60, FALSE),
 ('SRV-PEL-BAN-M','Bano - raza mediana',            'SERVICIO',      'Peluqueria y bano',    60000, 75, FALSE),
 ('SRV-PEL-BAN-G','Bano - raza grande',             'SERVICIO',      'Peluqueria y bano',    80000, 90, FALSE),
 ('SRV-PEL-CORT', 'Corte de pelo / estetica',       'SERVICIO',      'Peluqueria y bano',    70000, 90, FALSE),
 ('SRV-GUA-DIA',  'Guarderia (dia)',                'SERVICIO',      'Guarderia y hotel',    50000, NULL, FALSE),
 ('SRV-GUA-HOT',  'Hotel (noche)',                  'SERVICIO',      'Guarderia y hotel',    70000, NULL, FALSE),
 ('LAB-HEMO',     'Hemograma completo',             'EXAMEN_LAB',    'Laboratorio',          45000, NULL, FALSE),
 ('LAB-QUIM',     'Perfil bioquimico basico (renal-hepatico-glucosa)','EXAMEN_LAB','Laboratorio',90000,NULL,FALSE),
 ('LAB-UROA',     'Uroanalisis',                    'EXAMEN_LAB',    'Laboratorio',          35000, NULL, FALSE),
 ('LAB-COPRO',    'Coproscopico',                   'EXAMEN_LAB',    'Laboratorio',          25000, NULL, FALSE),
 ('LAB-PARVO',    'Test rapido parvovirus',         'EXAMEN_LAB',    'Laboratorio',          65000, NULL, FALSE),
 ('LAB-4DX',      'Test rapido hemoparasitos / dirofilaria (4Dx)','EXAMEN_LAB','Laboratorio',150000,NULL,FALSE),
 ('LAB-FELV-FIV', 'Test rapido FeLV/FIV',           'EXAMEN_LAB',    'Laboratorio',         120000, NULL, FALSE),
 ('IMG-RX',       'Radiografia (por proyeccion)',   'EXAMEN_IMAGEN', 'Imagenes diagnosticas',70000, 20, FALSE),
 ('IMG-ECO-ABD',  'Ecografia abdominal',            'EXAMEN_IMAGEN', 'Imagenes diagnosticas',120000,30, TRUE),
 ('IMG-ECO-CARD', 'Ecocardiograma',                 'EXAMEN_IMAGEN', 'Imagenes diagnosticas',220000,45, TRUE),
 ('IMG-ECG',      'Electrocardiograma',             'EXAMEN_IMAGEN', 'Imagenes diagnosticas', 80000,20, TRUE)
) AS v(codigo, nombre, tipo, cat, precio, dur, vet)
ON CONFLICT (codigo) DO NOTHING;
