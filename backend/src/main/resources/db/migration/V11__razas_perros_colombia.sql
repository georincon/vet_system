INSERT INTO raza (especie_id, nombre, activo)
SELECT e.id, v.nombre, TRUE
FROM especie e
CROSS JOIN (VALUES
  ('Criollo (Sabanero)'),
  ('Pastor Alemán'),
  ('Labrador Retriever'),
  ('Golden Retriever'),
  ('Rottweiler'),
  ('Doberman Pinscher'),
  ('Boxer'),
  ('Bulldog Francés'),
  ('Bulldog Inglés'),
  ('Beagle'),
  ('Dachshund (Teckel)'),
  ('Poodle (Caniche)'),
  ('Shih Tzu'),
  ('Yorkshire Terrier'),
  ('Chihuahua'),
  ('Pomerania'),
  ('Cocker Spaniel'),
  ('Springer Spaniel'),
  ('Border Collie'),
  ('Husky Siberiano'),
  ('Pastor Collie'),
  ('Pit Bull Terrier'),
  ('American Staffordshire Terrier'),
  ('Mastín Español'),
  ('San Bernardo'),
  ('Weimaraner'),
  ('Vizsla'),
  ('Bichón Frisé'),
  ('Schnauzer'),
  ('Pinscher Miniatura')
) AS v(nombre)
WHERE e.nombre = 'Perro'
ON CONFLICT (especie_id, nombre) DO NOTHING;
