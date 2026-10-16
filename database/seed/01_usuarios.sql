-- DATOS DE DEMOSTRACION (ficticios). Idempotente. Ejecutar DESPUES de las migraciones Flyway.
-- Usuarios demo por rol + datos del veterinario + horario. Marca: username 'demo.*'.
-- Contrasenas (hashes BCrypt $2a$10 precalculados, equivalentes a pgcrypto crypt(pass, gen_salt('bf',10))):
--   demo.vet  / VetDemo#2026     demo.recepcion / RecDemo#2026     demo.auxiliar / AuxDemo#2026
SET client_min_messages = warning;
SET TIME ZONE 'America/Bogota';
SET app.auditoria_app = 'on';   -- no genera filas de auditoria para la carga demo

INSERT INTO usuario (username, password, nombre, email, rol, activo, creado_en, actualizado_en, telefono)
SELECT v.username, v.password, v.nombre, v.email, v.rol, true, now(), now(), v.telefono
FROM (VALUES
  ('demo.vet',       '$2a$10$sa02xvtz1CKWOHg9VSiHfe37Awbi4GjOMwIOWpQZkCC.cWUGTkszq', 'Valentina Ospina (DEMO)', 'demo.vet@example.com',       'VETERINARIO', '3000000101'),
  ('demo.recepcion', '$2a$10$1kgIkc2oxirLqtS4CRMI8ux8vqJZSz.TVOgQQg3Ytv1FiIJDF3vSS', 'Sofia Caicedo (DEMO)',    'demo.recepcion@example.com', 'RECEPCION',   '3000000102'),
  ('demo.auxiliar',  '$2a$10$zzmFpy05z2pMHh7C3.zfSe1XLP/9gmJKd6yXgZxodVO9WqAQF.are', 'Mateo Rincon (DEMO)',     'demo.auxiliar@example.com',  'AUXILIAR',    '3000000103')
) AS v(username, password, nombre, email, rol, telefono)
ON CONFLICT (username) DO NOTHING;

-- Veterinario (la migracion V10 solo cubre usuarios existentes en ese momento).
INSERT INTO veterinario (usuario_id, nombres, apellidos, numero_profesional, telefono, email, especialidad, activo)
SELECT u.id, 'Valentina', 'Ospina (DEMO)', 'TP-DEMO-00001', '3000000101', 'demo.vet@example.com', 'Medicina interna de pequenos animales', true
FROM usuario u WHERE u.username = 'demo.vet'
ON CONFLICT (usuario_id) DO UPDATE
   SET numero_profesional = COALESCE(veterinario.numero_profesional, EXCLUDED.numero_profesional),
       especialidad       = COALESCE(veterinario.especialidad, EXCLUDED.especialidad),
       email              = COALESCE(veterinario.email, EXCLUDED.email);

-- Acceso a la sede principal.
INSERT INTO usuario_sede (usuario_id, sede_id, es_predeterminada)
SELECT u.id, s.id, true
FROM usuario u CROSS JOIN sede s
WHERE u.username LIKE 'demo.%' AND s.es_principal
  AND NOT EXISTS (SELECT 1 FROM usuario_sede us WHERE us.usuario_id = u.id AND us.sede_id = s.id);

-- Horario del veterinario (lunes a sabado 08:00-17:00; dia_semana 1..6).
INSERT INTO horario_profesional (usuario_id, sede_id, dia_semana, hora_inicio, hora_fin)
SELECT u.id, s.id, d, TIME '08:00', TIME '17:00'
FROM usuario u CROSS JOIN sede s CROSS JOIN generate_series(1, 6) AS d
WHERE u.username = 'demo.vet' AND s.es_principal
  AND NOT EXISTS (SELECT 1 FROM horario_profesional h
                   WHERE h.usuario_id = u.id AND h.sede_id = s.id AND h.dia_semana = d);
