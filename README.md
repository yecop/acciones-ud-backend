# Acciones UD - Backend API

Backend de la plataforma de Day Trading **Acciones UD**. Implementa un nucleo
hexagonal en Spring Boot para aislar la logica de negocio de los proveedores
externos (Alpaca, Alpha Vantage, Stripe), la persistencia y los canales de
entrada.

## Documentacion de referencia

- `AccionesUD-Docs/SAD_AccionesUD.pdf`: Documento de Diseno y Arquitectura de
  Software (SAD).
- `AccionesUD-Docs/API_Endpoints_AccionesUD_2-2.pdf`: contrato de API REST.
- `AccionesUD-Docs/2026-3 Instrumento Financiero.pdf`: enunciado del cliente.

## Stack

- Java 17
- Spring Boot 3.3.x
- Maven Wrapper (`./mvnw`)
- JUnit 5, Mockito, MockMvc y ArchUnit
- SpringDoc OpenAPI
- Spring Security con JWT (HS256) y MFA por correo
- PostgreSQL y Mailpit para desarrollo local

## Arquitectura

La solucion sigue un nucleo hexagonal organizado por modulos. Cada modulo
separa dominio, aplicacion, puertos y adaptadores:

```text
co.edu.udistrital.accionesud
├── shared
│   ├── domain
│   ├── application
│   └── infrastructure
├── auth
├── perfil
├── suscripciones
├── mercado
├── trading
├── comisionistas
├── cuenta
├── notificaciones
├── auditoria
└── administracion
```

Dentro de cada modulo:

```text
<modulo>/domain
<modulo>/application/port/in
<modulo>/application/port/out
<modulo>/application/service
<modulo>/application/dto
<modulo>/infrastructure/adapter/in/rest
<modulo>/infrastructure/adapter/out
<modulo>/infrastructure/config
```

Reglas verificadas con ArchUnit:

- El dominio solo depende de Java y de si mismo.
- El dominio no depende de Spring, de la aplicacion ni de los adaptadores.
- La aplicacion no depende de Spring ni de infraestructura.
- Los adaptadores pueden depender de dominio y aplicacion.

El build falla si se rompe alguna regla.

## Perfiles

| Perfil | Uso | Observaciones |
|---|---|---|
| `dev` | desarrollo local | PostgreSQL local, Swagger, datos demo y logs legibles con traceId |
| `test` | pruebas sin base de datos | DataSource y Flyway excluidos; health de correo y Redis desactivados; cotizaciones en caché de memoria y actualizador apagado |
| `staging` | ambiente público de pruebas | PostgreSQL y `AUTH_JWT_SECRET` obligatorios, Swagger activo para pruebas de API, health `when-authorized`, sin datos demo |
| `prod` | despliegue | PostgreSQL y `AUTH_JWT_SECRET` obligatorios, logs JSON, Swagger y datos demo deshabilitados |

El perfil por defecto es `dev`. La configuración común (correo, JWT, proveedores, CORS) está en `application.yml`; cada perfil solo aporta sus diferencias. Los datos demo están detrás de `accionesud.auth.demo.habilitado`, que solo es `true` en `dev`.

`test` es el perfil de las pruebas rápidas y de CI, no el ambiente público de aceptación. En `staging` y `prod`, `ValidadorConfiguracionDespliegue` falla el arranque si faltan `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` o un `AUTH_JWT_SECRET` de al menos 32 caracteres; en `staging` también exige `ACCIONESUD_CORS_ALLOWED_ORIGINS` con la URL pública del frontend.

## Ejecucion

```bash
./mvnw spring-boot:run
# o
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

La aplicacion queda en `http://localhost:8080`.

## Pruebas

```bash
./mvnw test
./mvnw -B clean verify
```

`verify` ejecuta pruebas unitarias, pruebas de integracion y reglas ArchUnit.

Las pruebas con infraestructura real son nueve. Las que necesitan PostgreSQL
(`AuthIntegrationTest`, `RegistroIntegrationTest`, `PerfilIntegrationTest`,
`MigracionesIntegrationTest`, `AuditoriaPostgresIntegrationTest`,
`NotificacionesPostgresIntegrationTest` y `CuentaFondosPostgresIntegrationTest`) comparten un unico contenedor `postgres:16-alpine` gestionado
desde `src/test/java/co/edu/udistrital/accionesud/support`; el datasource y Flyway
(`classpath:db/migration`) se registran de forma centralizada y antes de cada prueba se
limpian las tablas `auth_*` con `src/test/resources/db/limpiar-datos.sql`.
`RedisCotizacionCacheAdapterIntegrationTest` usa Redis 7 y `CanalCorreoMailpitIntegrationTest`
usa Mailpit (SMTP real) con Testcontainers. Cada prueba
debe crear sus propios datos con identificadores unicos y reloj controlado cuando evalúe
horarios de mercado. Todas usan `@Testcontainers(disabledWithoutDocker = true)`: sin Docker
se omiten en local, pero el guard de CI falla si alguna se omite. Por ello se necesita Docker
para validar `clean verify` en CI.

## Endpoints base

| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | `/actuator/health` | estado de la aplicacion |
| GET | `/v3/api-docs` | especificacion OpenAPI |
| GET | `/swagger-ui.html` | interfaz Swagger UI |
| POST | `/api/v1/auth/registro` | registro de usuario y alta en Alpaca sandbox (público) |
| POST | `/api/v1/auth/login` | valida credenciales y envía código MFA |
| POST | `/api/v1/auth/mfa/verificar` | valida el código y emite access/refresh tokens |
| POST | `/api/v1/auth/mfa/reenviar` | reenvía el código MFA |
| POST | `/api/v1/auth/refresh` | rota el refresh token |
| POST | `/api/v1/auth/logout` | revoca la sesión autenticada |
| POST | `/api/v1/admin/altas/reintentar` | reintenta altas pendientes (rol ADMIN) |
| GET | `/api/v1/perfil` | datos del inversionista autenticado (rol INVERSIONISTA) |
| PATCH | `/api/v1/perfil` | actualiza datos del perfil de forma parcial |
| GET | `/api/v1/perfil/preferencias` | preferencias operativas (valores por defecto si no se han guardado) |
| PUT | `/api/v1/perfil/preferencias` | reemplaza las preferencias |
| GET | `/api/v1/notificaciones` | bandeja del usuario autenticado (roles INVERSIONISTA y COMISIONISTA) |
| PATCH | `/api/v1/notificaciones/{id}/leida` | marca una notificación como leída (idempotente) |
| PATCH | `/api/v1/notificaciones/leidas` | marca todas las no leídas; responde `{ "marcadas": n }` |
| GET | `/api/v1/cuenta/saldo` | saldo de la cuenta del usuario autenticado |
| POST | `/api/v1/cuenta/depositos` | depósito simulado (rol INVERSIONISTA, `Idempotency-Key`) |
| POST | `/api/v1/cuenta/retiros` | retiro simulado (rol INVERSIONISTA, `Idempotency-Key`) |
| GET | `/api/v1/cuenta/movimientos` | historial de movimientos de fondos |
| POST | `/api/v1/cuenta/recargas` | recarga con Stripe Checkout (rol INVERSIONISTA, `Idempotency-Key`) |
| POST | `/api/v1/pagos/stripe/webhook` | avisos firmados de Stripe (público, verifica `Stripe-Signature`) |

### Perfil y preferencias (SCRUM-22)

Las cuatro rutas requieren un JWT de un usuario con rol `INVERSIONISTA`. El
backend identifica al usuario desde la autenticación; no se recibe un ID de
usuario en la ruta ni en el cuerpo.

`PATCH /api/v1/perfil` actualiza solo los campos enviados. Los campos omitidos
se conservan. Si se envía `mercados: []` o `sectores: []`, se eliminan todos los
intereses de esa categoría. Un mercado o sector inexistente responde
`422 INTERES_NO_VALIDO`; los demás datos inválidos responden
`400 VALIDACION_FALLIDA` con cada problema en `detalles`, como un objeto con
`campo` y `problema`.

`GET /api/v1/perfil/preferencias` no crea filas en la base de datos. Mientras
no se hayan guardado preferencias, responde con estos valores: visualización
`LISTA`, orden del portafolio `VALOR_TOTAL`, orden predeterminado `MARKET`, canal
`EMAIL`, y doble autorización desactivada. Los eventos disponibles aparecen
habilitados por defecto. `TRAILING_STOP` aparece en las opciones, pero
deshabilitado.

`PUT /api/v1/perfil/preferencias` reemplaza el conjunto completo de preferencias.
Acepta canales `EMAIL`, `SMS` o `WHATSAPP` (al menos uno), y eventos
`APERTURA_CIERRE_MERCADO`, `RESUMEN_DIARIO_OPERACIONES` y `ORDEN_EJECUTADA`
(puede ser una lista vacía). No se aceptan elementos nulos. El límite diario,
cuando se especifica, debe ser mayor que cero. Si la doble autorización está
activa, `umbralOrdenGrande` es obligatorio y debe ser un monto positivo con hasta
dos decimales. Una solicitud inválida responde `400 VALIDACION_FALLIDA` y no altera
las preferencias previamente almacenadas.

### Notificaciones (SCRUM-51)

El módulo `notificaciones` consume los eventos de dominio de SCRUM-33 con
`@EventListener` (llegan después del commit; si hay rollback no se notifica) y guarda
una notificación por destinatario en `notificacion` (V13):

| Evento | Tipo | Destinatario |
|---|---|---|
| `OrdenEjecutada` con `completa = true` (una ejecución parcial no notifica) | `ORDEN_EJECUTADA` | titular de la cuenta |
| `OrdenEstadoCambiado` a `CANCELADA` | `ORDEN_CANCELADA` | titular de la cuenta |
| `MovimientoFondosRegistrado` | `MOVIMIENTO_FONDOS` | titular de la cuenta |
| `AlertaPrecioDisparada` | `ALERTA_PRECIO` | usuario de la alerta |
| `PropuestaOrdenesCreada` | `PROPUESTA_ORDENES` | inversionista |
| `AsociacionComisionistaCambiada` | `ASOCIACION_COMISIONISTA` | inversionista y comisionista |
| `SuscripcionActualizada` | `SUSCRIPCION` | usuario |

- **Idempotencia:** el id de la notificación se deriva de (evento, usuario, tipo), así que
  reprocesar un evento no la duplica ni la reenvía.
- **Canales externos:** solo para los tipos del catálogo `evento_notificacion`
  (`APERTURA_CIERRE_MERCADO`, `RESUMEN_DIARIO_OPERACIONES`, `ORDEN_EJECUTADA`) y solo si el
  usuario eligió ese evento en sus preferencias (SCRUM-22). Se crea un envío por cada canal
  elegido (`notificacion_envio`). Los demás tipos solo se muestran en la bandeja.
- **Proveedores:** EMAIL va por SMTP con la misma configuración `MAIL_*` del MFA (en dev,
  Mailpit: `docker compose up -d mailpit` y se ven en http://localhost:8025); remitente
  `NOTIFICACIONES_CORREO_REMITENTE`. Con `NOTIFICACIONES_CANAL_EMAIL=simulado` (perfil test) solo
  se registra en el log. SMS y WHATSAPP siguen **simulados** hasta integrar Twilio. El correo y el
  celular se leen de `usuario`.
- **Reintentos:** un fallo deja el envío `PENDIENTE` con espera exponencial
  (`espera-base-segundos * 2^(intento-1)`); un proceso cada `NOTIFICACIONES_REINTENTO_INTERVALO_MS`
  lo retoma (`FOR UPDATE SKIP LOCKED`, seguro con varias instancias). Al llegar a `max-intentos`
  queda `FALLIDO`. Un rechazo permanente (usuario sin correo, dirección inválida o rechazada por
  el servidor, p. ej. 550) queda `FALLIDO` de inmediato; caída del servidor o error de
  autenticación SMTP se reintentan. Reintentar solo actualiza el envío, nunca crea otra notificación.
- **Bandeja:** `GET /api/v1/notificaciones?tipo=&desde=&leida=&pagina=0&tamano=20` devuelve
  `{contenido:[{id,tipo,titulo,mensaje,leida,creadaEn,referenciaId}],pagina,tamano,totalElementos,totalPaginas}`,
  más recientes primero. `desde` acepta `2026-11-01` (inicio del día UTC) o un instante ISO 8601
  con zona. `tamano` va de 1 a 100. El usuario sale del token: una notificación ajena o inexistente
  responde `404 NO_ENCONTRADO`.

### Saldo, depósitos y retiros (SCRUM-25 y SCRUM-46)

El saldo vive en `cuenta_corretaje` y cada movimiento en `movimiento_fondos` (V14). El usuario
sale del token: nadie consulta ni opera una cuenta ajena. Un usuario sin cuenta (p. ej. un
comisionista) recibe `404 NO_ENCONTRADO` en el saldo.

- **Saldo inicial simulado:** la primera vez que se usa una cuenta sin movimientos se le aplica
  `ACCIONESUD_SALDO_INICIAL_SIMULADO` (100000.00) como un depósito, que aparece en el historial.
- **Depósito y retiro:** `POST /api/v1/cuenta/depositos` y `POST /api/v1/cuenta/retiros` con
  `{"monto": "500.00"}` (número o texto, mayor que 0, hasta 2 decimales, tope 1000000.00) y el
  encabezado `Idempotency-Key` (obligatorio, hasta 60 caracteres, única por cuenta). Solo el
  titular (rol `INVERSIONISTA`) opera su cuenta. Responden `201` con
  `{movimiento:{id,tipo,estado,monto,moneda,creadoEn,completadoEn,traceId},saldo:{disponible,reservado,total}}`.
- **Retiro sin fondos:** `422 FONDOS_INSUFICIENTES`; el saldo no cambia y el intento queda
  registrado como `FALLIDO` con su traceId (y en la auditoría como `RECHAZADO`).
- **Idempotencia:** repetir la clave con el mismo tipo y monto responde `200` con el movimiento
  original y `Idempotent-Replayed: true`, sin aplicarlo otra vez; con otros datos responde
  `409 CONFLICTO_IDEMPOTENCIA`.
- **Concurrencia:** el débito es un único `UPDATE ... WHERE saldo_disponible >= monto`, así que
  dos retiros simultáneos nunca dejan el saldo negativo ni pierden una actualización.
- **Historial:** `GET /api/v1/cuenta/movimientos?tipo=&pagina=0&tamano=20` (más recientes
  primero; `tipo` = `DEPOSITO`, `RETIRO`, …). Un `COMISIONISTA` puede consultar el de un cliente
  con relación vigente pasando `inversionistaId`.
- Cada depósito o retiro aplicado se audita y publica `MovimientoFondosRegistrado`, que crea la
  notificación `MOVIMIENTO_FONDOS` en la bandeja del titular (SCRUM-51). **Excepción:** el saldo
  inicial simulado se registra en `movimiento_fondos` y se audita como `SALDO_INICIAL`, pero no
  publica el evento, para no notificarle al usuario un depósito que él no hizo.
- El usuario demo de `dev` se crea con su perfil de inversionista y su cuenta de corretaje.

#### Recarga con pasarela (Stripe Checkout)

Además del depósito simulado, el titular puede recargar pagando con tarjeta en Stripe (modo prueba
en desarrollo). El saldo **solo** se acredita cuando Stripe lo confirma por webhook firmado, nunca
porque el navegador vuelva a la página de éxito.

1. `POST /api/v1/cuenta/recargas` (rol `INVERSIONISTA`, `Idempotency-Key`, `{"monto": "500.00"}`,
   mínimo 0.50) registra un `DEPOSITO` en estado `PENDIENTE` sin tocar el saldo, crea la sesión en
   Stripe y responde `201` con `{movimiento, urlPago}`. El frontend redirige a `urlPago`. Repetir la
   clave devuelve la misma sesión mientras siga abierta.
2. El usuario paga en la página de Stripe (tarjeta de prueba `4242 4242 4242 4242`, cualquier fecha
   futura y CVC) y vuelve a `PAGOS_URL_EXITO` con `?movimiento=<id>`.
3. Stripe llama a `POST /api/v1/pagos/stripe/webhook` (público, sin JWT). Se verifica
   `Stripe-Signature` (HMAC-SHA256 con `STRIPE_WEBHOOK_SECRET`, máximo 5 minutos de antigüedad);
   sin firma válida responde `400` y no toca nada.
   - `checkout.session.completed` pagado (o `async_payment_succeeded`): el depósito pasa a
     `COMPLETADO`, se acredita el saldo, se audita y se publica `MovimientoFondosRegistrado`.
   - `checkout.session.expired` (o `async_payment_failed`): el depósito pasa a `CANCELADO`.
   - Cada evento se procesa una vez (`evento_pago_procesado`) y el paso desde `PENDIENTE` es
     condicional: avisos repetidos o simultáneos no acreditan dos veces. Si la sesión o el monto
     cobrado no coinciden con el depósito, no se acredita y queda en la auditoría como `RECHAZADO`.

Si Stripe no responde, la recarga responde `502 PASARELA_NO_DISPONIBLE` y el depósito queda
`FALLIDO`. Sin `PAGOS_PASARELA=stripe` (perfil test, CI) responde `503 PASARELA_NO_CONFIGURADA`.

**Configurarla en local:** en el `.env` (no se versiona) poner `PAGOS_PASARELA=stripe`,
`STRIPE_SECRET_KEY` (clave **de prueba** `sk_test_…`, o mejor una restringida `rk_test_…` con
permiso de escritura solo sobre *Checkout Sessions*) y `STRIPE_WEBHOOK_SECRET`. Para recibir los
webhooks en local, con el [Stripe CLI](https://docs.stripe.com/stripe-cli):

```bash
stripe listen --forward-to localhost:8080/api/v1/pagos/stripe/webhook
```

El comando imprime el `whsec_…` que va en `STRIPE_WEBHOOK_SECRET` (si el backend corre en otro
puerto, cambiarlo en la URL).

## Datos de mercado con cache (SCRUM-24)

Los usuarios nunca consultan a Alpaca. Un unico actualizador programado
(`ActualizadorCotizacionesScheduler`) hace **una** consulta agrupada de los 10
tickers de EE. UU. (`/v2/stocks/snapshots?symbols=...&feed=iex`) cada 5 s con el
mercado abierto (12 llamadas/min) a traves de `DatosMercadoPort` →
`AdaptadorAlpacaDatos`, y guarda el resultado en Redis (un HASH por ticker:
`accionesud:cotizacion:<TICKER>` con `precio, bid, ask, spread, volumen,
actualizadoEn, fuente`). Los endpoints leen siempre de la cache:

| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | `/api/v1/acciones?mercado=&sector=&q=&pagina=0&tamano=20` | listado paginado con filtros |
| GET | `/api/v1/acciones/{ticker}` | detalle con la ultima cotizacion en cache |
| GET | `/api/v1/acciones/{ticker}/cotizacion` | cotizacion; 502 `PROVEEDOR_NO_DISPONIBLE` si nunca hubo dato |

LSE, TSE y ASX usan una segunda fuente (`AdaptadorInternacionalDatos`) con la
cadena de SCRUM-23: Alpha Vantage (primario) → Finnhub → Polygon. Como esos
proveedores cotizan un simbolo por llamada y sus planes gratuitos son muy
limitados, el actualizador internacional corre cada 60 s y consulta el ticker que
lleva mas tiempo sin actualizarse (con mercado cerrado solo precarga los que aun
no tienen dato). Los sufijos de simbolo por bolsa son configurables
(`accionesud.mercados.cotizaciones.internacional.sufijos`).

- Dato de EE. UU. con mas de 15 s → `desactualizada: true` y advertencia `DATO_DESACTUALIZADO`.
  Internacionales: umbral `COTIZACIONES_INTERNACIONAL_UMBRAL_SEGUNDOS` (1800 s).
  La respuesta incluye `umbralAntiguedadSegundos` para que el frontend use el mismo valor.
- Si el proveedor de un mercado falla se sirve el ultimo dato con advertencia `PROVEEDOR_NO_DISPONIBLE`.
- El 502 incluye `Retry-After` (y `datos.reintentarEnSegundos`) con el intervalo del actualizador
  del mercado de la accion: `COTIZACIONES_INTERVALO_MS` para NYSE/NASDAQ (5 s) y
  `COTIZACIONES_INTERNACIONAL_INTERVALO_MS` para LSE/TSE/ASX (60 s).
- Limitador de cuota por proveedor (Token Bucket + ventana movil): tope de diseno
  60 % del plan. Alpaca: 60 % de 200/min = 120/min. Alpha Vantage: 60 % de 5/min y
  de 25/dia. Finnhub: 60 % de 60/min. Polygon: 60 % de 5/min. Si se intenta superar,
  se rechaza la llamada (en internacionales se pasa al siguiente proveedor) y se
  registra un log `ERROR` con el marcador `ALARMA_CUOTA_<PROVEEDOR>`.
- Si Redis no responde, la lectura usa una copia local en memoria.

```bash
docker compose up -d redis
```

## Trazabilidad

Todas las peticiones aceptan el header `X-Trace-Id`.

- Si el header contiene un UUID valido, se reutiliza.
- Si no existe o no es un UUID valido, se genera uno nuevo.
- El valor se guarda en el MDC de logs con la clave `traceId`.
- El valor se devuelve siempre en la respuesta con el header `X-Trace-Id`.
- Los logs de la peticion y el cuerpo de error usan el mismo `traceId`.

## Contrato de errores

Cualquier error 400, 401, 403, 404 o 500 devuelve el siguiente cuerpo:

```json
{
  "timestamp": "2026-11-08T18:00:00Z",
  "status": 404,
  "codigo": "NO_ENCONTRADO",
  "mensaje": "No se encontro el recurso solicitado",
  "traceId": "123e4567-e89b-12d3-a456-426614174000",
  "ruta": "/api/v1/no-existe",
  "detalles": [],
  "datos": {}
}
```

`detalles` y `datos` siempre estan presentes, aunque esten vacios. Cada elemento
de `detalles` es un objeto con `campo` y `problema`; `campo` se omite para errores
generales. Los errores 500 no exponen stacktrace ni mensajes internos. Las
validaciones responden `400 VALIDACION_FALLIDA`; las rutas inexistentes usan
`404 NO_ENCONTRADO`. Los recursos de dominio pueden devolver códigos más
específicos, como `PERFIL_NO_ENCONTRADO`.

## Seguridad

La API es stateless y usa JWT firmados con HS256. El login nunca emite tokens
directamente: primero envía un código MFA de 6 dígitos y solo después de
verificarlo entrega access y refresh tokens. El refresh token rota en cada uso
y su reutilización revoca toda la familia de tokens.

Rutas publicas:

- `/actuator/health/**`
- `/actuator/info`
- `/v3/api-docs/**`
- `/swagger-ui/**`
- `/swagger-ui.html`
- `/error`
- `/api/v1/auth/registro`
- `/api/v1/auth/login`
- `/api/v1/auth/mfa/**`
- `/api/v1/auth/refresh`

El resto requiere `Authorization: Bearer <accessToken>`. La ruta
`/api/v1/admin/**` requiere rol `ADMIN`.

En el perfil `dev` se crea el usuario demo
`inversionista@accionesud.local` con clave `Clave123!` (configurable con
`AUTH_DEMO_CORREO` y `AUTH_DEMO_CLAVE`, y desactivable con
`accionesud.auth.demo.habilitado=false`). En `test` y `prod` no se crea: el
inicializador exige el perfil `dev` y la propiedad habilitada. Los códigos MFA se envían
a Mailpit en `http://localhost:8025`.

## Variables de entorno

Copiar `.env.example` y ajustar los valores locales. En `staging` y `prod` no se
deben versionar secretos: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` y
`AUTH_JWT_SECRET` (minimo 32 caracteres) son obligatorios y no tienen valor por
defecto; `ACCIONESUD_CORS_ALLOWED_ORIGINS` tambien lo es en `staging`. `.env.example`
incluye todas las variables referenciadas en `application*.yml`.

## Base de datos local

```bash
docker compose up -d      # PostgreSQL, Redis y Mailpit
./mvnw spring-boot:run    # Flyway aplica las migraciones al arrancar
docker compose down -v    # reinicia la base desde cero
```

Las migraciones están en `src/main/resources/db/migration`. No se edita una
migración ya fusionada: se crea una nueva. Las pruebas de integración usan una
base PostgreSQL controlada con Testcontainers (ver sección "Pruebas"), no este
servicio local.

## Ramas y commits

Se usa GitHub Flow:

- `main` es la unica rama de larga duracion y esta protegida.
- Cada cambio entra por una rama corta y un Pull Request.
- Nombres de rama: `feature/SCRUM-XX-slug`, `fix/SCRUM-XX-slug`,
  `chore/SCRUM-XX-slug`, `docs/SCRUM-XX-slug`, `test/SCRUM-XX-slug`.
- Commits con Conventional Commits y clave Jira, por ejemplo:
  `feat(architecture): crea estructura hexagonal [SCRUM-17]`.
- Ver `CONTRIBUTING.md` para el flujo completo.
