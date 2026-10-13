-- ============================================================================
-- Migracion V2: columna sexo de paciente como CHAR(1)
-- Hibernate mapea los enums de una sola letra (Sexo M/F) al tipo CHARACTER(1);
-- la V1 la creo como VARCHAR(1). Ajuste de tipado sin cambio semantico.
-- ============================================================================
ALTER TABLE paciente ALTER COLUMN sexo TYPE CHAR(1) USING sexo::CHAR(1);