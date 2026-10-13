# Razas caninas de Colombia (catálogo `raza`, migración V23)

Migración: `backend/src/main/resources/db/migration/V23__razas_perros_fci_colombia.sql` (idempotente, `ON CONFLICT (especie_id, nombre) DO UPDATE`).

## Criterio

- En Colombia el registro genealógico lo lleva la Asociación Club Canino Colombiano (ACCC), miembro de la FCI. Se adopta por tanto la nomenclatura FCI como estándar.
- Se cargan las razas FCI con reconocimiento definitivo (344) y provisional (20), más 6 entradas `NO_FCI` de uso frecuente en clínica: `Criollo / Mestizo (sin raza definida)`, `Criollo (Sabanero)`, `Pit Bull Terrier`, `American Pit Bull Terrier`, `American Bully`, `American Bulldog`. Total: 370 filas para la especie `Perro`.
- Nombre en español cuando hay uso común; el nombre FCI original va entre paréntesis si difiere.
- Columnas nuevas en `raza`: `grupo_fci` (1..10), `numero_fci`, `origen`, `tamano` (MINI/PEQUENO/MEDIANO/GRANDE/GIGANTE), `reconocimiento_fci` (DEFINITIVO/PROVISIONAL/NO_FCI), `potencialmente_peligrosa`.
- Tamaño: aproximación propia según estándar (MINI = toy, aprox. < 5 kg). Se deja NULL en razas con varias tallas (Poodle, Spitz Alemán/Pomerania, Xoloitzcuintle, Perro sin Pelo del Perú, Podengo Portugués) y en las 6 entradas NO_FCI sin talla clara (Criollo x2, etc.).
- Dachshund (FCI 148) es una sola raza con 9 variedades; se carga una fila (`Teckel (Dachshund)`). Pastor Belga (FCI 15) también una fila con sus 4 variedades.
- Renombres de filas sembradas en V11 (se actualiza por nombre; las FK usan id): Doberman Pinscher -> Dóberman (Dobermann); Dachshund (Teckel) -> Teckel (Dachshund); Pomerania -> Spitz Alemán (Pomerania); Cocker Spaniel -> Cocker Spaniel Inglés; Springer Spaniel -> Springer Spaniel Inglés; Pastor Collie -> Collie de Pelo Largo; Vizsla -> Vizsla Húngaro de Pelo Corto; Schnauzer -> Schnauzer Mediano.

## Conteo por grupo FCI

| Grupo | Nombre | Razas |
|---|---|---|
| 1 | Perros de pastor y boyeros (excepto boyeros suizos) | 48 |
| 2 | Pinscher, Schnauzer, molosoides y boyeros suizos | 57 |
| 3 | Terriers | 36 |
| 4 | Teckels | 1 |
| 5 | Spitz y tipo primitivo | 48 |
| 6 | Sabuesos y razas semejantes | 75 |
| 7 | Perros de muestra | 36 |
| 8 | Cobradores, levantadores y perros de agua | 22 |
| 9 | Perros de compañía y de juguete | 27 |
| 10 | Lebreles | 14 |
| - | Sin grupo FCI (NO_FCI) | 6 |
| | Total | 370 |

Del total FCI, 20 son provisionales (incluye el Sabueso Fino Colombiano, FCI nº 376, grupo 6, reconocimiento provisional el 11-02-2026, primera raza colombiana reconocida; sin CACIB hasta el reconocimiento definitivo).

## Razas potencialmente peligrosas (13 filas)

`potencialmente_peligrosa = TRUE`: American Staffordshire Terrier, Bullmastiff, Dóberman, Dogo Argentino, Dogo de Burdeos, Fila Brasileiro, Mastín Napolitano, Pit Bull Terrier, American Pit Bull Terrier, Presa Canario, Rottweiler, Staffordshire Bull Terrier, Tosa Japonés.

Referencia legal:
- Ley 746 de 2002, art. 2 que adiciona el art. 108-F al Código Nacional de Policía (Decreto 1355 de 1970): la lista incluye, además de perros con antecedentes de agresión o adiestrados para ataque, "los de las siguientes razas o sus cruces o híbridos": American Staffordshire Terrier, Bullmastiff, Dóberman, Dogo Argentino, Dogo de Burdeos, Fila Brasileiro, Mastín Napolitano, Pit Bull Terrier, American Pit Bull Terrier, De presa canario, Rottweiler, Staffordshire Terrier y Tosa Japonés. Verificado en el texto de la Cancillería.
- Ley 1801 de 2016 (Código Nacional de Seguridad y Convivencia Ciudadana), art. 126 y siguientes, retoma la lista (parcialmente revisada por la Corte Constitucional, Sentencia C-059/18); el Gobierno puede ampliar razas. Las fuentes consultadas indican que esta ley derogó la 746 en lo regulado (art. 242).
- La ley cubre también los cruces e híbridos: el flag aplica a la raza pura; el cruce debe tratarse a nivel de la ficha del paciente (no modelado en V23).
- "Staffordshire Terrier" de la ley se interpretó como Staffordshire Bull Terrier (FCI 76).

## Fuentes

- FCI, nomenclatura por grupos (consultadas 2026-10-13): https://www.fci.be/en/nomenclature/ ; páginas de grupo 1-10, p. ej. https://www.fci.be/en/nomenclature/3-Terriers.html y https://www.fci.be/en/nomenclature/1-Sheepdogs-and-Cattledogs-except-Swiss-Cattledogs-44.html
- FCI, razas provisionales: https://www.fci.be/en/Nomenclature/Default.aspx?group=1
- Sabueso Fino Colombiano: https://www.fci.be/EN/nomenclature/SABUESO-FINO-COLOMBIANO-376.html ; circular ACCC https://accc.com.co/wp-content/uploads/2026/03/4-2026.pdf ; https://www.agronegocios.co/mascotas/la-fci-reconoce-oficialmente-al-sabueso-fino-como-la-primera-raza-canina-colombiana-4327001
- Ley 746 de 2002: https://www.cancilleria.gov.co/sites/default/files/Normograma/docs/ley_0746_2002.htm ; https://www.icbf.gov.co/cargues/avance/compilacion/docs/ley_0746_2002.htm
- Ley 1801 de 2016, art. 126: https://leyes.co/codigo_nacional_de_seguridad_y_convivencia_ciudadana/126.htm ; Sentencia C-059/18: https://www.ambitojuridico.com/sites/default/files/BancoMedios/Archivos/sent-c-059-18.pdf

## Limitaciones

- Las listas de grupo se extrajeron de las páginas de fci.be mediante lectura automatizada (resumen por modelo); no se contrastó fila a fila con los PDF de estándar. Pueden faltar o sobrar razas respecto a la nomenclatura vigente (FCI cuenta ~350 definitivas; aquí 344) y deben revisarse contra el A-Z oficial.
- Algunas razas definitivas recientes o con variedades podrían estar fusionadas en una sola fila (Pastor Belga, Teckel, Poodle, Spitz Alemán).
- Provisionales: la lista (20) refleja la página FCI consultada; algunas pueden haber pasado a definitivas. Grupo, origen y talla de los provisionales (salvo el Sabueso Fino Colombiano) se asignaron de memoria y no se verificaron; el grupo 2 de la Karaman Macedonia (378) es el más incierto.
- Tamaño y traducciones al español son criterio propio, no oficial. Los orígenes de algunas razas (Landseer, Perro sin Pelo, etc.) siguen la ficha FCI.
- Las entradas NO_FCI (Pit Bull, American Bully, American Bulldog) son razas de otros registros (UKC/ADBA) sin estándar FCI; no se verificó una lista de razas reconocidas por la ACCC fuera de FCI.
- No se verificó el texto vigente del art. 126 Ley 1801 ni decretos reglamentarios posteriores; la 746 puede estar derogada en parte.
- V11 sembró 'Criollo (Sabanero)' y ~30 razas; V23 las reutiliza por nombre. En la validación (BD limpia con V1..V22) V11 no insertó filas (especie 'Perro' aún no existía), por lo que el camino de renombrado no se ejercitó con datos; la segunda pasada de V23 fue idempotente (370 filas, 0 duplicados por lower/unaccent).
