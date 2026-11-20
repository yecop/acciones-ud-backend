CREATE TABLE evento_notificacion (
    id SMALLINT PRIMARY KEY,
    nombre VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE preferencia_evento (
    inversionista_id UUID NOT NULL REFERENCES inversionista(usuario_id),
    evento_id SMALLINT NOT NULL REFERENCES evento_notificacion(id),
    PRIMARY KEY (inversionista_id, evento_id)
);

INSERT INTO evento_notificacion (id, nombre) VALUES
  (1, 'APERTURA_CIERRE_MERCADO'),
  (2, 'RESUMEN_DIARIO_OPERACIONES'),
  (3, 'ORDEN_EJECUTADA')
ON CONFLICT (id) DO NOTHING;