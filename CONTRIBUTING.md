# Guia de contribucion

## Modelo de ramas

El repositorio usa GitHub Flow:

- `main` es la unica rama de larga duracion y debe estar protegida.
- No se permite push directo a `main`.
- Cada cambio se desarrolla en una rama corta y se integra mediante Pull
  Request.
- La rama se elimina despues de integrar el PR.

### Nombres de rama

```text
feature/SCRUM-XX-descripcion-corta
fix/SCRUM-XX-descripcion-corta
chore/SCRUM-XX-descripcion-corta
docs/SCRUM-XX-descripcion-corta
test/SCRUM-XX-descripcion-corta
```

Usar minusculas, guiones y la clave Jira cuando exista.

## Commits

Se usa Conventional Commits:

```text
<tipo>(<alcance>): <descripcion> [SCRUM-XX]
```

Tipos permitidos:

- `feat`: nueva funcionalidad.
- `fix`: correccion de defecto.
- `refactor`: cambio interno sin alterar comportamiento.
- `test`: pruebas.
- `docs`: documentacion.
- `build`: dependencias, Maven o configuracion de build.
- `ci`: integracion continua.
- `chore`: mantenimiento.
- `perf`: mejoras de rendimiento.

Ejemplos:

```text
feat(trace): agrega filtro X-Trace-Id [SCRUM-17]
test(error): cubre contrato de errores 400-500 [SCRUM-17]
docs(architecture): documenta estructura hexagonal [SCRUM-17]
```

Cada commit debe incluir el trailer:

```text
Refs: SCRUM-XX
```

No mezclar cambios no relacionados en un mismo commit.

## Pull Requests

Antes de abrir un PR:

1. Actualizar la rama con `main`.
2. Ejecutar `./mvnw -B clean verify`.
3. Verificar que no se versionan secretos ni archivos generados.
4. Completar la plantilla de PR.

Un PR debe:

- Tener un alcance pequeno y claro.
- Incluir pruebas cuando agregue logica.
- Mantener las reglas ArchUnit.
- Respetar el contrato de errores y el `traceId`.
- Tener al menos una revision aprobada.

## Estilo de codigo

- Java 17.
- UTF-8 y saltos de linea LF.
- 4 espacios en Java, 2 en YAML/JSON.
- Sin comentarios obvios; documentar solo decisiones no evidentes.
- Dominio y aplicacion sin anotaciones de Spring.
- Inyeccion de dependencias por constructor.
- Nombres de clases, metodos y variables en espanol cuando representen
  conceptos del negocio.

## Definition of Done

- La funcionalidad cumple los criterios de aceptacion.
- `./mvnw -B clean verify` pasa.
- Las reglas ArchUnit pasan.
- Los errores usan el contrato global.
- Los logs y respuestas conservan el `traceId`.
- La documentacion o el OpenAPI se actualizan si cambia el contrato.
