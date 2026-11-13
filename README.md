# Acciones UD - Backend API

Backend de la plataforma de Day Trading **Acciones UD**. Implementa un nucleo
hexagonal en Spring Boot para aislar la logica de negocio de los proveedores
externos (Alpaca, Alpha Vantage, Stripe), la persistencia y los canales de
entrada.

## Documentacion de referencia

- `AccionesUD-Docs/SAD_AccionesUD.pdf`: Documento de Diseno y Arquitectura de
  Software (SAD).
- `AccionesUD-Docs/API_Endpoints_AccionesUD_2-1.pdf`: contrato de API REST.
- `AccionesUD-Docs/2026-3 Instrumento Financiero.pdf`: enunciado del cliente.

## Stack

- Java 17
- Spring Boot 3.3.x
- Maven Wrapper (`./mvnw`)
- JUnit 5, Mockito, MockMvc y ArchUnit
- SpringDoc OpenAPI
- Spring Security (configuracion base)
- PostgreSQL y Redis en fases posteriores

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
| `dev` | desarrollo local | Swagger habilitado, logs legibles con traceId |
| `test` | pruebas automatizadas | usuario de prueba en memoria |
| `prod` | despliegue | logs JSON, Swagger deshabilitado por defecto |

El perfil por defecto es `dev`.

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

## Endpoints base

| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | `/actuator/health` | estado de la aplicacion |
| GET | `/v3/api-docs` | especificacion OpenAPI |
| GET | `/swagger-ui.html` | interfaz Swagger UI |
| POST | `/api/v1/auth/registro` | registro de usuario y alta en Alpaca sandbox (público) |
| POST | `/api/v1/admin/altas/reintentar` | reintenta altas pendientes (rol ADMIN) |

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

## Seguridad

La base incluye Spring Security en modo stateless con HTTP Basic para `dev` y
`test`. El modulo `auth` reemplazara esta configuracion con MFA y roles reales.

Rutas publicas:

- `/actuator/health/**`
- `/actuator/info`
- `/v3/api-docs/**`
- `/swagger-ui/**`
- `/swagger-ui.html`
- `/error`

El resto requiere autenticacion. La ruta `/api/v1/admin/**` requiere rol
`ADMIN`.

## Variables de entorno

Copiar `.env.example` y ajustar los valores locales. En produccion no se deben
versionar secretos.

## Base de datos local

```bash
docker compose up -d      # PostgreSQL, Redis y Mailpit
./mvnw spring-boot:run    # Flyway aplica las migraciones al arrancar
docker compose down -v    # reinicia la base desde cero
```

Las migraciones están en `src/main/resources/db/migration`. No se edita una
migración ya fusionada: se crea una nueva.

## Ramas y commits

Se usa GitHub Flow:

- `main` es la unica rama de larga duracion y esta protegida.
- Cada cambio entra por una rama corta y un Pull Request.
- Nombres de rama: `feature/SCRUM-XX-slug`, `fix/SCRUM-XX-slug`,
  `chore/SCRUM-XX-slug`, `docs/SCRUM-XX-slug`, `test/SCRUM-XX-slug`.
- Commits con Conventional Commits y clave Jira, por ejemplo:
  `feat(architecture): crea estructura hexagonal [SCRUM-17]`.
- Ver `CONTRIBUTING.md` para el flujo completo.
