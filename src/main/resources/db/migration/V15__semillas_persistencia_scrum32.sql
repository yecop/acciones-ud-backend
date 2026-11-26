-- Estado terminal mencionado en el plan SCRUM-32 (no define transiciones).
INSERT INTO estado_orden (id, nombre) VALUES (11, 'EJECUTADA')
ON CONFLICT DO NOTHING;

INSERT INTO plan_suscripcion (id, codigo, nombre, periodo, precio, moneda) VALUES
    (1, 'MENSUAL', 'Suscripción mensual', 'MENSUAL', 12.00, 'USD'),
    (2, 'ANUAL', 'Suscripción anual', 'ANUAL', 120.00, 'USD')
ON CONFLICT (codigo) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    periodo = EXCLUDED.periodo,
    precio = EXCLUDED.precio,
    moneda = EXCLUDED.moneda;

INSERT INTO feriado_mercado (id, mercado_id, fecha, descripcion)
SELECT datos.id, mercado.id, datos.fecha, datos.descripcion
FROM (VALUES
    ('13000000-0000-4000-8000-000000000001'::UUID, 'NYSE',   DATE '2026-01-01', 'Año Nuevo'),
    ('13000000-0000-4000-8000-000000000002'::UUID, 'NASDAQ', DATE '2026-01-01', 'Año Nuevo'),
    ('13000000-0000-4000-8000-000000000003'::UUID, 'NYSE',   DATE '2026-12-25', 'Navidad'),
    ('13000000-0000-4000-8000-000000000004'::UUID, 'LSE',    DATE '2026-12-25', 'Christmas Day'),
    ('13000000-0000-4000-8000-000000000005'::UUID, 'TSE',    DATE '2026-01-01', 'Ganjitsu (Año Nuevo)'),
    ('13000000-0000-4000-8000-000000000006'::UUID, 'ASX',    DATE '2026-01-26', 'Australia Day')
) AS datos(id, codigo_mercado, fecha, descripcion)
JOIN mercado ON mercado.codigo = datos.codigo_mercado
ON CONFLICT (mercado_id, fecha) DO UPDATE SET descripcion = EXCLUDED.descripcion;
