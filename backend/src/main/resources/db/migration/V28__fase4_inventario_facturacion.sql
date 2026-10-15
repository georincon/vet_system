-- ============================================================================
-- V28 - Fase 4: inventario/farmacia y facturacion interna/caja.
-- Solo agrega lo que V1/V15/V18/V19 no cubren (no se edita ninguna migracion previa):
--   1) item.stock_minimo: minimo global por item para las alertas "bajo minimo".
--   2) presupuesto: origen (MANUAL/CONSULTA), estado FACTURADO, creado_por; unico por consulta cuando lo genera
--      el evento ConsultaCerrada (idempotencia); presupuesto_item.origen_* para no duplicar renglones.
--   3) factura: creado_por; factura_item: cantidad_stock_cubierta (evita doble descuento cuando la vacuna ya
--      descontó el lote) y factura_item_origen_id (renglon de la factura que acredita una nota credito).
--   4) Secuencias de codigos (presupuesto, orden de compra) y resoluciones/consecutivos internos de partida.
--   5) kardex append-only y triggers de auditoria (fn_auditar_cambio) de las tablas nuevas de la fase.
--   6) Catalogo: un item por cada vacuna del carnet (VAC-<codigo>) para poder cotizar y llevar lotes.
--      NO se enlaza vacuna.item_id (la validacion de lote de inmunizacion exigiria ese item); el vinculo se hace
--      por convencion de codigo o por PUT /api/inmunizacion/vacunas/{id} {itemId}.
-- ============================================================================

-- ---------------------------------------------------------------- 1) minimo de stock
ALTER TABLE item ADD COLUMN IF NOT EXISTS stock_minimo NUMERIC(12,3) NOT NULL DEFAULT 0;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_item_stock_minimo') THEN
    ALTER TABLE item ADD CONSTRAINT ck_item_stock_minimo CHECK (stock_minimo >= 0);
  END IF;
END $$;

-- ---------------------------------------------------------------- 2) presupuesto
ALTER TABLE presupuesto ADD COLUMN IF NOT EXISTS origen     VARCHAR(10) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE presupuesto ADD COLUMN IF NOT EXISTS creado_por BIGINT REFERENCES usuario (id);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_presupuesto_origen') THEN
    ALTER TABLE presupuesto ADD CONSTRAINT ck_presupuesto_origen CHECK (origen IN ('MANUAL','CONSULTA'));
  END IF;
END $$;
ALTER TABLE presupuesto DROP CONSTRAINT IF EXISTS ck_presupuesto_estado;
ALTER TABLE presupuesto ADD CONSTRAINT ck_presupuesto_estado
  CHECK (estado IN ('BORRADOR','ENVIADO','ACEPTADO','RECHAZADO','VENCIDO','FACTURADO'));
CREATE UNIQUE INDEX IF NOT EXISTS uq_presupuesto_consulta_auto ON presupuesto (consulta_id)
  WHERE origen = 'CONSULTA';

ALTER TABLE presupuesto_item ADD COLUMN IF NOT EXISTS origen_tabla VARCHAR(40);
ALTER TABLE presupuesto_item ADD COLUMN IF NOT EXISTS origen_id    BIGINT;
CREATE UNIQUE INDEX IF NOT EXISTS uq_presupuesto_item_origen ON presupuesto_item (presupuesto_id, origen_tabla, origen_id)
  WHERE origen_tabla IS NOT NULL;

-- ---------------------------------------------------------------- 3) factura
ALTER TABLE factura ADD COLUMN IF NOT EXISTS creado_por BIGINT REFERENCES usuario (id);
ALTER TABLE factura_item ADD COLUMN IF NOT EXISTS cantidad_stock_cubierta NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE factura_item ADD COLUMN IF NOT EXISTS factura_item_origen_id BIGINT REFERENCES factura_item (id);
CREATE INDEX IF NOT EXISTS ix_fi_origen ON factura_item (factura_item_origen_id) WHERE factura_item_origen_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_fi_consulta_item ON factura_item (consulta_id, item_id) WHERE consulta_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_pago_fecha ON pago (fecha);

-- ---------------------------------------------------------------- 4) secuencias y resoluciones internas
CREATE SEQUENCE IF NOT EXISTS seq_presupuesto;
CREATE SEQUENCE IF NOT EXISTS seq_orden_compra;

-- Rangos internos de partida (factura interna, nota credito, POS). La Fase 5 (DIAN) agrega resoluciones reales
-- (numero_resolucion, vigencia, ambiente PRODUCCION) y desactiva estas.
INSERT INTO resolucion_facturacion (sede_id, tipo_documento, prefijo, numero_resolucion, rango_desde, rango_hasta,
                                    consecutivo_actual, ambiente, activo)
SELECT s.id, v.tipo, v.prefijo, v.numero, 1, 99999999, 0, 'HABILITACION', TRUE
FROM sede s,
     (VALUES ('FACTURA', 'FI', 'INTERNA-FACTURA'), ('NOTA_CREDITO', 'NC', 'INTERNA-NC'), ('POS', 'POS', 'INTERNA-POS'))
       AS v(tipo, prefijo, numero)
WHERE s.es_principal
ON CONFLICT (numero_resolucion, prefijo) DO NOTHING;

-- ---------------------------------------------------------------- 5) kardex inmutable y auditoria
CREATE OR REPLACE TRIGGER trg_ms_append_only BEFORE UPDATE OR DELETE ON movimiento_stock
  FOR EACH ROW EXECUTE FUNCTION fn_append_only();

CREATE OR REPLACE TRIGGER trg_aud_item           AFTER INSERT OR UPDATE OR DELETE ON item                 FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_bodega         AFTER INSERT OR UPDATE OR DELETE ON bodega               FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_proveedor      AFTER INSERT OR UPDATE OR DELETE ON proveedor            FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_orden_compra   AFTER INSERT OR UPDATE OR DELETE ON orden_compra         FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_recepcion      AFTER INSERT OR UPDATE OR DELETE ON recepcion_compra     FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_presupuesto    AFTER INSERT OR UPDATE OR DELETE ON presupuesto          FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_caja_turno     AFTER INSERT OR UPDATE OR DELETE ON caja_turno           FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_caja_mov       AFTER INSERT OR UPDATE OR DELETE ON caja_movimiento      FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_resolucion     AFTER INSERT OR UPDATE OR DELETE ON resolucion_facturacion FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();

-- ---------------------------------------------------------------- 6) items de vacunas
INSERT INTO item (codigo, nombre, tipo, categoria_id, precio_venta, impuesto_pct, controla_stock, unidad_medida,
                  via_administracion, stock_minimo)
SELECT 'VAC-' || v.codigo, v.nombre, 'MEDICAMENTO', (SELECT id FROM categoria_item WHERE nombre = 'Vacunas'),
       CASE WHEN v.codigo LIKE '%RAB%' THEN 35000 WHEN v.codigo IN ('CAN-DHPP', 'FEL-FVRCP') THEN 65000 ELSE 55000 END,
       0, TRUE, 'dosis', v.via_administracion, 0
FROM vacuna v
ON CONFLICT (codigo) DO NOTHING;
