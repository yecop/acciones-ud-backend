-- Persistencia para órdenes completadas, comisionistas, suscripciones,
-- alertas, restricciones administrativas y movimientos de fondos (SCRUM-32).

CREATE TABLE comisionista (
    usuario_id UUID PRIMARY KEY REFERENCES usuario(id),
    numero_licencia VARCHAR(80) NOT NULL UNIQUE,
    entidad_certificadora VARCHAR(120) NOT NULL,
    certificado_desde DATE NOT NULL,
    certificado_hasta DATE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (certificado_hasta IS NULL OR certificado_hasta >= certificado_desde)
);

CREATE TABLE asignacion_comisionista (
    id UUID PRIMARY KEY,
    inversionista_id UUID NOT NULL REFERENCES inversionista(usuario_id),
    comisionista_id UUID NOT NULL REFERENCES comisionista(usuario_id),
    asignada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    finalizada_en TIMESTAMPTZ,
    CHECK (finalizada_en IS NULL OR finalizada_en >= asignada_en)
);
CREATE UNIQUE INDEX uq_asignacion_comisionista_vigente
    ON asignacion_comisionista(inversionista_id) WHERE finalizada_en IS NULL;
CREATE INDEX idx_asignacion_comisionista_comisionista
    ON asignacion_comisionista(comisionista_id);

CREATE TABLE estrategia_cliente (
    id UUID PRIMARY KEY,
    asignacion_id UUID NOT NULL REFERENCES asignacion_comisionista(id),
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(500),
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (asignacion_id, nombre)
);

CREATE TABLE propuesta_orden (
    id UUID PRIMARY KEY,
    estrategia_id UUID NOT NULL REFERENCES estrategia_cliente(id),
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('BORRADOR', 'PENDIENTE', 'ACEPTADA', 'RECHAZADA', 'EXPIRADA')),
    creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    vence_en TIMESTAMPTZ,
    respondida_en TIMESTAMPTZ,
    CHECK (vence_en IS NULL OR vence_en >= creada_en)
);
CREATE INDEX idx_propuesta_orden_estrategia_estado
    ON propuesta_orden(estrategia_id, estado);

CREATE TABLE propuesta_orden_item (
    id UUID PRIMARY KEY,
    propuesta_id UUID NOT NULL REFERENCES propuesta_orden(id) ON DELETE CASCADE,
    accion_id INTEGER NOT NULL REFERENCES accion(id),
    tipo_orden_id SMALLINT NOT NULL REFERENCES tipo_orden(id),
    lado VARCHAR(6) NOT NULL CHECK (lado IN ('COMPRA', 'VENTA')),
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    precio_limite NUMERIC(18,4),
    creada_orden_id UUID UNIQUE REFERENCES orden(id),
    CHECK (precio_limite IS NULL OR precio_limite > 0)
);
CREATE INDEX idx_propuesta_item_propuesta ON propuesta_orden_item(propuesta_id);

-- orden_ejecucion conserva fills individuales del broker; transaccion consolida
-- el resultado final de una orden para liquidación y cálculo de comisión.
ALTER TABLE orden
    ADD COLUMN precio_referencia NUMERIC(18,4),
    ADD COLUMN monto_reservado NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (monto_reservado >= 0),
    ADD COLUMN broker_order_id VARCHAR(120) UNIQUE,
    ADD COLUMN programada_para TIMESTAMPTZ,
    ADD COLUMN hash_solicitud VARCHAR(128),
    ADD COLUMN comisionista_id UUID REFERENCES comisionista(usuario_id),
    ADD CONSTRAINT ck_orden_precio_referencia CHECK (precio_referencia IS NULL OR precio_referencia > 0);

ALTER TABLE posicion
    ADD COLUMN cantidad_reservada INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_posicion_cantidad_reservada
        CHECK (cantidad_reservada >= 0 AND cantidad_reservada <= cantidad);

CREATE TABLE transaccion (
    id UUID PRIMARY KEY,
    orden_id UUID NOT NULL UNIQUE REFERENCES orden(id),
    cantidad_ejecutada INTEGER NOT NULL CHECK (cantidad_ejecutada > 0),
    precio_ejecucion NUMERIC(18,4) NOT NULL CHECK (precio_ejecucion > 0),
    ejecutada_en TIMESTAMPTZ NOT NULL,
    referencia_externa VARCHAR(120) UNIQUE,
    simulada BOOLEAN NOT NULL DEFAULT FALSE,
    trace_id VARCHAR(64) NOT NULL,
    creada_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_transaccion_ejecutada_en ON transaccion(ejecutada_en);

CREATE TABLE comision (
    transaccion_id UUID PRIMARY KEY REFERENCES transaccion(id),
    porcentaje NUMERIC(5,2) NOT NULL CHECK (porcentaje >= 0 AND porcentaje <= 100),
    monto_total NUMERIC(18,2) NOT NULL CHECK (monto_total >= 0),
    monto_plataforma NUMERIC(18,2) NOT NULL CHECK (monto_plataforma >= 0),
    monto_comisionista NUMERIC(18,2) NOT NULL CHECK (monto_comisionista >= 0),
    comisionista_id UUID REFERENCES comisionista(usuario_id),
    liquidada_en TIMESTAMPTZ,
    CHECK (monto_total = monto_plataforma + monto_comisionista),
    CHECK ((comisionista_id IS NULL AND monto_comisionista = 0)
        OR (comisionista_id IS NOT NULL))
);

CREATE TABLE plan_suscripcion (
    id SMALLINT PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(80) NOT NULL,
    periodo VARCHAR(12) NOT NULL CHECK (periodo IN ('MENSUAL', 'ANUAL')),
    precio NUMERIC(18,2) NOT NULL CHECK (precio >= 0),
    moneda CHAR(3) NOT NULL DEFAULT 'USD',
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE suscripcion (
    id UUID PRIMARY KEY,
    inversionista_id UUID NOT NULL REFERENCES inversionista(usuario_id),
    plan_id SMALLINT NOT NULL REFERENCES plan_suscripcion(id),
    estado VARCHAR(16) NOT NULL CHECK (estado IN ('PENDIENTE', 'ACTIVA', 'VENCIDA', 'CANCELADA')),
    inicia_en TIMESTAMPTZ NOT NULL,
    termina_en TIMESTAMPTZ,
    proveedor_suscripcion_id VARCHAR(120) UNIQUE,
    creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (termina_en IS NULL OR termina_en > inicia_en)
);
CREATE UNIQUE INDEX uq_suscripcion_activa_inversionista
    ON suscripcion(inversionista_id) WHERE estado IN ('PENDIENTE', 'ACTIVA');
CREATE INDEX idx_suscripcion_plan_estado ON suscripcion(plan_id, estado);

CREATE TABLE pago_suscripcion (
    id UUID PRIMARY KEY,
    suscripcion_id UUID NOT NULL REFERENCES suscripcion(id),
    monto NUMERIC(18,2) NOT NULL CHECK (monto >= 0),
    moneda CHAR(3) NOT NULL,
    estado VARCHAR(16) NOT NULL CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'REEMBOLSADO')),
    referencia_proveedor VARCHAR(120) UNIQUE,
    pagado_en TIMESTAMPTZ,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pago_suscripcion_fecha ON pago_suscripcion(suscripcion_id, creado_en);

CREATE TABLE evento_pago_procesado (
    proveedor VARCHAR(30) NOT NULL,
    evento_id VARCHAR(160) NOT NULL,
    procesado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (proveedor, evento_id)
);

CREATE TABLE lista_observacion_item (
    inversionista_id UUID NOT NULL REFERENCES inversionista(usuario_id),
    accion_id INTEGER NOT NULL REFERENCES accion(id),
    agregado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (inversionista_id, accion_id)
);

CREATE TABLE alerta_precio (
    id UUID PRIMARY KEY,
    inversionista_id UUID NOT NULL REFERENCES inversionista(usuario_id),
    accion_id INTEGER NOT NULL REFERENCES accion(id),
    condicion VARCHAR(20) NOT NULL CHECK (condicion IN ('POR_ENCIMA', 'POR_DEBAJO', 'VARIACION_DIARIA')),
    umbral NUMERIC(18,4) NOT NULL CHECK (umbral > 0),
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultima_activacion_en TIMESTAMPTZ
);
CREATE INDEX idx_alerta_precio_usuario_activa ON alerta_precio(inversionista_id, activa);
CREATE INDEX idx_alerta_precio_accion_activa ON alerta_precio(accion_id, activa);

CREATE TABLE restriccion_mercado (
    id UUID PRIMARY KEY,
    mercado_id SMALLINT NOT NULL REFERENCES mercado(id),
    motivo VARCHAR(200) NOT NULL,
    inicia_en TIMESTAMPTZ NOT NULL,
    termina_en TIMESTAMPTZ,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    creada_por_id UUID REFERENCES usuario(id),
    creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (termina_en IS NULL OR termina_en > inicia_en)
);
CREATE INDEX idx_restriccion_mercado_vigencia ON restriccion_mercado(mercado_id, activa, inicia_en, termina_en);

CREATE TABLE feriado_mercado (
    id UUID PRIMARY KEY,
    mercado_id SMALLINT NOT NULL REFERENCES mercado(id),
    fecha DATE NOT NULL,
    descripcion VARCHAR(120) NOT NULL,
    UNIQUE (mercado_id, fecha)
);

CREATE TABLE restriccion_usuario (
    id UUID PRIMARY KEY,
    inversionista_id UUID NOT NULL REFERENCES inversionista(usuario_id),
    mercado_id SMALLINT REFERENCES mercado(id),
    motivo VARCHAR(200) NOT NULL,
    inicia_en TIMESTAMPTZ NOT NULL,
    termina_en TIMESTAMPTZ,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    creada_por_id UUID REFERENCES usuario(id),
    creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (termina_en IS NULL OR termina_en > inicia_en)
);
CREATE INDEX idx_restriccion_usuario_vigencia ON restriccion_usuario(inversionista_id, activa, inicia_en, termina_en);

CREATE TABLE movimiento_fondos (
    id UUID PRIMARY KEY,
    cuenta_id UUID NOT NULL REFERENCES cuenta_corretaje(id),
    tipo VARCHAR(16) NOT NULL CHECK (tipo IN ('DEPOSITO', 'RETIRO', 'RESERVA', 'LIBERACION', 'LIQUIDACION')),
    estado VARCHAR(16) NOT NULL CHECK (estado IN ('PENDIENTE', 'COMPLETADO', 'FALLIDO', 'CANCELADO')),
    monto NUMERIC(18,2) NOT NULL CHECK (monto > 0),
    moneda CHAR(3) NOT NULL DEFAULT 'USD',
    referencia_externa VARCHAR(120) UNIQUE,
    clave_idempotencia VARCHAR(100) NOT NULL UNIQUE,
    orden_id UUID REFERENCES orden(id),
    trace_id VARCHAR(64) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    completado_en TIMESTAMPTZ
);
CREATE INDEX idx_movimiento_fondos_cuenta_fecha ON movimiento_fondos(cuenta_id, creado_en DESC);
CREATE INDEX idx_movimiento_fondos_estado ON movimiento_fondos(estado, creado_en);
