INSERT INTO mercado (id, codigo, nombre, zona_horaria, hora_apertura, hora_cierre) VALUES
  (1, 'NYSE',   'Bolsa de Nueva York', 'America/New_York',   '09:30', '16:00'),
  (2, 'NASDAQ', 'NASDAQ',              'America/New_York',   '09:30', '16:00'),
  (3, 'LSE',    'Bolsa de Londres',    'Europe/London',      '08:00', '16:30'),
  (4, 'TSE',    'Bolsa de Tokio',      'Asia/Tokyo',         '09:00', '15:00'),
  (5, 'ASX',    'Bolsa de Sídney',     'Australia/Sydney',   '10:00', '16:00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO accion (ticker, nombre_empresa, sector_id, mercado_id)
SELECT v.ticker, v.nombre, s.id, m.id
FROM (VALUES
  ('AAPL',  'Apple Inc.',                          'Tecnología', 'NASDAQ'),
  ('MSFT',  'Microsoft Corporation',               'Tecnología', 'NASDAQ'),
  ('JNJ',   'Johnson & Johnson',                   'Salud',      'NYSE'),
  ('JPM',   'JPMorgan Chase & Co.',                'Finanzas',   'NYSE'),
  ('XOM',   'Exxon Mobil Corp.',                   'Energía',    'NYSE'),
  ('GOOGL', 'Alphabet Inc.',                       'Tecnología', 'NASDAQ'),
  ('AMZN',  'Amazon.com Inc.',                     'Consumo',    'NASDAQ'),
  ('META',  'Meta Platforms Inc.',                 'Tecnología', 'NASDAQ'),
  ('TSLA',  'Tesla Inc.',                          'Consumo',    'NASDAQ'),
  ('NVDA',  'NVIDIA Corp.',                        'Tecnología', 'NASDAQ'),
  ('HSBA',  'HSBC Holdings plc',                   'Finanzas',   'LSE'),
  ('BP',    'BP plc',                              'Energía',    'LSE'),
  ('GSK',   'GlaxoSmithKline plc',                 'Salud',      'LSE'),
  ('ULVR',  'Unilever plc',                        'Consumo',    'LSE'),
  ('BARC',  'Barclays plc',                        'Finanzas',   'LSE'),
  ('7203',  'Toyota Motor Corp.',                  'Consumo',    'TSE'),
  ('6758',  'Sony Group Corp.',                    'Tecnología', 'TSE'),
  ('9984',  'SoftBank Group Corp.',                'Tecnología', 'TSE'),
  ('8306',  'Mitsubishi UFJ Financial Group Inc.', 'Finanzas',   'TSE'),
  ('6861',  'Keyence Corp.',                       'Tecnología', 'TSE'),
  ('BHP',   'BHP Group Ltd.',                      'Materiales', 'ASX'),
  ('CBA',   'Commonwealth Bank of Australia',      'Finanzas',   'ASX'),
  ('CSL',   'CSL Ltd.',                            'Salud',      'ASX'),
  ('WES',   'Wesfarmers Ltd.',                     'Consumo',    'ASX'),
  ('WOW',   'Woolworths Group Ltd.',               'Consumo',    'ASX')
) AS v(ticker, nombre, sector, mercado)
JOIN sector s ON s.nombre = v.sector
JOIN mercado m ON m.codigo = v.mercado
ON CONFLICT (ticker) DO NOTHING;
