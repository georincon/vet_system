-- ============================================================================
-- V22 (PROPUESTA) - Permisos finos (RBAC), excepciones por usuario, refresh tokens,
-- bloqueo por intentos, 2FA y roles adicionales.
-- ============================================================================
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS intentos_fallidos  INT NOT NULL DEFAULT 0;
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS bloqueado_hasta    TIMESTAMPTZ;
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS ultimo_login_en    TIMESTAMPTZ;
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS debe_cambiar_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS totp_habilitado    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS totp_secreto_cifrado VARCHAR(255);
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS telefono           VARCHAR(30);

-- Roles adicionales (superconjunto del CHECK de V1)
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS ck_usuario_rol;
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_rol CHECK (rol IN
  ('ADMIN','VETERINARIO','RECEPCION','AUXILIAR','CAJERO','GROOMER','CONTADOR','TUTOR'));

CREATE TABLE IF NOT EXISTS permiso (
    id          BIGSERIAL PRIMARY KEY,
    codigo      VARCHAR(60) NOT NULL,
    modulo      VARCHAR(30) NOT NULL,
    descripcion VARCHAR(255),
    CONSTRAINT uq_permiso_codigo UNIQUE (codigo)
);

CREATE TABLE IF NOT EXISTS rol_permiso (
    rol        VARCHAR(20) NOT NULL,
    permiso_id BIGINT NOT NULL REFERENCES permiso (id) ON DELETE CASCADE,
    PRIMARY KEY (rol, permiso_id)
);

CREATE TABLE IF NOT EXISTS usuario_permiso (
    usuario_id BIGINT NOT NULL REFERENCES usuario (id),
    permiso_id BIGINT NOT NULL REFERENCES permiso (id),
    concedido  BOOLEAN NOT NULL,           -- TRUE agrega, FALSE revoca respecto al rol
    PRIMARY KEY (usuario_id, permiso_id)
);

CREATE TABLE IF NOT EXISTS refresh_token (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT NOT NULL REFERENCES usuario (id),
    token_hash  VARCHAR(64) NOT NULL,
    familia_id  UUID NOT NULL,
    expira_en   TIMESTAMPTZ NOT NULL,
    revocado_en TIMESTAMPTZ,
    ip          VARCHAR(45),
    user_agent  VARCHAR(255),
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_refresh_hash UNIQUE (token_hash)
);
CREATE INDEX IF NOT EXISTS ix_refresh_usuario ON refresh_token (usuario_id) WHERE revocado_en IS NULL;

-- ---------------------------------------------------------------- SEMILLAS
INSERT INTO permiso (codigo, modulo, descripcion) VALUES
 ('PACIENTE_LEER','pacientes','Ver pacientes y tutores'),
 ('PACIENTE_ESCRIBIR','pacientes','Crear/editar pacientes y tutores'),
 ('PACIENTE_FUSIONAR','pacientes','Fusionar duplicados'),
 ('AGENDA_LEER','agenda','Ver agenda'),
 ('AGENDA_ESCRIBIR','agenda','Crear/mover/cancelar citas y bloqueos'),
 ('CONSULTA_LEER','clinica','Ver historia clinica'),
 ('CONSULTA_ESCRIBIR','clinica','Crear/editar consultas abiertas'),
 ('CONSULTA_CERRAR','clinica','Cerrar consultas (firma)'),
 ('CONSULTA_ADDENDUM','clinica','Agregar addendum a consultas cerradas'),
 ('RECETA_CONTROLADOS','clinica','Prescribir medicamentos controlados'),
 ('VACUNA_APLICAR','inmunizacion','Registrar vacunas y desparasitacion'),
 ('HOSPITAL_GESTIONAR','hospitalizacion','Ingresos, tareas y altas'),
 ('CIRUGIA_GESTIONAR','hospitalizacion','Programar y registrar cirugias'),
 ('LAB_GESTIONAR','laboratorio','Ordenar examenes y registrar resultados'),
 ('INVENTARIO_LEER','inventario','Ver stock'),
 ('INVENTARIO_AJUSTAR','inventario','Ajustes, traslados y conteos'),
 ('COMPRAS_GESTIONAR','inventario','Ordenes de compra y recepciones'),
 ('FACTURA_LEER','facturacion','Ver facturas'),
 ('FACTURA_EMITIR','facturacion','Emitir facturas y cobrar'),
 ('FACTURA_ANULAR','facturacion','Notas credito / anulaciones'),
 ('CAJA_OPERAR','facturacion','Abrir/cerrar turno de caja'),
 ('PRECIOS_EDITAR','facturacion','Cambiar precios del catalogo y descuentos'),
 ('COMUNICACIONES_GESTIONAR','comunicaciones','Plantillas y reglas de recordatorio'),
 ('REPORTES_VER','reportes','Dashboards y reportes'),
 ('USUARIOS_GESTIONAR','seguridad','Usuarios, roles y permisos'),
 ('AUDITORIA_VER','seguridad','Consultar bitacora de auditoria'),
 ('CONFIGURACION_EDITAR','administracion','Sedes y configuracion'),
 ('DATOS_EXPORTAR','seguridad','Exportar datos / historia clinica')
ON CONFLICT (codigo) DO NOTHING;

-- ADMIN: todo
INSERT INTO rol_permiso (rol, permiso_id) SELECT 'ADMIN', id FROM permiso ON CONFLICT DO NOTHING;

INSERT INTO rol_permiso (rol, permiso_id)
SELECT m.rol, p.id FROM (VALUES
 ('VETERINARIO','PACIENTE_LEER'),('VETERINARIO','PACIENTE_ESCRIBIR'),('VETERINARIO','AGENDA_LEER'),('VETERINARIO','AGENDA_ESCRIBIR'),
 ('VETERINARIO','CONSULTA_LEER'),('VETERINARIO','CONSULTA_ESCRIBIR'),('VETERINARIO','CONSULTA_CERRAR'),('VETERINARIO','CONSULTA_ADDENDUM'),
 ('VETERINARIO','RECETA_CONTROLADOS'),('VETERINARIO','VACUNA_APLICAR'),('VETERINARIO','HOSPITAL_GESTIONAR'),('VETERINARIO','CIRUGIA_GESTIONAR'),
 ('VETERINARIO','LAB_GESTIONAR'),('VETERINARIO','INVENTARIO_LEER'),('VETERINARIO','FACTURA_LEER'),('VETERINARIO','REPORTES_VER'),
 ('RECEPCION','PACIENTE_LEER'),('RECEPCION','PACIENTE_ESCRIBIR'),('RECEPCION','AGENDA_LEER'),('RECEPCION','AGENDA_ESCRIBIR'),
 ('RECEPCION','FACTURA_LEER'),('RECEPCION','FACTURA_EMITIR'),('RECEPCION','CAJA_OPERAR'),('RECEPCION','INVENTARIO_LEER'),
 ('AUXILIAR','PACIENTE_LEER'),('AUXILIAR','AGENDA_LEER'),('AUXILIAR','CONSULTA_LEER'),('AUXILIAR','CONSULTA_ESCRIBIR'),
 ('AUXILIAR','VACUNA_APLICAR'),('AUXILIAR','HOSPITAL_GESTIONAR'),('AUXILIAR','LAB_GESTIONAR'),('AUXILIAR','INVENTARIO_LEER'),
 ('CAJERO','PACIENTE_LEER'),('CAJERO','FACTURA_LEER'),('CAJERO','FACTURA_EMITIR'),('CAJERO','CAJA_OPERAR'),
 ('CONTADOR','FACTURA_LEER'),('CONTADOR','FACTURA_ANULAR'),('CONTADOR','REPORTES_VER'),('CONTADOR','COMPRAS_GESTIONAR'),('CONTADOR','INVENTARIO_LEER'),
 ('GROOMER','PACIENTE_LEER'),('GROOMER','AGENDA_LEER')
) AS m(rol, permiso) JOIN permiso p ON p.codigo = m.permiso
ON CONFLICT DO NOTHING;
