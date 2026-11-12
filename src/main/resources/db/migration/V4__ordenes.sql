-- orden.estado_orden_id = estado ACTUAL (consultas rápidas y motor de órdenes).
-- orden_historial_estado = registro de auditoría append-only (trazabilidad y no repudio).
-- Ambos se escriben en la misma transacción; el estado inicial también genera historial.
CREATE TABLE orden (
    id UUID PRIMARY KEY,
    cuenta_id UUID NOT NULL REFERENCES cuenta_corretaje(id),
    accion_id INTEGER NOT NULL REFERENCES accion(id),
    tipo_orden_id SMALLINT NOT NULL REFERENCES tipo_orden(id),
    estado_orden_id SMALLINT NOT NULL REFERENCES estado_orden(id),
    creada_por_id UUID NOT NULL REFERENCES usuario(id),
    validada_por_id UUID REFERENCES usuario(id),
    validada_en TIMESTAMPTZ,
    firmada_por_id UUID REFERENCES usuario(id),
    lado VARCHAR(6) NOT NULL CHECK (lado IN ('COMPRA', 'VENTA')),
    cantidad INTEGER NOT NULL CHECK (cantidad >= 1),
    precio_limite NUMERIC(18,4),
    precio_stop NUMERIC(18,4),
    encolada BOOLEAN NOT NULL DEFAULT FALSE,
    clave_idempotencia VARCHAR(80),
    trace_id VARCHAR(64) NOT NULL,
    creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviada_a_mercado_en TIMESTAMPTZ,
    UNIQUE (cuenta_id, clave_idempotencia),
    CHECK ((validada_por_id IS NULL) = (validada_en IS NULL))
);
CREATE INDEX idx_orden_cuenta ON orden(cuenta_id);
CREATE INDEX idx_orden_estado ON orden(estado_orden_id);
CREATE INDEX idx_orden_trace ON orden(trace_id);

CREATE TABLE orden_historial_estado (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    orden_id UUID NOT NULL REFERENCES orden(id),
    estado_orden_id SMALLINT NOT NULL REFERENCES estado_orden(id),
    cambiado_por_id UUID REFERENCES usuario(id),
    motivo VARCHAR(300),
    fecha TIMESTAMPTZ NOT NULL DEFAULT now(),
    precio_referencia NUMERIC(18,4) NOT NULL CHECK (precio_referencia >= 0),
    precio_bid NUMERIC(18,4) CHECK (precio_bid >= 0),
    precio_ask NUMERIC(18,4) CHECK (precio_ask >= 0),
    precio_observado_en TIMESTAMPTZ NOT NULL,
    trace_id VARCHAR(64) NOT NULL
);
CREATE INDEX idx_historial_orden ON orden_historial_estado(orden_id);

CREATE TABLE orden_ejecucion (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    orden_id UUID NOT NULL REFERENCES orden(id),
    id_ejecucion_externa VARCHAR(120) UNIQUE,
    cantidad_ejecutada INTEGER NOT NULL CHECK (cantidad_ejecutada > 0),
    precio_ejecucion NUMERIC(18,4) NOT NULL CHECK (precio_ejecucion > 0),
    ejecutada_en TIMESTAMPTZ NOT NULL,
    trace_id VARCHAR(64) NOT NULL
);
CREATE INDEX idx_ejecucion_orden ON orden_ejecucion(orden_id);

CREATE FUNCTION rechazar_modificacion_historial_orden()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'orden_historial_estado es inmutable: solo se permiten inserciones';
END;
$$;

CREATE TRIGGER trg_historial_orden_inmutable
BEFORE UPDATE OR DELETE ON orden_historial_estado
FOR EACH ROW EXECUTE FUNCTION rechazar_modificacion_historial_orden();

CREATE TRIGGER trg_historial_orden_no_truncate
BEFORE TRUNCATE ON orden_historial_estado
FOR EACH STATEMENT EXECUTE FUNCTION rechazar_modificacion_historial_orden();
