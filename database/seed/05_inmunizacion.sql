-- DATOS DE DEMOSTRACION (ficticios). Idempotente (clave de negocio: marcador 'DEMO-Vnn' en observaciones).
-- Vacunas aplicadas con proxima fecha (algunas por vencer o vencidas) y desparasitaciones.
SET client_min_messages = warning;
SET TIME ZONE 'America/Bogota';
SET app.auditoria_app = 'on';

INSERT INTO inmunizacion (paciente_id, vacuna_id, consulta_id, fecha_aplicacion, fecha_proxima_dosis, lote, laboratorio, veterinario_id,
                          observaciones, sede_id, dosis_numero, peso_kg, estado, via_administracion, sitio_aplicacion, consulta_vacuna_id)
SELECT p.id, va.id, k.id, CURRENT_DATE + v.d_aplic, CURRENT_DATE + v.d_prox, v.lote, 'Laboratorio Demo', vet.id,
       v.marca, (SELECT id FROM sede WHERE es_principal LIMIT 1), v.dosis, p.peso_actual, 'APLICADA', 'SC', 'Region interescapular',
       cv.id
FROM (VALUES
  -- marcador,  paciente,    vacuna,       dias_aplic, dias_prox, lote,             dosis, consulta
  ('DEMO-V01','DEMO-P001','CAN-DHPP',  -350,  745, 'DEMO-LV-DHPP-01', 3, NULL),
  ('DEMO-V02','DEMO-P001','CAN-RAB',   -340,   25, 'DEMO-LV-RAB-01',  3, NULL),
  ('DEMO-V03','DEMO-P001','CAN-BORD',  -355,  -10, 'DEMO-LV-BORD-01', 2, NULL),
  ('DEMO-V04','DEMO-P002','FEL-FVRCP',   -2,  363, 'DEMO-LV-FEL-01',  3, 'DEMO-K002'),
  ('DEMO-V05','DEMO-P002','FEL-RAB',     -2,  363, 'DEMO-LV-RAB-01',  3, 'DEMO-K002'),
  ('DEMO-V06','DEMO-P003','CAN-RAB',   -335,   30, 'DEMO-LV-RAB-02',  2, NULL),
  ('DEMO-V07','DEMO-P003','CAN-DHPP',  -400,  695, 'DEMO-LV-DHPP-02', 3, NULL),
  ('DEMO-V08','DEMO-P004','CAN-RAB',   -360,    5, 'DEMO-LV-RAB-03',  4, NULL),
  ('DEMO-V09','DEMO-P009','CAN-DHPP',  -200,  895, 'DEMO-LV-DHPP-03', 3, NULL),
  ('DEMO-V10','DEMO-P009','CAN-RAB',   -200,  165, 'DEMO-LV-RAB-04',  2, NULL),
  ('DEMO-V11','DEMO-P006','CAN-DHPP',   -45,  -24, 'DEMO-LV-DHPP-04', 2, NULL)
) AS v(marca, pcod, vcod, d_aplic, d_prox, lote, dosis, kcod)
JOIN paciente p  ON p.codigo = v.pcod
JOIN vacuna va   ON va.codigo = v.vcod
JOIN usuario u   ON u.username = 'demo.vet'
JOIN veterinario vet ON vet.usuario_id = u.id
LEFT JOIN consulta k ON k.codigo = v.kcod
LEFT JOIN consulta_vacuna cv ON cv.consulta_id = k.id
     AND cv.nombre = CASE v.vcod WHEN 'FEL-FVRCP' THEN 'Triple felina (Panleucopenia, Calicivirus, Herpesvirus-1)' WHEN 'FEL-RAB' THEN 'Antirrabica felina' END
WHERE NOT EXISTS (SELECT 1 FROM inmunizacion i WHERE i.observaciones = v.marca);

-- Desparasitaciones (marcador 'DEMO-Dnn' en observaciones).
INSERT INTO desparasitacion (paciente_id, tipo, item_id, producto, principio_activo, dosis, peso_kg, fecha_aplicacion, proxima_fecha,
                             veterinario_id, sede_id, observaciones)
SELECT p.id, v.tipo, it.id, it.nombre, it.principio_activo, v.dosis, p.peso_actual, CURRENT_DATE + v.d_aplic, CURRENT_DATE + v.d_prox,
       vet.id, (SELECT id FROM sede WHERE es_principal LIMIT 1), v.marca
FROM (VALUES
  ('DEMO-D01','DEMO-P001','INTERNA', 'MED-ANT-PZQ',  '3 tabletas',        -80,  10),
  ('DEMO-D02','DEMO-P001','EXTERNA', 'MED-ANT-ISOX', '1 tableta',         -20,  70),
  ('DEMO-D03','DEMO-P002','COMBINADA','MED-ANT-SELA','1 pipeta 45 mg',    -30,   0),
  ('DEMO-D04','DEMO-P006','INTERNA', 'MED-ANT-PZQ',  '1.5 tabletas',      -60,  30)
) AS v(marca, pcod, tipo, icod, dosis, d_aplic, d_prox)
JOIN paciente p ON p.codigo = v.pcod
JOIN item it ON it.codigo = v.icod
JOIN usuario u ON u.username = 'demo.vet'
JOIN veterinario vet ON vet.usuario_id = u.id
WHERE NOT EXISTS (SELECT 1 FROM desparasitacion d WHERE d.observaciones = v.marca);
