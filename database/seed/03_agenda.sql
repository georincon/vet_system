-- DATOS DE DEMOSTRACION (ficticios). Idempotente.
-- Citas (codigo DEMO-Cnnn) en distintos estados alrededor de CURRENT_DATE y bloqueos de agenda (motivo 'DEMO ...').
SET client_min_messages = warning;
SET TIME ZONE 'America/Bogota';
SET app.auditoria_app = 'on';

INSERT INTO cita (codigo, paciente_id, veterinario_id, tipo_cita_id, fecha_hora_inicio, fecha_hora_fin, motivo, notas,
                  fecha, hora_inicio, hora_fin, estado_cita_id, activo, creado_el, actualizado_el, sede_id, sala_id,
                  canal_origen, hora_llegada, atencion_inicio, confirmada_en, confirmada_via, cancelada_en, motivo_cancelacion, es_urgencia)
SELECT v.codigo, p.id, vet.id, tc.id,
       ini, ini + (tc.duracion_minutos || ' minutes')::interval,
       v.motivo, 'DEMO',
       (CURRENT_DATE + v.dia), v.hora, v.hora + (tc.duracion_minutos || ' minutes')::interval,
       ec.id, true, now()::timestamp, now()::timestamp,
       (SELECT id FROM sede WHERE es_principal LIMIT 1), sa.id,
       v.canal,
       CASE WHEN v.estado IN ('EN_SALA','ATENDIDA') THEN ini - interval '10 minutes' END,
       CASE WHEN v.estado IN ('EN_SALA','ATENDIDA') THEN ini END,
       CASE WHEN v.estado IN ('CONFIRMADA','EN_SALA','ATENDIDA') THEN ini - interval '1 day' END,
       CASE WHEN v.estado IN ('CONFIRMADA','EN_SALA','ATENDIDA') THEN 'WHATSAPP' END,
       CASE WHEN v.estado = 'CANCELADA' THEN now() END,
       CASE WHEN v.estado = 'CANCELADA' THEN 'El tutor reprograma por viaje (demo)' END,
       v.urgencia
FROM (VALUES
  -- codigo,      paciente,    tipo de cita,                   dia, hora,           estado,      sala,           canal,      urgencia, motivo
  ('DEMO-C001','DEMO-P001','Consulta general',               -1, TIME '10:00','ATENDIDA',  'Consultorio 1','TELEFONO', false, 'Vomito y diarrea desde hace 2 dias'),
  ('DEMO-C002','DEMO-P002','Vacunacion',                     -2, TIME '15:00','ATENDIDA',  'Consultorio 2','WHATSAPP', false, 'Refuerzo de vacunas anuales'),
  ('DEMO-C003','DEMO-P003','Control / seguimiento',          -3, TIME '09:00','NO_ASISTIO','Consultorio 1','TELEFONO', false, 'Control de dermatitis'),
  ('DEMO-C004','DEMO-P004','Control / seguimiento',           0, TIME '10:00','EN_SALA',   'Consultorio 1','RECEPCION',false, 'Control cardiologico y de peso'),
  ('DEMO-C005','DEMO-P005','Consulta general',                0, TIME '11:00','CONFIRMADA','Consultorio 1','WHATSAPP', false, 'Primera consulta, rescatado hace 1 semana'),
  ('DEMO-C006','DEMO-P006','Consulta general',                0, TIME '15:00','AGENDADA',  'Consultorio 2','RECEPCION',false, 'Picazon y perdida de pelo'),
  ('DEMO-C007','DEMO-P009','Chequeo prequirurgico',           1, TIME '09:00','CONFIRMADA','Consultorio 1','WHATSAPP', false, 'Valoracion previa a castracion'),
  ('DEMO-C008','DEMO-P007','Consulta general',                1, TIME '10:30','AGENDADA',  'Consultorio 2','WEB',      false, 'Revision de dientes y apetito'),
  ('DEMO-C009','DEMO-P010','Vacunacion',                      2, TIME '14:00','AGENDADA',  'Consultorio 2','RECEPCION',false, 'Vacuna polivalente'),
  ('DEMO-C010','DEMO-P003','Control / seguimiento',           3, TIME '09:30','CANCELADA', 'Consultorio 1','TELEFONO', false, 'Control de dermatitis (reprogramado)'),
  ('DEMO-C011','DEMO-P002','Ecografia / imagenes',            4, TIME '11:00','AGENDADA',  'Imagenes',     'RECEPCION',false, 'Ecografia abdominal de control'),
  ('DEMO-C012','DEMO-P008','Consulta general',                5, TIME '16:00','AGENDADA',  'Consultorio 1','WEB',      false, 'Revision general del ave')
) AS v(codigo, pcod, tipo, dia, hora, estado, sala, canal, urgencia, motivo)
JOIN paciente p      ON p.codigo = v.pcod
JOIN tipo_cita tc    ON tc.nombre = v.tipo
JOIN estado_cita ec  ON ec.codigo = CASE v.estado WHEN 'AGENDADA' THEN 'PROGRAMADA' ELSE v.estado END
JOIN sala sa         ON sa.nombre = v.sala
JOIN usuario u       ON u.username = 'demo.vet'
JOIN veterinario vet ON vet.usuario_id = u.id
CROSS JOIN LATERAL (SELECT ((CURRENT_DATE + v.dia) + v.hora) AT TIME ZONE 'America/Bogota' AS ini) t(ini)
ON CONFLICT (codigo) DO NOTHING;

-- ---------- Bloqueos de agenda ----------
INSERT INTO bloqueo_agenda (veterinario_id, sala_id, inicio, fin, motivo, sede_id, tipo)
SELECT v.vet_id, v.sala_id, ((CURRENT_DATE + v.dia) + v.h_ini) AT TIME ZONE 'America/Bogota',
       ((CURRENT_DATE + v.dia) + v.h_fin) AT TIME ZONE 'America/Bogota', v.motivo, (SELECT id FROM sede WHERE es_principal LIMIT 1), v.tipo
FROM (
  SELECT vet.id AS vet_id, NULL::bigint AS sala_id, 1 AS dia, TIME '12:00' AS h_ini, TIME '14:00' AS h_fin, 'DEMO almuerzo' AS motivo, 'ALMUERZO' AS tipo
  FROM veterinario vet JOIN usuario u ON u.id = vet.usuario_id AND u.username = 'demo.vet'
  UNION ALL
  SELECT vet.id, NULL, 7, TIME '08:00', TIME '17:00', 'DEMO capacitacion en cardiologia', 'CAPACITACION'
  FROM veterinario vet JOIN usuario u ON u.id = vet.usuario_id AND u.username = 'demo.vet'
  UNION ALL
  SELECT NULL, s.id, 2, TIME '07:00', TIME '12:00', 'DEMO mantenimiento del quirofano', 'MANTENIMIENTO'
  FROM sala s WHERE s.nombre = 'Quirofano'
) v
WHERE NOT EXISTS (SELECT 1 FROM bloqueo_agenda b WHERE b.motivo = v.motivo);
