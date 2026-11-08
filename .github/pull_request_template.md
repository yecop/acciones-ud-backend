## Descripcion

<!-- Explica que cambia y por que. -->

Clave Jira: SCRUM-XX

## Tipo de cambio

- [ ] feat: nueva funcionalidad
- [ ] fix: correccion
- [ ] refactor: cambio interno
- [ ] test: pruebas
- [ ] docs: documentacion
- [ ] build/ci: infraestructura
- [ ] chore: mantenimiento

## Como probar

<!-- Pasos, comandos o escenarios. -->

```bash
./mvnw -B clean verify
```

## Checklist

- [ ] El dominio sigue sin depender de Spring ni de adaptadores.
- [ ] Los errores usan el contrato global.
- [ ] El `traceId` se conserva en logs y respuestas.
- [ ] Agregue o actualice pruebas.
- [ ] Actualice README/OpenAPI si cambia el contrato.
- [ ] No incluí secretos ni archivos generados.
