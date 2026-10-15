-- Variantes y tipos de pit bull / bully frecuentes en Colombia que no son razas FCI
-- independientes (lineas, colores o cruces). reconocimiento_fci = NO_FCI.
-- potencialmente_peligrosa: TRUE para los tipos de pit bull (Ley 746/2002 art. 108-F y
-- sus cruces); FALSE para los American Bully, que no figuran en la lista legal
-- (validar con asesor juridico si un ejemplar es cruce con raza listada).
INSERT INTO raza (especie_id, nombre, activo, origen, tamano, reconocimiento_fci, potencialmente_peligrosa)
SELECT e.id, v.nombre, TRUE, v.origen, v.tamano, 'NO_FCI', v.peligrosa
FROM especie e
CROSS JOIN (VALUES
  ('Pit Bull Red Nose (American Pit Bull Terrier)',  'Estados Unidos', 'MEDIANO', TRUE),
  ('Pit Bull Blue Nose (American Pit Bull Terrier)', 'Estados Unidos', 'MEDIANO', TRUE),
  ('Pit Bull Cruce / Mestizo',                       'Estados Unidos', 'MEDIANO', TRUE),
  ('American Bully Pocket',                          'Estados Unidos', 'PEQUENO', FALSE),
  ('American Bully Standard',                        'Estados Unidos', 'MEDIANO', FALSE),
  ('American Bully Classic',                         'Estados Unidos', 'MEDIANO', FALSE),
  ('American Bully XL',                              'Estados Unidos', 'GRANDE',  FALSE),
  ('American Bully Micro / Exotic',                  'Estados Unidos', 'PEQUENO', FALSE)
) AS v(nombre, origen, tamano, peligrosa)
WHERE e.nombre = 'Perro'
ON CONFLICT (especie_id, nombre) DO NOTHING;
