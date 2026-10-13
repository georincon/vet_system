DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cita_estado') THEN
    -- FK to estado_cita added after table creation
  END IF;
END $$;
