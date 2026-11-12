-- Capitalización de referencia estática en USD, con corte al 2026-11-11.
-- Valores redondeados; no representan cotizaciones en tiempo real.
UPDATE accion
SET capitalizacion_referencia = CASE ticker
    WHEN 'HSBA' THEN 308360000000
    WHEN 'BP'   THEN 113990000000
    WHEN 'GSK'  THEN 103270000000
    WHEN 'ULVR' THEN 142030000000
    WHEN 'BARC' THEN 76680000000
    WHEN '7203' THEN 253860000000
    WHEN '6758' THEN 154780000000
    WHEN '9984' THEN 138380000000
    WHEN '8306' THEN 248750000000
    WHEN '6861' THEN 115670000000
    WHEN 'BHP'  THEN 211130000000
    WHEN 'CBA'  THEN 191790000000
    WHEN 'CSL'  THEN 66430000000
    WHEN 'WES'  THEN 63360000000
    WHEN 'WOW'  THEN 35290000000
END
WHERE ticker IN (
    'HSBA', 'BP', 'GSK', 'ULVR', 'BARC',
    '7203', '6758', '9984', '8306', '6861',
    'BHP', 'CBA', 'CSL', 'WES', 'WOW'
)
  AND mercado_id IN (3, 4, 5);

COMMENT ON COLUMN accion.capitalizacion_referencia IS
    'Capitalización de mercado de referencia en USD; valores estáticos con corte al 2026-11-11.';
