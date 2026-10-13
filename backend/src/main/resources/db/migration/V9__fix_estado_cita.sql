CREATE TABLE IF NOT EXISTS estado_cita (
    id SERIAL PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(60) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO estado_cita (codigo, nombre) VALUES
  ('PROGRAMADA','Programada'),
  ('CONFIRMADA','Confirmada'),
  ('EN_CURSO','En curso'),
  ('ATENDIDA','Atendida'),
  ('CANCELADA','Cancelada'),
  ('NO_ASISTIO','No asistió'),
  ('REAGENDADA','Reagendada')
ON CONFLICT (codigo) DO NOTHING;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_cita_estado'
  ) THEN
    ALTER TABLE cita ADD CONSTRAINT fk_cita_estado FOREIGN KEY (estado_cita_id) REFERENCES estado_cita(id);
  END IF;
END $$;
