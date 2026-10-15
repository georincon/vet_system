-- ============================================================================
-- V30 - Semillas de la Etapa A: paneles de laboratorio caninos y felinos, pruebas rapidas, examenes de imagen y
-- patologia, con LOINC/UCUM por parametro cuando se conocen.
--
-- AVISO (validacion pendiente):
--  * Los RANGOS DE REFERENCIA son ORIENTATIVOS (valores publicados de uso comun en perros y gatos adultos); deben
--    validarse con el laboratorio veterinario que procese las muestras (metodo y equipo cambian el rango).
--  * Los codigos LOINC son de MEDICINA HUMANA (LOINC no tiene catalogo veterinario completo). Solo se asignaron los que
--    se conocen con seguridad; el resto queda NULL. TODOS quedan en estado POR_VALIDAR hasta que el laboratorio los confirme.
--  * Las unidades UCUM se derivan de la unidad de despliegue (mapa al final).
-- ============================================================================

-- ---------------------------------------------------------------- examenes (paneles)
INSERT INTO examen_catalogo (item_id, codigo, nombre, tipo, muestra, requiere_ayuno, tiempo_entrega_horas, categoria, descripcion)
SELECT (SELECT id FROM item WHERE codigo = v.item), v.codigo, v.nombre, v.tipo, v.muestra, v.ayuno, v.horas, v.cat, v.descr
FROM (VALUES
 (NULL,           'HEPA',     'Perfil hepatico',                     'LAB',       'Suero / heparina', TRUE,  4,  'QUIMICA',     'ALT, AST, ALP, GGT, bilirrubina total, albumina'),
 (NULL,           'RENAL',    'Perfil renal',                        'LAB',       'Suero / heparina', FALSE, 4,  'QUIMICA',     'BUN, creatinina, fosforo, calcio, proteinas, albumina'),
 (NULL,           'ELEC',     'Electrolitos',                        'LAB',       'Suero / heparina', FALSE, 2,  'QUIMICA',     'Sodio, potasio, cloro, calcio, fosforo'),
 ('LAB-UROA',     'UROA',     'Uroanalisis',                         'LAB',       'Orina',            FALSE, 2,  'UROANALISIS', 'Examen fisico, quimico y sedimento'),
 ('LAB-COPRO',    'COPRO',    'Coproscopico',                        'LAB',       'Heces',            FALSE, 4,  'PARASITOLOGIA','Examen directo y flotacion'),
 ('LAB-PARVO',    'PARVO',    'Test rapido parvovirus',              'LAB',       'Heces',            FALSE, 1,  'PRUEBA_RAPIDA','Antigeno de parvovirus canino'),
 ('LAB-FELV-FIV', 'FELVFIV',  'Test rapido FeLV/FIV',                'LAB',       'Sangre / suero',   FALSE, 1,  'PRUEBA_RAPIDA','Antigeno FeLV y anticuerpos FIV'),
 ('LAB-4DX',      'HEMOPAR',  'Hemoparasitos / Ehrlichia (4Dx)',     'LAB',       'Sangre / suero',   FALSE, 1,  'PRUEBA_RAPIDA','Ehrlichia, Anaplasma, Borrelia, Dirofilaria y frotis'),
 ('IMG-RX',       'RX',       'Radiografia',                         'IMAGEN',    NULL,               FALSE, 1,  'IMAGEN',      'Estudio radiografico por region/proyeccion'),
 ('IMG-ECO-ABD',  'ECO-ABD',  'Ecografia abdominal',                 'IMAGEN',    NULL,               FALSE, 2,  'IMAGEN',      'Ecografia abdominal'),
 ('IMG-ECO-CARD', 'ECO-CARD', 'Ecocardiograma',                      'IMAGEN',    NULL,               FALSE, 2,  'IMAGEN',      'Ecocardiografia'),
 ('IMG-ECG',      'ECG',      'Electrocardiograma',                  'FUNCIONAL', NULL,               FALSE, 1,  'FUNCIONAL',   'Registro electrocardiografico'),
 (NULL,           'CITO',     'Citologia',                           'PATOLOGIA', 'Aspirado / impronta',FALSE,48, 'PATOLOGIA',   'Citologia de masa, aspirado o impronta'),
 (NULL,           'HISTO',    'Histopatologia',                      'PATOLOGIA', 'Tejido en formol', FALSE, 120,'PATOLOGIA',   'Estudio histopatologico de biopsia o pieza quirurgica')
) AS v(item, codigo, nombre, tipo, muestra, ayuno, horas, cat, descr)
ON CONFLICT (codigo) DO NOTHING;

UPDATE examen_catalogo SET categoria = 'HEMATOLOGIA', descripcion = COALESCE(descripcion, 'Hemograma completo con indices y formula leucocitaria') WHERE codigo = 'HEMO';
UPDATE examen_catalogo SET categoria = 'QUIMICA',     descripcion = COALESCE(descripcion, 'Glucosa, renal, hepatico y proteinas basicas') WHERE codigo = 'QUIM';

-- ---------------------------------------------------------------- parametros
INSERT INTO examen_parametro (examen_id, codigo, nombre, unidad, orden, tipo_valor, valor_normal_texto, opciones)
SELECT e.id, v.codigo, v.nombre, v.unidad, v.orden, v.tipo, v.normal, v.opciones
FROM (VALUES
 -- hemograma: indices y formula (HCT, HGB, RBC, WBC, PLT ya sembrados en V20)
 ('HEMO','MCV',  'VCM (volumen corpuscular medio)', 'fL',       6,  'NUMERICO', NULL, NULL),
 ('HEMO','MCH',  'HCM (hemoglobina corpuscular media)','pg',    7,  'NUMERICO', NULL, NULL),
 ('HEMO','MCHC', 'CHCM',                            'g/dL',     8,  'NUMERICO', NULL, NULL),
 ('HEMO','RDW',  'RDW',                             '%',        9,  'NUMERICO', NULL, NULL),
 ('HEMO','NEUT', 'Neutrofilos',                     'x10^3/uL', 10, 'NUMERICO', NULL, NULL),
 ('HEMO','LINF', 'Linfocitos',                      'x10^3/uL', 11, 'NUMERICO', NULL, NULL),
 ('HEMO','MONO', 'Monocitos',                       'x10^3/uL', 12, 'NUMERICO', NULL, NULL),
 ('HEMO','EOS',  'Eosinofilos',                     'x10^3/uL', 13, 'NUMERICO', NULL, NULL),
 ('HEMO','BASO', 'Basofilos',                       'x10^3/uL', 14, 'NUMERICO', NULL, NULL),
 ('HEMO','FROT', 'Frotis / morfologia',             NULL,       15, 'TEXTO',    NULL, NULL),
 -- perfil hepatico
 ('HEPA','ALT',  'ALT (TGP)',                       'U/L',      1,  'NUMERICO', NULL, NULL),
 ('HEPA','AST',  'AST (TGO)',                       'U/L',      2,  'NUMERICO', NULL, NULL),
 ('HEPA','ALP',  'Fosfatasa alcalina',              'U/L',      3,  'NUMERICO', NULL, NULL),
 ('HEPA','GGT',  'GGT',                             'U/L',      4,  'NUMERICO', NULL, NULL),
 ('HEPA','BILT', 'Bilirrubina total',               'mg/dL',    5,  'NUMERICO', NULL, NULL),
 ('HEPA','ALB',  'Albumina',                        'g/dL',     6,  'NUMERICO', NULL, NULL),
 -- perfil renal
 ('RENAL','BUN', 'Nitrogeno ureico (BUN)',          'mg/dL',    1,  'NUMERICO', NULL, NULL),
 ('RENAL','CREA','Creatinina',                      'mg/dL',    2,  'NUMERICO', NULL, NULL),
 ('RENAL','PHOS','Fosforo',                         'mg/dL',    3,  'NUMERICO', NULL, NULL),
 ('RENAL','CA',  'Calcio total',                    'mg/dL',    4,  'NUMERICO', NULL, NULL),
 ('RENAL','TP',  'Proteinas totales',               'g/dL',     5,  'NUMERICO', NULL, NULL),
 ('RENAL','ALB', 'Albumina',                        'g/dL',     6,  'NUMERICO', NULL, NULL),
 -- electrolitos
 ('ELEC','NA',   'Sodio',                           'mmol/L',   1,  'NUMERICO', NULL, NULL),
 ('ELEC','K',    'Potasio',                         'mmol/L',   2,  'NUMERICO', NULL, NULL),
 ('ELEC','CL',   'Cloro',                           'mmol/L',   3,  'NUMERICO', NULL, NULL),
 ('ELEC','CA',   'Calcio total',                    'mg/dL',    4,  'NUMERICO', NULL, NULL),
 ('ELEC','PHOS', 'Fosforo',                         'mg/dL',    5,  'NUMERICO', NULL, NULL),
 -- uroanalisis
 ('UROA','COLOR','Color',                           NULL,       1,  'TEXTO',       NULL, NULL),
 ('UROA','ASPEC','Aspecto',                         NULL,       2,  'TEXTO',       NULL, NULL),
 ('UROA','DENS', 'Densidad urinaria',               NULL,       3,  'NUMERICO',    NULL, NULL),
 ('UROA','PH',   'pH',                              NULL,       4,  'NUMERICO',    NULL, NULL),
 ('UROA','PROT', 'Proteinas',                       NULL,       5,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|TRAZAS|+|++|+++'),
 ('UROA','GLUC', 'Glucosa',                         NULL,       6,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|+|++|+++'),
 ('UROA','CET',  'Cetonas',                         NULL,       7,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|TRAZAS|+|++|+++'),
 ('UROA','BILI', 'Bilirrubina',                     NULL,       8,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|+|++|+++'),
 ('UROA','SANG', 'Sangre oculta',                   NULL,       9,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|TRAZAS|+|++|+++'),
 ('UROA','SED',  'Sedimento urinario',              NULL,       10, 'TEXTO',       NULL, NULL),
 -- coproscopico
 ('COPRO','CONS','Consistencia',                    NULL,       1,  'TEXTO',       NULL, NULL),
 ('COPRO','COLOR','Color',                          NULL,       2,  'TEXTO',       NULL, NULL),
 ('COPRO','PARAS','Parasitos (examen directo)',     NULL,       3,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|POSITIVO'),
 ('COPRO','HUEVOS','Huevos / ooquistes (flotacion)',NULL,       4,  'TEXTO',       NULL, NULL),
 ('COPRO','PROTOZ','Protozoarios (Giardia, otros)', NULL,       5,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|POSITIVO'),
 ('COPRO','SANGRE','Sangre oculta',                 NULL,       6,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|POSITIVO'),
 -- pruebas rapidas
 ('PARVO','PARVO','Parvovirus canino (antigeno)',   NULL,       1,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|POSITIVO'),
 ('FELVFIV','FELV','FeLV (antigeno)',               NULL,       1,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|POSITIVO'),
 ('FELVFIV','FIV','FIV (anticuerpos)',              NULL,       2,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|POSITIVO'),
 ('HEMOPAR','EHRL','Ehrlichia',                     NULL,       1,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|POSITIVO'),
 ('HEMOPAR','ANAP','Anaplasma',                     NULL,       2,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|POSITIVO'),
 ('HEMOPAR','BORR','Borrelia',                      NULL,       3,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|POSITIVO'),
 ('HEMOPAR','DIRO','Dirofilaria (antigeno)',        NULL,       4,  'CUALITATIVO', 'NEGATIVO', 'NEGATIVO|POSITIVO'),
 ('HEMOPAR','FROTIS','Frotis sanguineo (hemoparasitos)', NULL,  5,  'TEXTO',       NULL, NULL)
) AS v(ex, codigo, nombre, unidad, orden, tipo, normal, opciones)
JOIN examen_catalogo e ON e.codigo = v.ex
ON CONFLICT (examen_id, codigo) DO NOTHING;

-- ---------------------------------------------------------------- LOINC (humano, POR_VALIDAR) solo los conocidos con seguridad
UPDATE examen_parametro p SET codigo_loinc = v.loinc
FROM (VALUES
 ('HEMO','HCT','4544-3'), ('HEMO','HGB','718-7'), ('HEMO','RBC','789-8'), ('HEMO','WBC','6690-2'), ('HEMO','PLT','777-3'),
 ('HEMO','MCV','787-2'),  ('HEMO','MCH','785-6'), ('HEMO','MCHC','786-4'), ('HEMO','RDW','788-0'),
 ('HEMO','NEUT','751-8'), ('HEMO','LINF','731-0'),('HEMO','MONO','742-7'), ('HEMO','EOS','711-2'), ('HEMO','BASO','704-7'),
 ('QUIM','GLU','2345-7'), ('QUIM','BUN','3094-0'),('QUIM','CREA','2160-0'),('QUIM','ALT','1742-6'),('QUIM','ALP','6768-6'),
 ('QUIM','TP','2885-2'),  ('QUIM','ALB','1751-7'),
 ('HEPA','ALT','1742-6'), ('HEPA','AST','1920-8'),('HEPA','ALP','6768-6'),('HEPA','GGT','2324-2'),('HEPA','BILT','1975-2'),('HEPA','ALB','1751-7'),
 ('RENAL','BUN','3094-0'),('RENAL','CREA','2160-0'),('RENAL','PHOS','2777-1'),('RENAL','CA','17861-6'),('RENAL','TP','2885-2'),('RENAL','ALB','1751-7'),
 ('ELEC','NA','2951-2'),  ('ELEC','K','2823-3'),   ('ELEC','CL','2075-0'),   ('ELEC','CA','17861-6'),('ELEC','PHOS','2777-1'),
 ('UROA','DENS','2965-2'),('UROA','PH','2756-5')
) AS v(ex, par, loinc)
JOIN examen_catalogo e ON e.codigo = v.ex
WHERE p.examen_id = e.id AND p.codigo = v.par;

-- ---------------------------------------------------------------- UCUM derivado de la unidad de despliegue
UPDATE examen_parametro SET unidad_ucum = CASE unidad
    WHEN 'x10^6/uL' THEN '10*6/uL'
    WHEN 'x10^3/uL' THEN '10*3/uL'
    WHEN '%'        THEN '%'
    WHEN 'g/dL'     THEN 'g/dL'
    WHEN 'mg/dL'    THEN 'mg/dL'
    WHEN 'U/L'      THEN 'U/L'
    WHEN 'fL'       THEN 'fL'
    WHEN 'pg'       THEN 'pg'
    WHEN 'mmol/L'   THEN 'mmol/L'
    ELSE NULL END
WHERE unidad IS NOT NULL AND unidad_ucum IS NULL;
UPDATE examen_parametro SET unidad_ucum = '1' WHERE codigo = 'DENS' AND unidad IS NULL AND unidad_ucum IS NULL;

-- ---------------------------------------------------------------- rangos de referencia ORIENTATIVOS (adultos)
INSERT INTO rango_referencia (parametro_id, especie_id, valor_min, valor_max, fuente)
SELECT p.id, (SELECT id FROM especie WHERE nombre = v.esp), v.vmin, v.vmax,
       'Orientativo adultos (por validar con el laboratorio)'
FROM (VALUES
 ('HEMO','MCV','Perro',60,77),      ('HEMO','MCV','Gato',39,55),
 ('HEMO','MCH','Perro',19.5,24.5),  ('HEMO','MCH','Gato',13,17),
 ('HEMO','MCHC','Perro',32,36),     ('HEMO','MCHC','Gato',30,36),
 ('HEMO','NEUT','Perro',3,11.5),    ('HEMO','NEUT','Gato',2.5,12.5),
 ('HEMO','LINF','Perro',1,4.8),     ('HEMO','LINF','Gato',1.5,7),
 ('HEMO','MONO','Perro',0.15,1.35), ('HEMO','MONO','Gato',0,0.9),
 ('HEMO','EOS','Perro',0.1,1.25),   ('HEMO','EOS','Gato',0.1,1.5),
 ('HEMO','BASO','Perro',0,0.1),     ('HEMO','BASO','Gato',0,0.1),
 ('HEPA','ALT','Perro',10,100),     ('HEPA','ALT','Gato',12,130),
 ('HEPA','AST','Perro',0,50),       ('HEPA','AST','Gato',0,48),
 ('HEPA','ALP','Perro',23,212),     ('HEPA','ALP','Gato',14,111),
 ('HEPA','GGT','Perro',0,10),       ('HEPA','GGT','Gato',0,4),
 ('HEPA','BILT','Perro',0,0.9),     ('HEPA','BILT','Gato',0,0.9),
 ('HEPA','ALB','Perro',2.3,4.0),    ('HEPA','ALB','Gato',2.2,4.0),
 ('RENAL','BUN','Perro',7,27),      ('RENAL','BUN','Gato',16,36),
 ('RENAL','CREA','Perro',0.5,1.8),  ('RENAL','CREA','Gato',0.8,2.4),
 ('RENAL','PHOS','Perro',2.5,6.8),  ('RENAL','PHOS','Gato',3.1,7.5),
 ('RENAL','CA','Perro',7.9,12.0),   ('RENAL','CA','Gato',8.2,10.8),
 ('RENAL','TP','Perro',5.2,8.2),    ('RENAL','TP','Gato',5.7,8.9),
 ('RENAL','ALB','Perro',2.3,4.0),   ('RENAL','ALB','Gato',2.2,4.0),
 ('ELEC','NA','Perro',144,160),     ('ELEC','NA','Gato',150,165),
 ('ELEC','K','Perro',3.5,5.8),      ('ELEC','K','Gato',3.5,5.8),
 ('ELEC','CL','Perro',109,122),     ('ELEC','CL','Gato',112,129),
 ('ELEC','CA','Perro',7.9,12.0),    ('ELEC','CA','Gato',8.2,10.8),
 ('ELEC','PHOS','Perro',2.5,6.8),   ('ELEC','PHOS','Gato',3.1,7.5),
 ('UROA','DENS','Perro',1.015,1.045),('UROA','DENS','Gato',1.035,1.060),
 ('UROA','PH','Perro',5.5,7.5),     ('UROA','PH','Gato',5.5,7.5)
) AS v(ex, par, esp, vmin, vmax)
JOIN examen_catalogo e ON e.codigo = v.ex
JOIN examen_parametro p ON p.examen_id = e.id AND p.codigo = v.par
ON CONFLICT DO NOTHING;

-- Rangos juveniles (mas especificos: ganan sobre el adulto cuando la edad cae en el intervalo)
INSERT INTO rango_referencia (parametro_id, especie_id, edad_min_meses, edad_max_meses, valor_min, valor_max, fuente)
SELECT p.id, (SELECT id FROM especie WHERE nombre = v.esp), 0, 3, v.vmin, v.vmax,
       'Orientativo cachorro/gatito <4 meses (por validar con el laboratorio)'
FROM (VALUES
 ('HCT','Perro',26,43), ('HGB','Perro',8.5,14.5),
 ('HCT','Gato',24,40),  ('HGB','Gato',8,13)
) AS v(par, esp, vmin, vmax)
JOIN examen_catalogo e ON e.codigo = 'HEMO'
JOIN examen_parametro p ON p.examen_id = e.id AND p.codigo = v.par
ON CONFLICT DO NOTHING;

-- Valores criticos orientativos (bandera LL/HH y marca de critico)
UPDATE rango_referencia r SET critico_min = v.cmin, critico_max = v.cmax
FROM (VALUES
 ('HEMO','HCT',15,NULL),  ('HEMO','PLT',30,NULL),
 ('QUIM','GLU',40,500),   ('ELEC','K',2.5,7.5),   ('ELEC','NA',120,170),
 ('RENAL','CREA',NULL,8)
) AS v(ex, par, cmin, cmax)
JOIN examen_catalogo e ON e.codigo = v.ex
JOIN examen_parametro p ON p.examen_id = e.id AND p.codigo = v.par
WHERE r.parametro_id = p.id AND r.edad_min_meses IS NULL;
