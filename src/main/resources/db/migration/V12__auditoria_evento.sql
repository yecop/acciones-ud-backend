-- SCRUM-33: auditoría compartida. Registro append-only de acciones sensibles con actor,
-- fecha, entidad y traceId (lo consulta Legal/Cumplimiento en SCRUM-42).
-- No se puede editar, borrar ni truncar: igual que orden_historial_estado (V4).
CREATE TABLE auditoria_evento (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ocurrido_en TIMESTAMPTZ NOT NULL,
    actor_id UUID,
    accion VARCHAR(60) NOT NULL,
    entidad VARCHAR(40) NOT NULL,
    entidad_id VARCHAR(80),
    resultado VARCHAR(20) NOT NULL CHECK (resultado IN ('EXITOSO', 'RECHAZADO', 'FALLIDO')),
    detalle JSONB NOT NULL DEFAULT '{}'::jsonb,
    trace_id VARCHAR(64) NOT NULL,
    registrado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- actor_id sin FK: también se auditan procesos del sistema y usuarios que luego se den de baja.

CREATE INDEX idx_auditoria_entidad ON auditoria_evento(entidad, entidad_id, ocurrido_en);
CREATE INDEX idx_auditoria_actor ON auditoria_evento(actor_id, ocurrido_en);
CREATE INDEX idx_auditoria_trace ON auditoria_evento(trace_id);
CREATE INDEX idx_auditoria_accion ON auditoria_evento(accion, ocurrido_en);

CREATE FUNCTION rechazar_modificacion_auditoria()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'auditoria_evento es inmutable: solo se permiten inserciones';
END;
$$;

CREATE TRIGGER trg_auditoria_inmutable
BEFORE UPDATE OR DELETE ON auditoria_evento
FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion_auditoria();

CREATE TRIGGER trg_auditoria_no_truncate
BEFORE TRUNCATE ON auditoria_evento
FOR EACH STATEMENT EXECUTE FUNCTION rechazar_modificacion_auditoria();
