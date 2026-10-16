-- DATOS DE DEMOSTRACION (ficticios). Idempotente (cada bloque se omite si su consulta DEMO-Knnn ya existe).
-- Consultas: DEMO-K001 CERRADA (signos vitales, diagnosticos, receta, plan de tratamiento, procedimiento),
--            DEMO-K002 CERRADA (vacunacion), DEMO-K003 ABIERTA (en sala hoy).
-- Respeta los triggers de inmutabilidad: primero se insertan los hijos y al final se cierra la consulta.
SET client_min_messages = warning;
SET TIME ZONE 'America/Bogota';
SET app.auditoria_app = 'on';

-- ===================== K001: consulta cerrada completa (Max) =====================
DO $$
DECLARE
  v_vet   bigint; v_usr bigint; v_sede bigint; v_pac bigint; v_cita bigint; v_cons bigint; v_receta bigint; v_plan bigint;
  v_ini   timestamptz;
BEGIN
  IF EXISTS (SELECT 1 FROM consulta WHERE codigo = 'DEMO-K001') THEN RETURN; END IF;
  SELECT v.id, u.id INTO v_vet, v_usr FROM veterinario v JOIN usuario u ON u.id = v.usuario_id WHERE u.username = 'demo.vet';
  SELECT id INTO v_sede FROM sede WHERE es_principal LIMIT 1;
  SELECT id INTO v_pac FROM paciente WHERE codigo = 'DEMO-P001';
  SELECT id, fecha_hora_inicio INTO v_cita, v_ini FROM cita WHERE codigo = 'DEMO-C001';
  IF v_vet IS NULL OR v_pac IS NULL OR v_cita IS NULL THEN RAISE EXCEPTION 'Ejecute antes 01, 02 y 03'; END IF;

  INSERT INTO consulta (codigo, cita_id, paciente_id, veterinario_id, fecha_hora, fecha, sede_id, tipo_consulta, estado,
                        motivo_consulta, motivo, anamnesis, subjetivo, objetivo, evaluacion, plan, sintomatologia,
                        diagnostico_principal, diagnostico, tratamiento, pronostico, observaciones,
                        peso_kg, temperatura_c, frecuencia_cardiaca, frecuencia_respiratoria, proxima_revision, creado_el, actualizado_el)
  VALUES ('DEMO-K001', v_cita, v_pac, v_vet, v_ini, (v_ini AT TIME ZONE 'America/Bogota')::date, v_sede, 'GENERAL', 'ABIERTA',
          'Vomito y diarrea desde hace 2 dias', 'Vomito y diarrea',
          'Paciente de 5 anos, vacunas al dia. Ingirio restos de comida hace 3 dias. Vomito 4 veces y diarrea liquida sin sangre. Apetito disminuido.',
          'El tutor refiere decaimiento y rechazo parcial del alimento.',
          'Mucosas rosadas, TLLC 2 s, deshidratacion leve (5%). Dolor abdominal leve a la palpacion. Sin masas.',
          'Gastroenteritis aguda inespecifica con deshidratacion leve.',
          'Fluidoterapia SC/IV, antiemetico, dieta blanda 5 dias, control en 72 h.',
          'Vomito, diarrea, decaimiento', 'Gastroenteritis aguda inespecifica', 'Gastroenteritis aguda inespecifica',
          'Maropitant, fluidos y dieta blanda', 'Bueno',
          'DEMO: consulta ficticia de demostracion',
          28.40, 39.30, 112, 28, CURRENT_DATE + 2, now()::timestamp, now()::timestamp)
  RETURNING id INTO v_cons;

  INSERT INTO signo_vital (consulta_id, temperatura, frecuencia_cardiaca, frecuencia_respiratoria, peso_kg, condicion_corporal, mucosas,
                           tiempo_llenado_capilar, escala_dolor, hidratacion_pct, spo2, puntaje_condicion_corporal, notas, registrado_por)
  VALUES (v_cons, 39.3, 112, 28, 28.40, 'IDEAL', 'Rosadas, ligeramente secas', 2.0, 2, 5.0, 98.0, 5, 'Signos vitales de ingreso (DEMO)', v_usr);

  INSERT INTO diagnostico (consulta_id, descripcion, codigo_cie, tipo, orden)
  VALUES (v_cons, 'Gastroenteritis aguda inespecifica', 'K52.9', 'DEFINITIVO', 1),
         (v_cons, 'Deshidratacion leve', 'E86', 'PRESUNTIVO', 2);

  INSERT INTO consulta_medicamento (consulta_id, nombre, dosis, frecuencia, duracion, via, observaciones, orden)
  VALUES (v_cons, 'Maropitant', '1 mg/kg', 'Cada 24 h', '3 dias', 'SC', 'Aplicar antes de la comida', 1),
         (v_cons, 'Amoxicilina + acido clavulanico', '12.5 mg/kg', 'Cada 12 h', '5 dias', 'VO', 'Con alimento', 2);

  INSERT INTO consulta_procedimiento (consulta_id, item_id, descripcion, cantidad, precio_unitario, realizado_por, realizado_en, notas)
  SELECT v_cons, i.id, 'Terapia de fluidos (Lactato de Ringer SC)', 1, i.precio_venta, v_usr, v_ini + interval '20 minutes', 'DEMO: 500 ml SC'
  FROM item i WHERE i.codigo = 'SRV-PRC-FLUI';

  INSERT INTO receta (consulta_id, paciente_id, veterinario_id, fecha, indicaciones_generales, estado, valida_hasta,
                      numero_formula, codigo_verificacion, formula_emitida_en)
  VALUES (v_cons, v_pac, v_vet, (v_ini AT TIME ZONE 'America/Bogota')::date,
          'Dieta blanda (pollo cocido y arroz) por 5 dias. Agua fresca a voluntad. Volver si hay sangre en heces o fiebre.',
          'ACTIVA', CURRENT_DATE + 20, 'DEMO-FOR-0001', 'DEMO0001', v_ini + interval '40 minutes')
  RETURNING id INTO v_receta;

  INSERT INTO receta_item (receta_id, item_id, medicamento, dosis, via, frecuencia, duracion, cantidad, indicaciones, orden, concentracion, presentacion)
  VALUES (v_receta, (SELECT id FROM item WHERE codigo = 'MED-MARO'), 'Maropitant', '1 mg/kg (2.8 ml)', 'SC', 'Cada 24 h', '3 dias', 3, 'Aplicar en la clinica o por el tutor entrenado', 1, '10 mg/ml', 'Frasco inyectable'),
         (v_receta, (SELECT id FROM item WHERE codigo = 'MED-AMOX'), 'Amoxicilina + acido clavulanico', '1 tableta de 500 mg', 'VO', 'Cada 12 h', '5 dias', 10, 'Administrar con comida', 2, '500/125 mg', 'Tableta');

  INSERT INTO plan_tratamiento (consulta_id, paciente_id, objetivo, recomendaciones, advertencias, estado, creado_por)
  VALUES (v_cons, v_pac, 'Resolver el cuadro gastrointestinal y restablecer la hidratacion.',
          'Reintroducir el alimento habitual de forma gradual a partir del dia 6.',
          'Acudir a urgencias si hay vomito persistente, sangre en heces o decaimiento marcado.', 'ACTIVO', v_usr)
  RETURNING id INTO v_plan;

  INSERT INTO plan_tratamiento_item (plan_id, consulta_id, orden, fase, tipo, descripcion, fecha_inicio, fecha_fin)
  VALUES (v_plan, v_cons, 1, 'Fase aguda', 'MEDICACION', 'Maropitant SC cada 24 h y amoxicilina/clavulanico cada 12 h', CURRENT_DATE - 1, CURRENT_DATE + 4),
         (v_plan, v_cons, 2, 'Fase aguda', 'DIETA',       'Dieta blanda en porciones pequenas (4-5 tomas al dia)',       CURRENT_DATE - 1, CURRENT_DATE + 4),
         (v_plan, v_cons, 3, 'Seguimiento', 'CONTROL',    'Control clinico y pesaje en 72 h',                             CURRENT_DATE + 2, CURRENT_DATE + 2);

  INSERT INTO problema_activo (paciente_id, descripcion, codigo, tipo, estado, desde, consulta_id, notas, creado_por)
  VALUES (v_pac, 'Gastroenteritis aguda', 'K52.9', 'AGUDO', 'ACTIVO', CURRENT_DATE - 1, v_cons, 'DEMO', v_usr);

  UPDATE paciente SET peso_actual = 28.40 WHERE id = v_pac;

  -- Cierre al final (la consulta pasa a ser inmutable).
  UPDATE consulta
     SET estado = 'CERRADA', cerrada_en = v_ini + interval '55 minutes', cerrada_por = v_usr,
         firma_hash = encode(sha256(convert_to('DEMO-K001|' || v_cons || '|' || v_ini::text, 'UTF8')), 'hex')
   WHERE id = v_cons;
END $$;

-- ===================== K002: consulta cerrada de vacunacion (Luna) =====================
DO $$
DECLARE
  v_vet bigint; v_usr bigint; v_sede bigint; v_pac bigint; v_cita bigint; v_cons bigint; v_ini timestamptz;
BEGIN
  IF EXISTS (SELECT 1 FROM consulta WHERE codigo = 'DEMO-K002') THEN RETURN; END IF;
  SELECT v.id, u.id INTO v_vet, v_usr FROM veterinario v JOIN usuario u ON u.id = v.usuario_id WHERE u.username = 'demo.vet';
  SELECT id INTO v_sede FROM sede WHERE es_principal LIMIT 1;
  SELECT id INTO v_pac FROM paciente WHERE codigo = 'DEMO-P002';
  SELECT id, fecha_hora_inicio INTO v_cita, v_ini FROM cita WHERE codigo = 'DEMO-C002';
  IF v_vet IS NULL OR v_pac IS NULL OR v_cita IS NULL THEN RAISE EXCEPTION 'Ejecute antes 01, 02 y 03'; END IF;

  INSERT INTO consulta (codigo, cita_id, paciente_id, veterinario_id, fecha_hora, fecha, sede_id, tipo_consulta, estado,
                        motivo_consulta, motivo, subjetivo, objetivo, evaluacion, plan, diagnostico_principal, diagnostico,
                        observaciones, peso_kg, temperatura_c, frecuencia_cardiaca, frecuencia_respiratoria, creado_el, actualizado_el)
  VALUES ('DEMO-K002', v_cita, v_pac, v_vet, v_ini, (v_ini AT TIME ZONE 'America/Bogota')::date, v_sede, 'VACUNACION', 'ABIERTA',
          'Refuerzo de vacunas anuales', 'Vacunacion anual', 'Sin molestias. Come y juega con normalidad.',
          'Paciente alerta, mucosas rosadas, auscultacion normal.', 'Paciente sano apto para vacunacion.',
          'Aplicar triple felina y antirrabica; proxima dosis en 1 ano.', 'Paciente sano - vacunacion', 'Paciente sano - vacunacion',
          'DEMO: consulta ficticia de demostracion', 3.80, 38.5, 180, 30, now()::timestamp, now()::timestamp)
  RETURNING id INTO v_cons;

  INSERT INTO signo_vital (consulta_id, temperatura, frecuencia_cardiaca, frecuencia_respiratoria, peso_kg, mucosas, escala_dolor, registrado_por)
  VALUES (v_cons, 38.5, 180, 30, 3.80, 'Rosadas', 0, v_usr);

  INSERT INTO diagnostico (consulta_id, descripcion, tipo, orden)
  VALUES (v_cons, 'Paciente sano - control preventivo', 'DEFINITIVO', 1);

  INSERT INTO consulta_vacuna (consulta_id, nombre, lote, fecha_aplicacion, proxima_fecha)
  VALUES (v_cons, 'Triple felina (Panleucopenia, Calicivirus, Herpesvirus-1)', 'DEMO-LV-FEL-01', (v_ini AT TIME ZONE 'America/Bogota')::date, CURRENT_DATE - 2 + 365),
         (v_cons, 'Antirrabica felina', 'DEMO-LV-RAB-01', (v_ini AT TIME ZONE 'America/Bogota')::date, CURRENT_DATE - 2 + 365);

  UPDATE consulta
     SET estado = 'CERRADA', cerrada_en = v_ini + interval '25 minutes', cerrada_por = v_usr,
         firma_hash = encode(sha256(convert_to('DEMO-K002|' || v_cons || '|' || v_ini::text, 'UTF8')), 'hex')
   WHERE id = v_cons;
END $$;

-- ===================== K003: consulta abierta hoy (Mia, paciente en sala) =====================
DO $$
DECLARE
  v_vet bigint; v_usr bigint; v_sede bigint; v_pac bigint; v_cita bigint; v_cons bigint; v_ini timestamptz;
BEGIN
  IF EXISTS (SELECT 1 FROM consulta WHERE codigo = 'DEMO-K003') THEN RETURN; END IF;
  SELECT v.id, u.id INTO v_vet, v_usr FROM veterinario v JOIN usuario u ON u.id = v.usuario_id WHERE u.username = 'demo.vet';
  SELECT id INTO v_sede FROM sede WHERE es_principal LIMIT 1;
  SELECT id INTO v_pac FROM paciente WHERE codigo = 'DEMO-P004';
  SELECT id, fecha_hora_inicio INTO v_cita, v_ini FROM cita WHERE codigo = 'DEMO-C004';
  IF v_vet IS NULL OR v_pac IS NULL OR v_cita IS NULL THEN RAISE EXCEPTION 'Ejecute antes 01, 02 y 03'; END IF;

  INSERT INTO consulta (codigo, cita_id, paciente_id, veterinario_id, fecha_hora, fecha, sede_id, tipo_consulta, estado,
                        motivo_consulta, motivo, subjetivo, objetivo, observaciones, peso_kg, temperatura_c,
                        frecuencia_cardiaca, frecuencia_respiratoria, proxima_revision, creado_el, actualizado_el)
  VALUES ('DEMO-K003', v_cita, v_pac, v_vet, v_ini, (v_ini AT TIME ZONE 'America/Bogota')::date, v_sede, 'CONTROL', 'ABIERTA',
          'Control cardiologico y de peso', 'Control cardiologico',
          'Tos ocasional al despertar, sin intolerancia al ejercicio.',
          'Soplo sistolico grado II/VI en foco mitral. Mucosas rosadas.',
          'DEMO: consulta ficticia abierta (para probar el flujo de cierre)', 6.20, 38.4, 132, 26,
          CURRENT_DATE + 30, now()::timestamp, now()::timestamp)
  RETURNING id INTO v_cons;

  INSERT INTO signo_vital (consulta_id, temperatura, frecuencia_cardiaca, frecuencia_respiratoria, peso_kg, mucosas, tiempo_llenado_capilar, escala_dolor, spo2, registrado_por)
  VALUES (v_cons, 38.4, 132, 26, 6.20, 'Rosadas', 1.5, 0, 97.0, v_usr);

  INSERT INTO problema_activo (paciente_id, descripcion, tipo, estado, desde, consulta_id, notas, creado_por)
  SELECT v_pac, 'Soplo cardiaco grado II (valvulopatia mitral incipiente)', 'CRONICO', 'EN_CONTROL', CURRENT_DATE - 200, v_cons, 'DEMO', v_usr
  WHERE NOT EXISTS (SELECT 1 FROM problema_activo WHERE paciente_id = v_pac AND notas = 'DEMO');
END $$;
