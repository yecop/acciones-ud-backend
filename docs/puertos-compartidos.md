# Puertos, eventos y auditoría compartida (SCRUM-33)

Contratos comunes para que los módulos se implementen en paralelo sin acoplarse entre sí.
Todo vive en `co.edu.udistrital.accionesud.shared`.

| Puerto (`shared.application.port.out`) | Para qué | Adaptador por defecto | Lo reemplaza |
|---|---|---|---|
| `TransaccionPort` | Unidad de trabajo atómica sin depender de Spring | Spring (`TransactionTemplate`); sin BD, transacción local | — |
| `PublicadorEventosPort` | Publicar eventos de dominio | Eventos de Spring, entregados **tras el commit** | — |
| `RegistroAuditoriaPort` | Auditoría append-only con traceId | Tabla `auditoria_evento` (V12); sin BD, memoria | — |
| `TrazabilidadPort` | traceId de la solicitud en la capa de aplicación | MDC (`TraceIdFilter`) | — |
| `BrokerOrdenesPort` | Enviar, consultar y cancelar órdenes | `BrokerOrdenesSimuladoAdapter` | SCRUM-43 (Alpaca) |
| `AccesoPremiumPort` | ¿El usuario es premium? | `AccesoPremiumEnMemoriaAdapter` (todos BASICO) | SCRUM-54 (Stripe) |
| `AccesoComisionistaPort` | ¿El comisionista tiene relación vigente con el cliente? | `AccesoComisionistaEnMemoriaAdapter` (nadie) | SCRUM-52 |

`RegistradorAuditoria` (`shared.application.service`) arma los registros de auditoría y los
metadatos de eventos con la hora y el traceId actuales.

## Uso en un caso de uso

```java
public class DepositarService {
    private final TransaccionPort transaccion;
    private final RegistradorAuditoria auditoria;
    private final PublicadorEventosPort eventos;
    // ...

    public void depositar(UUID actor, UUID cuenta, BigDecimal monto) {
        transaccion.enTransaccion(() -> {
            BigDecimal saldo = cuentas.sumar(cuenta, monto);              // escritura de negocio
            auditoria.exitoso(actor, "DEPOSITO", "CUENTA", cuenta.toString(),
                    Map.of("monto", monto.toPlainString()));               // misma transacción
            eventos.publicar(new Eventos.MovimientoFondosRegistrado(
                    auditoria.metadatosEvento(actor), cuenta, "DEPOSITO", monto, saldo));
        });
        // Aquí (después del commit) los consumidores ya recibieron el evento.
    }
}
```

## Reglas

- **Eventos solo tras el commit.** Dentro de `TransaccionPort` el evento queda en espera; si la
  transacción hace rollback, no se entrega. Fuera de transacción se entrega de inmediato.
- **Consumidores:** `@EventListener` sobre el tipo de evento (`Eventos.OrdenEjecutada`, etc.). Deben
  ser idempotentes con `metadatos().id()`. Si un consumidor falla, se registra en el log y no
  deshace la operación ya confirmada.
- **Auditoría:** se escribe en la misma transacción que el cambio (con rollback, desaparece). La
  tabla `auditoria_evento` rechaza `UPDATE`, `DELETE` y `TRUNCATE`. No guardar secretos en `detalle`.
- **Errores de acceso:** `AccesoDenegadoException` responde 403. `exigirPremium` devuelve
  `FUNCION_PREMIUM` y `exigirAcceso` del comisionista devuelve `ACCESO_DENEGADO`; el frontend ya
  tiene mensajes para ambos.
- **Broker:** `idCliente` es la clave de idempotencia (usar el UUID de la orden). Reenviar no
  duplica la orden.

## Eventos disponibles (`shared.domain.evento.Eventos`)

`OrdenCreada`, `OrdenEstadoCambiado`, `OrdenEjecutada`, `MovimientoFondosRegistrado`,
`SuscripcionActualizada`, `AlertaPrecioDisparada`, `AsociacionComisionistaCambiada`,
`PropuestaOrdenesCreada`. Todos llevan `MetadatosEvento` (id, fecha, traceId, actor). Los estados
viajan como texto para no acoplar a los consumidores con los enums de cada módulo.

## Reemplazar un adaptador simulado

Los adaptadores simulados se activan por defecto con estas propiedades:

```properties
accionesud.integraciones.broker=simulado        # SCRUM-43 registra el suyo con =alpaca
accionesud.integraciones.premium=simulado       # SCRUM-54 con =stripe
accionesud.integraciones.premium-usuarios=      # UUIDs premium en el simulado (separados por coma)
accionesud.integraciones.comisionistas=memoria  # SCRUM-52 con =jdbc
```

El adaptador real se declara con `@ConditionalOnProperty(prefix = "accionesud.integraciones",
name = "broker", havingValue = "alpaca")` y se activa cambiando la propiedad en el perfil.

## Pruebas

- Sin base de datos, el perfil `test` usa `TransaccionLocalAdapter` y `AuditoriaEnMemoriaAdapter`
  con el mismo contrato (eventos y auditoría solo tras commit).
- Los adaptadores simulados sirven de fakes: `BrokerOrdenesSimuladoAdapter.simularCaida(true)`,
  `fijarEstado(...)`; `AccesoPremiumEnMemoriaAdapter.otorgar(id)`;
  `AccesoComisionistaEnMemoriaAdapter.asociar(inv, com)`.
- `AuditoriaPostgresIntegrationTest` verifica commit, rollback, traceId e inmutabilidad contra
  PostgreSQL real (Testcontainers).

## Migración

`V12__auditoria_evento.sql`. SCRUM-32 debe usar **V13 en adelante** para sus tablas.
