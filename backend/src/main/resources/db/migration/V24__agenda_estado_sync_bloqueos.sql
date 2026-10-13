-- ============================================================================
-- V24 - Fase 2 (Sprint 1): consistencia de la agenda.
--  1) cita.estado (texto legado, base de las restricciones EXCLUDE de V14) se sincroniza desde
--     cita.estado_cita_id (catalogo que usa la aplicacion). Sin esto, cancelar una cita no
--     liberaba el horario en la BD (estado seguia 'AGENDADA').
--  2) uq_cita_vet_inicio (UNIQUE global, V1) ya fue retirada en V14; se deja un indice unico
--     parcial equivalente que ignora citas canceladas/no asistidas/inactivas.
--  3) bloqueo_agenda puede ser solo de sala (veterinario_id opcional).
--  4) auditoria por trigger tambien para cita y bloqueo_agenda.
--  5) consulta_addendum.tipo era VARCHAR(15) pero el CHECK admite RESULTADO_TARDIO (16 car.): se amplia.
-- ============================================================================

-- ---------------------------------------------------------------- 1) sincronizacion de estado
CREATE OR REPLACE FUNCTION fn_cita_sync_estado() RETURNS trigger LANGUAGE plpgsql AS $fn$
DECLARE v_codigo VARCHAR;
BEGIN
  IF NEW.estado_cita_id IS NOT NULL THEN
    SELECT codigo INTO v_codigo FROM estado_cita WHERE id = NEW.estado_cita_id;
    NEW.estado := CASE v_codigo
      WHEN 'PROGRAMADA' THEN 'AGENDADA'
      WHEN 'CONFIRMADA' THEN 'CONFIRMADA'
      WHEN 'EN_SALA'    THEN 'EN_SALA'
      WHEN 'EN_CURSO'   THEN 'EN_SALA'      -- sigue ocupando la franja
      WHEN 'ATENDIDA'   THEN 'ATENDIDA'
      WHEN 'CANCELADA'  THEN 'CANCELADA'
      WHEN 'NO_ASISTIO' THEN 'NO_ASISTIO'
      WHEN 'REAGENDADA' THEN 'CANCELADA'    -- libera la franja original
      ELSE NEW.estado
    END;
  END IF;
  RETURN NEW;
END
$fn$;

CREATE OR REPLACE TRIGGER trg_cita_sync_estado
  BEFORE INSERT OR UPDATE ON cita FOR EACH ROW EXECUTE FUNCTION fn_cita_sync_estado();

-- Backfill (solo puede liberar franjas, nunca crear solapes nuevos)
UPDATE cita c SET estado = CASE e.codigo
      WHEN 'PROGRAMADA' THEN 'AGENDADA'
      WHEN 'CONFIRMADA' THEN 'CONFIRMADA'
      WHEN 'EN_SALA'    THEN 'EN_SALA'
      WHEN 'EN_CURSO'   THEN 'EN_SALA'
      WHEN 'ATENDIDA'   THEN 'ATENDIDA'
      WHEN 'CANCELADA'  THEN 'CANCELADA'
      WHEN 'NO_ASISTIO' THEN 'NO_ASISTIO'
      WHEN 'REAGENDADA' THEN 'CANCELADA'
      ELSE c.estado END
  FROM estado_cita e WHERE e.id = c.estado_cita_id;

-- ---------------------------------------------------------------- 2) unicidad parcial
ALTER TABLE cita DROP CONSTRAINT IF EXISTS uq_cita_vet_inicio;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cita_vet_inicio_vigente
  ON cita (veterinario_id, fecha_hora_inicio)
  WHERE activo AND estado NOT IN ('CANCELADA', 'NO_ASISTIO');

-- ---------------------------------------------------------------- 3) bloqueos de sala
ALTER TABLE bloqueo_agenda ALTER COLUMN veterinario_id DROP NOT NULL;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_bloqueo_destino') THEN
    ALTER TABLE bloqueo_agenda ADD CONSTRAINT ck_bloqueo_destino
      CHECK (veterinario_id IS NOT NULL OR sala_id IS NOT NULL);
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_bloqueo_sala_fecha ON bloqueo_agenda (sala_id, inicio);

-- ---------------------------------------------------------------- 4) auditoria
CREATE OR REPLACE TRIGGER trg_aud_cita
  AFTER INSERT OR UPDATE OR DELETE ON cita FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_bloqueo_agenda
  AFTER INSERT OR UPDATE OR DELETE ON bloqueo_agenda FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();

-- ---------------------------------------------------------------- 5) addendum.tipo
ALTER TABLE consulta_addendum ALTER COLUMN tipo TYPE VARCHAR(20);
