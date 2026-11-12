CREATE TABLE usuario (
    id UUID PRIMARY KEY,
    rol_id SMALLINT NOT NULL REFERENCES rol(id),
    estado_usuario_id SMALLINT NOT NULL REFERENCES estado_usuario(id),
    nombre VARCHAR(120) NOT NULL,
    correo VARCHAR(160) NOT NULL UNIQUE,
    contrasena_hash VARCHAR(100) NOT NULL,
    celular VARCHAR(25),
    mfa_activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inversionista (
    usuario_id UUID PRIMARY KEY REFERENCES usuario(id),
    nivel_experiencia_id SMALLINT NOT NULL REFERENCES nivel_experiencia(id),
    nivel_riesgo VARCHAR(10) CHECK (nivel_riesgo IN ('BAJO', 'MEDIO', 'ALTO')),
    objetivos_financieros VARCHAR(500)
);

CREATE TABLE inversionista_interes_mercado (
    inversionista_id UUID NOT NULL REFERENCES inversionista(usuario_id),
    mercado_id SMALLINT NOT NULL REFERENCES mercado(id),
    PRIMARY KEY (inversionista_id, mercado_id)
);
CREATE TABLE inversionista_interes_sector (
    inversionista_id UUID NOT NULL REFERENCES inversionista(usuario_id),
    sector_id SMALLINT NOT NULL REFERENCES sector(id),
    PRIMARY KEY (inversionista_id, sector_id)
);

CREATE TABLE cuenta_corretaje (
    id UUID PRIMARY KEY,
    inversionista_id UUID NOT NULL UNIQUE REFERENCES inversionista(usuario_id),
    alpaca_account_id VARCHAR(80) UNIQUE,
    saldo_disponible NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (saldo_disponible >= 0),
    saldo_reservado NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (saldo_reservado >= 0)
);

CREATE TABLE preferencia_perfil (
    inversionista_id UUID PRIMARY KEY REFERENCES inversionista(usuario_id),
    tipo_visualizacion VARCHAR(20) NOT NULL DEFAULT 'LISTA'
        CHECK (tipo_visualizacion IN ('LISTA', 'BARRAS', 'DETALLADA')),
    orden_predeterminado VARCHAR(30) NOT NULL DEFAULT 'VALOR_TOTAL'
        CHECK (orden_predeterminado IN ('VALOR_TOTAL', 'PORCENTAJE_GANANCIA')),
    ocultar_activos_menor_peso BOOLEAN NOT NULL DEFAULT FALSE,
    tipo_orden_id SMALLINT NOT NULL DEFAULT 1 REFERENCES tipo_orden(id),
    limite_operaciones_diarias INTEGER CHECK (limite_operaciones_diarias > 0),
    doble_autorizacion BOOLEAN NOT NULL DEFAULT FALSE,
    umbral_orden_grande NUMERIC(18,2),
    reporte_mensual BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (NOT doble_autorizacion OR umbral_orden_grande IS NOT NULL)
);

CREATE TABLE preferencia_canal (
    inversionista_id UUID NOT NULL REFERENCES inversionista(usuario_id),
    canal_id SMALLINT NOT NULL REFERENCES canal_notificacion(id),
    PRIMARY KEY (inversionista_id, canal_id)
);

CREATE TABLE posicion (
    cuenta_id UUID NOT NULL REFERENCES cuenta_corretaje(id),
    accion_id INTEGER NOT NULL REFERENCES accion(id),
    cantidad INTEGER NOT NULL CHECK (cantidad >= 0),
    precio_promedio_compra NUMERIC(18,4) NOT NULL CHECK (precio_promedio_compra >= 0),
    PRIMARY KEY (cuenta_id, accion_id)
);