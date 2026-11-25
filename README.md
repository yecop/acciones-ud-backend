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

Las pruebas con infraestructura real son seis. Las que necesitan PostgreSQL
(`AuthIntegrationTest`, `RegistroIntegrationTest`, `PerfilIntegrationTest`,
`MigracionesIntegrationTest` y `AuditoriaPostgresIntegrationTest`) comparten un unico contenedor `postgres:16-alpine` gestionado
desde `src/test/java/co/edu/udistrital/accionesud/support`; el datasource y Flyway
(`classpath:db/migration`) se registran de forma centralizada y antes de cada prueba se
limpian las tablas `auth_*` con `src/test/resources/db/limpiar-datos.sql`.
`RedisCotizacionCacheAdapterIntegrationTest` usa Redis 7 con Testcontainers. Cada prueba
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

### Perfil y preferencias (SCRUM-22)

Las cuatro rutas requieren un JWT de un usuario con rol `INVERSIONISTA`. El
backend identifica al usuario desde la autenticación; no se recibe un ID de
usuario en la ruta ni en el cuerpo.

`PATCH /api/v1/perfil` actualiza solo los campos enviados. Los campos omitidos
se conservan. Si se envía `mercados: []` o `sectores: []`, se eliminan todos los
intereses de esa categoría. Un mercado o sector inexistente responde
`422 INTERES_NO_VALIDO`; los demás datos inválidos responden
`400 ERROR_VALIDACION` con el detalle por campo.

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
dos decimales. Una solicitud inválida responde `400 ERROR_VALIDACION` y no altera
las preferencias previamente almacenadas.

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
  "codigo": "RECURSO_NO_ENCONTRADO",
  "mensaje": "No se encontro el recurso solicitado",
  "traceId": "123e4567-e89b-12d3-a456-426614174000",
  "ruta": "/api/v1/no-existe",
  "detalles": [],
  "datos": {}
}
```

`detalles` y `datos` siempre estan presentes, aunque esten vacios. Los errores
500 no exponen stacktrace ni mensajes internos.

Nota de seguimiento (SCRUM-34): `AuthExceptionHandler` traduce `CodigoAuth` a
`CodigoError` por nombre (`valueOf`). Si se añade un codigo de autenticacion sin
su equivalente en `CodigoError`, la respuesta sera 500. Ademas, el contrato v0.1
usa `VALIDACION_FALLIDA` y `NO_ENCONTRADO`, mientras la implementacion responde
`ERROR_VALIDACION` y `RECURSO_NO_ENCONTRADO` (o codigos especificos por modulo).
Ambos desvios quedan registrados para tickets posteriores; este cambio no modifica
el contrato de errores.

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
