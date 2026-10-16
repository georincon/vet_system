-- DATOS DE DEMOSTRACION (ficticios). Idempotente.
-- Tutores (notas 'DEMO', documentos 99xxxxxx, correos @example.com), consentimientos (firmados y pendientes),
-- pacientes (codigo DEMO-Pnnn), vinculos paciente-tutor, alergias y pesos.
SET client_min_messages = warning;
SET TIME ZONE 'America/Bogota';
SET app.auditoria_app = 'on';

-- ---------- Tutores ----------
INSERT INTO tutor (tipo_documento, numero_documento, nombres, apellidos, telefono, telefono_alt, email, direccion, ciudad, notas,
                   tipo_persona, digito_verificacion, preferencia_contacto, acepta_email, acepta_whatsapp, acepta_sms,
                   autoriza_tratamiento_datos, fecha_autorizacion_datos, fecha_nacimiento)
SELECT v.tipo_doc, v.doc, v.nombres, v.apellidos, v.tel, v.tel_alt, v.email, v.dir, v.ciudad, 'DEMO: dato ficticio de demostracion',
       v.tipo_persona, v.dv, v.pref, v.a_email, v.a_wa, v.a_sms, v.autoriza,
       CASE WHEN v.autoriza THEN now() - (v.dias_aut || ' days')::interval END, v.f_nac
FROM (VALUES
  ('CC','99000001','Laura','Mendoza Prada','3000000201','3000000211','laura.mendoza.demo@example.com','Calle 10 # 5-20 (ficticia)','Bogota','NATURAL',NULL,'WHATSAPP',true,true,false,true,40,DATE '1988-03-14'),
  ('CC','99000002','Carlos','Prada Gomez','3000000202',NULL,'carlos.prada.demo@example.com','Carrera 7 # 12-34 (ficticia)','Bogota','NATURAL',NULL,'EMAIL',true,false,false,true,25,DATE '1979-11-02'),
  ('CC','99000003','Marcela','Torres Beltran','3000000203','3000000213','marcela.torres.demo@example.com','Avenida 68 # 45-10 (ficticia)','Bogota','NATURAL',NULL,'WHATSAPP',true,true,true,true,12,DATE '1992-07-21'),
  ('CE','99000004','Jorge','Quintero Salazar','3000000204',NULL,'jorge.quintero.demo@example.com','Calle 80 # 20-15 (ficticia)','Chia','NATURAL',NULL,'LLAMADA',false,false,false,false,0,DATE '1985-01-30'),
  ('CC','99000005','Diana','Herrera Lozano','3000000205',NULL,'diana.herrera.demo@example.com','Transversal 3 # 9-90 (ficticia)','Bogota','NATURAL',NULL,'WHATSAPP',false,true,false,false,0,DATE '1995-09-09'),
  ('CC','99000006','Andres','Villamizar Reyes','3000000206','3000000216','andres.villamizar.demo@example.com','Carrera 15 # 100-8 (ficticia)','Soacha','NATURAL',NULL,'EMAIL',true,true,false,true,5,DATE '2000-05-17'),
  ('NIT','99000007','Fundacion Huellas de Prueba',NULL,'3000000207',NULL,'contacto.huellas.demo@example.com','Calle 50 # 30-40 (ficticia)','Bogota','JURIDICA','7','EMAIL',true,false,false,true,60,NULL)
) AS v(tipo_doc, doc, nombres, apellidos, tel, tel_alt, email, dir, ciudad, tipo_persona, dv, pref, a_email, a_wa, a_sms, autoriza, dias_aut, f_nac)
ON CONFLICT (tipo_documento, numero_documento) DO NOTHING;

-- ---------- Consentimientos ----------
-- HABEAS_DATA firmado para quienes autorizan; PENDIENTE para Diana (flujo de firma); Jorge y Andres sin consentimiento.
INSERT INTO consentimiento (plantilla_id, tipo, tutor_id, sede_id, estado, texto_firmado, firmante_nombre, firmante_documento,
                            metodo_firma, firmado_en)
SELECT p.id, 'HABEAS_DATA', t.id, (SELECT id FROM sede WHERE es_principal LIMIT 1), 'FIRMADO', p.texto,
       trim(t.nombres || ' ' || COALESCE(t.apellidos, '')), t.numero_documento, 'TABLET', t.fecha_autorizacion_datos
FROM tutor t JOIN consentimiento_plantilla p ON p.codigo = 'HABEAS_DATA'
WHERE t.numero_documento IN ('99000001','99000002','99000003','99000007')
  AND NOT EXISTS (SELECT 1 FROM consentimiento c WHERE c.tutor_id = t.id AND c.tipo = 'HABEAS_DATA');

INSERT INTO consentimiento (plantilla_id, tipo, tutor_id, sede_id, estado)
SELECT p.id, 'HABEAS_DATA', t.id, (SELECT id FROM sede WHERE es_principal LIMIT 1), 'PENDIENTE'
FROM tutor t JOIN consentimiento_plantilla p ON p.codigo = 'HABEAS_DATA'
WHERE t.numero_documento = '99000005'
  AND NOT EXISTS (SELECT 1 FROM consentimiento c WHERE c.tutor_id = t.id AND c.tipo = 'HABEAS_DATA');

-- ---------- Pacientes ----------
INSERT INTO paciente (codigo, nombre, especie_id, raza_id, sexo, estado_reproductivo, color, fecha_nacimiento, fecha_nacimiento_aproximada,
                      microchip, peso_actual, esterilizado, fecha_esterilizacion, alertas, notas, agresivo, origen, sede_origen_id)
SELECT v.codigo, v.nombre, e.id,
       (SELECT r.id FROM raza r WHERE r.especie_id = e.id AND r.nombre = v.raza ORDER BY r.id LIMIT 1),
       v.sexo, v.repro, v.color, CURRENT_DATE - v.edad_dias, v.aprox, v.chip, v.peso, v.est,
       CASE WHEN v.est THEN CURRENT_DATE - (v.edad_dias / 2) END,
       v.alertas, 'DEMO: paciente ficticio de demostracion', v.agresivo, v.origen,
       (SELECT id FROM sede WHERE es_principal LIMIT 1)
FROM (VALUES
  ('DEMO-P001','Max',    'Perro',  'Labrador Retriever',                  'M','ENTERO',     'Dorado',        1825, false,'DEMO000000001', 28.40, false, NULL,                              false,'CRIADERO'),
  ('DEMO-P002','Luna',   'Gato',   'Siames',                              'F','ESTERILIZADA','Crema y chocolate',1095,false,'DEMO000000002', 3.80, true,  NULL,                              false,'ADOPCION'),
  ('DEMO-P003','Rocky',  'Perro',  'Pit Bull Terrier',                    'M','ENTERO',     'Gris',          1460, false,'DEMO000000003', 24.10, false, 'Raza potencialmente peligrosa: usar bozal y correa corta', true,'ADOPCION'),
  ('DEMO-P004','Mia',    'Perro',  'Poodle (Caniche)',                    'F','ESTERILIZADA','Blanco',       2920, false,NULL,             6.20, true,  'Cardiopatia leve en control',     false,'COMPRA'),
  ('DEMO-P005','Simba',  'Gato',   'Criollo',                             'M','ENTERO',     'Naranja atigrado',365, true, NULL,             4.10, false, NULL,                              false,'RESCATE'),
  ('DEMO-P006','Coco',   'Perro',  'Criollo / Mestizo (sin raza definida)','M','ENTERO',    'Cafe',          730,  true, NULL,             15.70, false, NULL,                              false,'RESCATE'),
  ('DEMO-P007','Nieve',  'Conejo', NULL,                                  'F','ENTERO',     'Blanco',        730,  true, NULL,             1.90, false, NULL,                              false,'COMPRA'),
  ('DEMO-P008','Kiwi',   'Ave',    NULL,                                  'M','ENTERO',     'Verde',         540,  true, NULL,             0.04, false, NULL,                              false,'COMPRA'),
  ('DEMO-P009','Thor',   'Perro',  'Rottweiler',                          'M','ENTERO',     'Negro y fuego', 1095, false,'DEMO000000009', 41.30, false, 'Raza potencialmente peligrosa: usar bozal', false,'ADOPCION'),
  ('DEMO-P010','Bruno',  'Perro',  'Schnauzer Miniatura',                 'M','ENTERO',     'Sal y pimienta',3285, false,NULL,             8.90, false, NULL,                              false,'COMPRA')
) AS v(codigo, nombre, especie, raza, sexo, repro, color, edad_dias, aprox, chip, peso, est, alertas, agresivo, origen)
JOIN especie e ON e.nombre = v.especie
ON CONFLICT (codigo) DO NOTHING;

-- ---------- Vinculos paciente-tutor ----------
INSERT INTO paciente_tutor (paciente_id, tutor_id, es_principal, parentesco)
SELECT p.id, t.id, v.principal, v.parentesco
FROM (VALUES
  ('DEMO-P001','99000001', true, 'Propietario'),
  ('DEMO-P002','99000001', true, 'Propietario'),
  ('DEMO-P003','99000002', true, 'Propietario'),
  ('DEMO-P010','99000002', true, 'Propietario'),
  ('DEMO-P004','99000003', true, 'Propietario'),
  ('DEMO-P004','99000005', false,'Cuidadora (hija)'),
  ('DEMO-P005','99000004', true, 'Propietario'),
  ('DEMO-P006','99000005', true, 'Propietario'),
  ('DEMO-P007','99000006', true, 'Propietario'),
  ('DEMO-P008','99000006', true, 'Propietario'),
  ('DEMO-P009','99000007', true, 'Fundacion (responsable)')
) AS v(pcod, doc, principal, parentesco)
JOIN paciente p ON p.codigo = v.pcod
JOIN tutor t ON t.tipo_documento IN ('CC','CE','NIT') AND t.numero_documento = v.doc
WHERE NOT EXISTS (SELECT 1 FROM paciente_tutor x WHERE x.paciente_id = p.id AND x.tutor_id = t.id);

-- ---------- Alergias ----------
INSERT INTO alergia (paciente_id, descripcion, severidad, tipo, reaccion, registrada_en)
SELECT p.id, v.descr, v.sev, v.tipo, v.reaccion, CURRENT_DATE - v.dias
FROM (VALUES
  ('DEMO-P001','Penicilina',                'ALTA',  'MEDICAMENTO','Urticaria y edema facial', 400),
  ('DEMO-P002','Pollo (proteina)',          'MEDIA', 'ALIMENTO',   'Prurito y otitis recurrente', 300),
  ('DEMO-P004','Vacuna antirrabica',        'BAJA',  'VACUNA',     'Letargo leve 24 h',        900),
  ('DEMO-P006','Picadura de pulga (DAPP)',  'MEDIA', 'AMBIENTAL',  'Dermatitis alergica',      120)
) AS v(pcod, descr, sev, tipo, reaccion, dias)
JOIN paciente p ON p.codigo = v.pcod
WHERE NOT EXISTS (SELECT 1 FROM alergia a WHERE a.paciente_id = p.id AND a.descripcion = v.descr);

-- ---------- Historial de pesos (fechas relativas) ----------
INSERT INTO peso_registro (paciente_id, peso_kg, fecha)
SELECT p.id, v.peso, CURRENT_DATE - v.dias
FROM (VALUES
  ('DEMO-P001', 27.10, 180), ('DEMO-P001', 27.90, 90), ('DEMO-P001', 28.40, 3),
  ('DEMO-P002', 3.60, 200),  ('DEMO-P002', 3.80, 30),
  ('DEMO-P003', 23.20, 150), ('DEMO-P003', 24.10, 20),
  ('DEMO-P004', 6.50, 120),  ('DEMO-P004', 6.20, 15),
  ('DEMO-P005', 3.20, 60),   ('DEMO-P005', 4.10, 5),
  ('DEMO-P009', 39.80, 100), ('DEMO-P009', 41.30, 10)
) AS v(pcod, peso, dias)
JOIN paciente p ON p.codigo = v.pcod
-- clave de negocio: (paciente, peso); las fechas son relativas a CURRENT_DATE y cambian cada dia
WHERE NOT EXISTS (SELECT 1 FROM peso_registro w WHERE w.paciente_id = p.id AND w.peso_kg = v.peso);
