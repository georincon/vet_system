-- Depura 3 razas de perro del sembrado inicial (V1/seed de catalogos) que duplican
-- logicamente a las del catalogo FCI (V23). Los pacientes se re-apuntan a la raza
-- canonica y la fila antigua queda inactiva (no se borra, por trazabilidad).
DO $$
DECLARE
    v_perro BIGINT := (SELECT id FROM especie WHERE nombre = 'Perro');
    r RECORD;
BEGIN
    FOR r IN
        SELECT d.id AS viejo, c.id AS nuevo
        FROM (VALUES ('Poodle', 'Poodle (Caniche)'),
                     ('Pastor Aleman', 'Pastor Alemán'),
                     ('Criollo', 'Criollo / Mestizo (sin raza definida)')) AS m(viejo, nuevo)
        JOIN raza d ON d.especie_id = v_perro AND d.nombre = m.viejo
        JOIN raza c ON c.especie_id = v_perro AND c.nombre = m.nuevo
    LOOP
        UPDATE paciente SET raza_id = r.nuevo WHERE raza_id = r.viejo;
        UPDATE raza SET activo = FALSE WHERE id = r.viejo;
    END LOOP;
END $$;
