INSERT INTO rol (id, nombre) VALUES
  (1, 'INVERSIONISTA'), (2, 'COMISIONISTA'), (3, 'ADMIN'),
  (4, 'LEGAL'), (5, 'JUNTA_DIRECTIVA')
ON CONFLICT (id) DO NOTHING;

INSERT INTO estado_usuario (id, nombre) VALUES
  (1, 'ACTIVO'), (2, 'PENDIENTE_ALTA'), (3, 'OPERACIONES_RESTRINGIDAS')
ON CONFLICT (id) DO NOTHING;

INSERT INTO nivel_experiencia (id, nombre) VALUES
  (1, 'PRINCIPIANTE'), (2, 'EXPERIMENTADO')
ON CONFLICT (id) DO NOTHING;

INSERT INTO canal_notificacion (id, nombre) VALUES
  (1, 'SMS'), (2, 'EMAIL'), (3, 'WHATSAPP')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sector (id, nombre) VALUES
  (1, 'Tecnología'), (2, 'Finanzas'), (3, 'Salud'),
  (4, 'Energía'), (5, 'Consumo'), (6, 'Materiales')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tipo_orden (id, nombre) VALUES
  (1, 'MARKET'), (2, 'LIMIT'), (3, 'STOP_LOSS'), (4, 'TAKE_PROFIT')
ON CONFLICT (id) DO NOTHING;

INSERT INTO estado_orden (id, nombre) VALUES
  (1, 'PENDIENTE'), (2, 'ENVIADA'), (3, 'FIRMADA_POR_COMISIONISTA'),
  (4, 'EN_EJECUCION'), (5, 'CANCELADA'), (6, 'RECHAZADA'),
  (7, 'EXPIRADA'), (8, 'EN_REVISION'), (9, 'FALLIDA'), (10, 'DETENIDA')
ON CONFLICT (id) DO NOTHING;

INSERT INTO parametro_sistema (clave, valor, descripcion) VALUES
  ('comision.porcentaje', '2.00', 'Porcentaje de comisión sobre el total de la transacción'),
  ('comision.reparto.plataforma', '60.00', 'Porcentaje de la comisión para Acciones UD con comisionista'),
  ('comision.reparto.comisionista', '40.00', 'Porcentaje de la comisión para el comisionista')
ON CONFLICT (clave) DO NOTHING;