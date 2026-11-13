CREATE UNIQUE INDEX ux_usuario_correo_lower ON usuario (lower(correo));

CREATE TABLE auth_mfa_desafio (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    codigo_hash CHAR(64) NOT NULL,
    canal VARCHAR(10) NOT NULL CHECK (canal IN ('EMAIL', 'SMS')),
    creado_en TIMESTAMPTZ NOT NULL,
    expira_en TIMESTAMPTZ NOT NULL,
    intentos SMALLINT NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    max_intentos SMALLINT NOT NULL CHECK (max_intentos > 0),
    bloqueado_hasta TIMESTAMPTZ,
    reenvios SMALLINT NOT NULL DEFAULT 0 CHECK (reenvios >= 0),
    max_reenvios SMALLINT NOT NULL CHECK (max_reenvios >= 0),
    ultimo_envio_en TIMESTAMPTZ,
    usado_en TIMESTAMPTZ,
    CHECK (expira_en > creado_en)
);

CREATE INDEX idx_auth_mfa_usuario ON auth_mfa_desafio(usuario_id);
CREATE INDEX idx_auth_mfa_expira ON auth_mfa_desafio(expira_en);

CREATE TABLE auth_token_refresco (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    familia_id UUID NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL,
    expira_en TIMESTAMPTZ NOT NULL,
    usado_en TIMESTAMPTZ,
    revocado_en TIMESTAMPTZ,
    CHECK (expira_en > creado_en)
);

CREATE INDEX idx_auth_refresh_usuario ON auth_token_refresco(usuario_id);
CREATE INDEX idx_auth_refresh_familia ON auth_token_refresco(familia_id);

CREATE TABLE auth_intento (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id UUID REFERENCES usuario(id),
    correo VARCHAR(160),
    tipo VARCHAR(30) NOT NULL,
    resultado VARCHAR(30) NOT NULL,
    motivo VARCHAR(200),
    ip VARCHAR(45),
    user_agent VARCHAR(300),
    trace_id VARCHAR(64),
    creado_en TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_intento_correo ON auth_intento(lower(correo), creado_en);
CREATE INDEX idx_auth_intento_usuario ON auth_intento(usuario_id, creado_en);
