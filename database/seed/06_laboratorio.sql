-- DATOS DE DEMOSTRACION (ficticios). Idempotente (cada bloque se omite si la orden DEMO-Lnnn ya existe).
--   DEMO-L001: hemograma de Max, COMPLETADA con resultados VALIDADOS y banderas (leucocitosis, neutrofilia).
--   DEMO-L002: ecografia abdominal de Max con informe de imagen VALIDADO.
--   DEMO-L003: hemograma y quimica de Rocky SOLICITADA (para probar la toma de muestra / captura de resultados).
-- Los resultados e informes validados son inmutables: se insertan ya en su estado final (no se actualizan).
SET client_min_messages = warning;
SET TIME ZONE 'America/Bogota';
SET app.auditoria_app = 'on';

-- ===================== L001: hemograma validado =====================
DO $$
DECLARE
  v_usr bigint; v_sede bigint; v_pac bigint; v_cons bigint; v_ord bigint; v_oi bigint; v_val bigint; v_ex bigint;
  v_especie bigint; v_sexo char(1); v_meses int; v_ini timestamptz;
BEGIN
  IF EXISTS (SELECT 1 FROM orden_examen WHERE codigo = 'DEMO-L001') THEN RETURN; END IF;
  SELECT id INTO v_usr FROM usuario WHERE username = 'demo.vet';
  SELECT id INTO v_sede FROM sede WHERE es_principal LIMIT 1;
  SELECT id, especie_id, sexo, (extract(year FROM age(CURRENT_DATE, fecha_nacimiento)) * 12 + extract(month FROM age(CURRENT_DATE, fecha_nacimiento)))::int
    INTO v_pac, v_especie, v_sexo, v_meses FROM paciente WHERE codigo = 'DEMO-P001';
  SELECT id, fecha_hora INTO v_cons, v_ini FROM consulta WHERE codigo = 'DEMO-K001';
  SELECT id INTO v_ex FROM examen_catalogo WHERE codigo = 'HEMO';
  IF v_usr IS NULL OR v_pac IS NULL OR v_cons IS NULL OR v_ex IS NULL THEN RAISE EXCEPTION 'Ejecute antes 01 a 04 (y las migraciones)'; END IF;

  INSERT INTO orden_examen (codigo, sede_id, paciente_id, consulta_id, solicitado_por, prioridad, estado, indicacion_clinica,
                            solicitada_en, muestra_tomada_en, muestra_tomada_por, muestra_observaciones, completada_en)
  VALUES ('DEMO-L001', v_sede, v_pac, v_cons, v_usr, 'URGENTE', 'COMPLETADA', 'Gastroenteritis: descartar proceso infeccioso e inflamatorio',
          v_ini + interval '10 minutes', v_ini + interval '15 minutes', v_usr, 'Sangre EDTA, vena cefalica (DEMO)', v_ini + interval '50 minutes')
  RETURNING id INTO v_ord;

  INSERT INTO orden_examen_item (orden_id, examen_id, estado, interpretacion, validado_por, validado_en, capturado_por, capturado_en, version_actual)
  VALUES (v_ord, v_ex, 'VALIDADO', 'Leucocitosis con neutrofilia, compatible con proceso inflamatorio/infeccioso. Resto del hemograma dentro de rangos.',
          v_usr, v_ini + interval '50 minutes', v_usr, v_ini + interval '40 minutes', 1)
  RETURNING id INTO v_oi;

  INSERT INTO orden_item_validacion (orden_item_id, version, tipo, interpretacion, validado_por, validado_en)
  VALUES (v_oi, 1, 'VALIDACION', 'Leucocitosis con neutrofilia, compatible con proceso inflamatorio/infeccioso. Resto del hemograma dentro de rangos.',
          v_usr, v_ini + interval '50 minutes')
  RETURNING id INTO v_val;

  INSERT INTO resultado_examen (orden_item_id, parametro_id, valor_numerico, valor_texto, unidad, ref_min, ref_max, bandera, critico,
                                ref_critico_min, ref_critico_max, rango_id, registrado_por, origen, estado, validacion_id, registrado_en)
  SELECT v_oi, p.id, x.valor, x.texto, p.unidad, r.valor_min, r.valor_max,
         CASE WHEN x.valor IS NULL OR r.valor_min IS NULL THEN NULL
              WHEN r.critico_min IS NOT NULL AND x.valor < r.critico_min THEN 'LL'
              WHEN r.critico_max IS NOT NULL AND x.valor > r.critico_max THEN 'HH'
              WHEN x.valor < r.valor_min THEN 'L'
              WHEN x.valor > r.valor_max THEN 'H'
              ELSE 'N' END,
         COALESCE((r.critico_min IS NOT NULL AND x.valor < r.critico_min) OR (r.critico_max IS NOT NULL AND x.valor > r.critico_max), false),
         r.critico_min, r.critico_max, r.id, v_usr, 'MANUAL', 'VALIDADO', v_val, v_ini + interval '40 minutes'
  FROM (VALUES
    ('HCT', 52.0::numeric, NULL::text), ('HGB', 17.5, NULL), ('RBC', 8.1, NULL), ('WBC', 21.5, NULL), ('PLT', 310, NULL),
    ('MCV', 70, NULL), ('MCH', 22.0, NULL), ('MCHC', 34.0, NULL), ('RDW', 14.0, NULL), ('NEUT', 17.2, NULL),
    ('LINF', 2.3, NULL), ('MONO', 1.1, NULL), ('EOS', 0.4, NULL), ('BASO', 0.05, NULL),
    ('FROT', NULL, 'Neutrofilos con ligera toxicidad. Sin hemoparasitos observados.')
  ) AS x(codigo, valor, texto)
  JOIN examen_parametro p ON p.examen_id = v_ex AND p.codigo = x.codigo
  LEFT JOIN LATERAL (
      SELECT * FROM rango_referencia rr
       WHERE rr.parametro_id = p.id AND rr.especie_id = v_especie
         AND (rr.sexo IS NULL OR rr.sexo = v_sexo)
         AND (rr.edad_min_meses IS NULL OR rr.edad_min_meses <= v_meses)
         AND (rr.edad_max_meses IS NULL OR rr.edad_max_meses >= v_meses)
       ORDER BY rr.id LIMIT 1) r ON true;
END $$;

-- ===================== L002: ecografia abdominal con informe validado =====================
DO $$
DECLARE
  v_usr bigint; v_sede bigint; v_pac bigint; v_cons bigint; v_ord bigint; v_oi bigint; v_val bigint; v_ex bigint; v_ini timestamptz;
BEGIN
  IF EXISTS (SELECT 1 FROM orden_examen WHERE codigo = 'DEMO-L002') THEN RETURN; END IF;
  SELECT id INTO v_usr FROM usuario WHERE username = 'demo.vet';
  SELECT id INTO v_sede FROM sede WHERE es_principal LIMIT 1;
  SELECT id INTO v_pac FROM paciente WHERE codigo = 'DEMO-P001';
  SELECT id, fecha_hora INTO v_cons, v_ini FROM consulta WHERE codigo = 'DEMO-K001';
  SELECT id INTO v_ex FROM examen_catalogo WHERE codigo = 'ECO-ABD';
  IF v_usr IS NULL OR v_pac IS NULL OR v_cons IS NULL OR v_ex IS NULL THEN RAISE EXCEPTION 'Ejecute antes 01 a 04 (y las migraciones)'; END IF;

  INSERT INTO orden_examen (codigo, sede_id, paciente_id, consulta_id, solicitado_por, prioridad, estado, indicacion_clinica,
                            solicitada_en, completada_en)
  VALUES ('DEMO-L002', v_sede, v_pac, v_cons, v_usr, 'RUTINA', 'COMPLETADA', 'Dolor abdominal leve: descartar cuerpo extrano u obstruccion',
          v_ini + interval '12 minutes', v_ini + interval '58 minutes')
  RETURNING id INTO v_ord;

  INSERT INTO orden_examen_item (orden_id, examen_id, estado, interpretacion, validado_por, validado_en, capturado_por, capturado_en, version_actual)
  VALUES (v_ord, v_ex, 'VALIDADO', 'Hallazgos compatibles con gastroenteritis, sin evidencia de obstruccion.',
          v_usr, v_ini + interval '58 minutes', v_usr, v_ini + interval '55 minutes', 1)
  RETURNING id INTO v_oi;

  INSERT INTO orden_item_validacion (orden_item_id, version, tipo, interpretacion, validado_por, validado_en)
  VALUES (v_oi, 1, 'VALIDACION', 'Hallazgos compatibles con gastroenteritis, sin evidencia de obstruccion.', v_usr, v_ini + interval '58 minutes')
  RETURNING id INTO v_val;

  INSERT INTO informe_imagen (orden_item_id, version, estado, region, tecnica, hallazgos, impresion_diagnostica, recomendaciones,
                              equipo, fecha_estudio, informado_por, validacion_id, creado_por)
  VALUES (v_oi, 1, 'VALIDADO', 'Abdomen completo',
          'Ecografia abdominal con transductor microconvexo 5-8 MHz, paciente en decubito dorsal.',
          'Asas intestinales con contenido liquido y peristaltismo aumentado. Pared intestinal de grosor normal con estratificacion conservada. Higado, bazo, rinones y vejiga sin alteraciones. Sin liquido libre. No se observan cuerpos extranos ni signos de obstruccion.',
          'Enteritis leve inespecifica.',
          'Manejo medico y control clinico en 72 horas; repetir estudio si empeora.',
          'Ecografo demo (ficticio)', (v_ini AT TIME ZONE 'America/Bogota')::date, v_usr, v_val, v_usr);
END $$;

-- ===================== L003: orden solicitada (pendiente de muestra) =====================
DO $$
DECLARE v_usr bigint; v_sede bigint; v_pac bigint; v_ord bigint;
BEGIN
  IF EXISTS (SELECT 1 FROM orden_examen WHERE codigo = 'DEMO-L003') THEN RETURN; END IF;
  SELECT id INTO v_usr FROM usuario WHERE username = 'demo.vet';
  SELECT id INTO v_sede FROM sede WHERE es_principal LIMIT 1;
  SELECT id INTO v_pac FROM paciente WHERE codigo = 'DEMO-P003';
  IF v_usr IS NULL OR v_pac IS NULL THEN RAISE EXCEPTION 'Ejecute antes 01 y 02'; END IF;

  INSERT INTO orden_examen (codigo, sede_id, paciente_id, solicitado_por, prioridad, estado, indicacion_clinica, solicitada_en)
  VALUES ('DEMO-L003', v_sede, v_pac, v_usr, 'RUTINA', 'SOLICITADA', 'Chequeo anual y control de dermatitis (DEMO)', now())
  RETURNING id INTO v_ord;

  INSERT INTO orden_examen_item (orden_id, examen_id, estado)
  SELECT v_ord, e.id, 'SOLICITADO' FROM examen_catalogo e WHERE e.codigo IN ('HEMO', 'QUIM');
END $$;
