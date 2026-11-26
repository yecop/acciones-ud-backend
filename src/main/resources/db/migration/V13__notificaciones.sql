-- SCRUM-51: servicio de notificaciones.
-- notificacion: bandeja de cada usuario (campana del frontend). El id se deriva del
-- (evento, usuario, tipo), así que reprocesar un evento no duplica la notificación.
CREATE TABLE notificacion (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    evento_id UUID NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    titulo VARCHAR(120) NOT NULL,
    mensaje VARCHAR(500) NOT NULL,
    referencia_id VARCHAR(80),
    leida BOOLEAN NOT NULL DEFAULT FALSE,
    creada_en TIMESTAMPTZ NOT NULL,
    leida_en TIMESTAMPTZ,
    trace_id VARCHAR(64) NOT NULL,
    CONSTRAINT uq_notificacion_evento UNIQUE (usuario_id, evento_id, tipo),
    CONSTRAINT ck_notificacion_leida CHECK (leida = (leida_en IS NOT NULL))
);

CREATE INDEX idx_notificacion_usuario_fecha ON notificacion(usuario_id, creada_en DESC, id);
CREATE INDEX idx_notificacion_no_leidas ON notificacion(usuario_id) WHERE NOT leida;

-- notificacion_envio: entrega por canal externo (SMS, correo, WhatsApp). Un fallo temporal
-- deja el envío PENDIENTE con proximo_intento; reintentar actualiza esta fila, no crea otra.
CREATE TABLE notificacion_envio (
    notificacion_id UUID NOT NULL REFERENCES notificacion(id) ON DELETE CASCADE,
    canal_id SMALLINT NOT NULL REFERENCES canal_notificacion(id),
    estado VARCHAR(12) NOT NULL CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO')),
    intentos INTEGER NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    ultimo_error VARCHAR(300),
    proximo_intento TIMESTAMPTZ,
    enviado_en TIMESTAMPTZ,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (notificacion_id, canal_id),
    CONSTRAINT ck_envio_pendiente CHECK (estado <> 'PENDIENTE' OR proximo_intento IS NOT NULL)
);

CREATE INDEX idx_envio_pendiente ON notificacion_envio(proximo_intento) WHERE estado = 'PENDIENTE';
