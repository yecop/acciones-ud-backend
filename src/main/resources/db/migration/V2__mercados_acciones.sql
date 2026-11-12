-- Fuente de verdad de los horarios de mercado (RF-06: el administrador los modifica).
-- El catálogo en código del módulo mercado (SCRUM-23) es temporal y debe leer de esta tabla.
CREATE TABLE mercado (
    id SMALLINT PRIMARY KEY,
    codigo VARCHAR(10) NOT NULL UNIQUE,
    nombre VARCHAR(80) NOT NULL,
    zona_horaria VARCHAR(40) NOT NULL,
    hora_apertura TIME NOT NULL,
    hora_cierre TIME NOT NULL,
    CHECK (hora_apertura < hora_cierre)
);

CREATE TABLE accion (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticker VARCHAR(12) NOT NULL UNIQUE,
    nombre_empresa VARCHAR(120) NOT NULL,
    sector_id SMALLINT NOT NULL REFERENCES sector(id),
    mercado_id SMALLINT NOT NULL REFERENCES mercado(id),
    capitalizacion_referencia NUMERIC(20,2)
);
CREATE INDEX idx_accion_mercado ON accion(mercado_id);
