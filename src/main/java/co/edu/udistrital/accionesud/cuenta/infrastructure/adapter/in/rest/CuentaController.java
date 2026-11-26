package co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest;

import co.edu.udistrital.accionesud.cuenta.application.dto.ResultadoMovimiento;
import co.edu.udistrital.accionesud.cuenta.application.dto.ResultadoRecarga;
import co.edu.udistrital.accionesud.cuenta.application.port.in.ConsultarMovimientosUseCase;
import co.edu.udistrital.accionesud.cuenta.application.port.in.ConsultarSaldoUseCase;
import co.edu.udistrital.accionesud.cuenta.application.port.in.MoverFondosUseCase;
import co.edu.udistrital.accionesud.cuenta.application.port.in.RecargarSaldoUseCase;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.dto.RecargaResponse;
import co.edu.udistrital.accionesud.cuenta.domain.model.Saldo;
import co.edu.udistrital.accionesud.cuenta.domain.model.TipoMovimiento;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.dto.MovimientoFondosRequest;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.dto.OperacionFondosResponse;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.dto.PaginaMovimientosResponse;
import co.edu.udistrital.accionesud.cuenta.infrastructure.adapter.in.rest.dto.SaldoResponse;
import co.edu.udistrital.accionesud.shared.application.port.out.AccesoComisionistaPort;
import co.edu.udistrital.accionesud.shared.domain.exception.AccesoDenegadoException;
import co.edu.udistrital.accionesud.shared.domain.exception.ValidacionException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cuenta del usuario autenticado: saldo (SCRUM-25) y depósitos, retiros e historial (SCRUM-46).
 *
 * <p>El usuario sale del token (subject = id): nadie opera una cuenta ajena. Solo el titular
 * (INVERSIONISTA) deposita y retira; un COMISIONISTA puede consultar el historial de un cliente
 * con relación vigente pasando {@code inversionistaId}.</p>
 */
@RestController
@RequestMapping("/api/v1/cuenta")
public class CuentaController {

    static final String IDEMPOTENCIA = "Idempotency-Key";
    static final String REPETIDA = "Idempotent-Replayed";

    private final ConsultarSaldoUseCase consultarSaldoUseCase;
    private final MoverFondosUseCase moverFondos;
    private final ConsultarMovimientosUseCase movimientos;
    private final AccesoComisionistaPort accesoComisionista;
    private final RecargarSaldoUseCase recargas;

    public CuentaController(ConsultarSaldoUseCase consultarSaldoUseCase, MoverFondosUseCase moverFondos,
                            ConsultarMovimientosUseCase movimientos, AccesoComisionistaPort accesoComisionista,
                            RecargarSaldoUseCase recargas) {
        this.consultarSaldoUseCase = consultarSaldoUseCase;
        this.moverFondos = moverFondos;
        this.movimientos = movimientos;
        this.accesoComisionista = accesoComisionista;
        this.recargas = recargas;
    }

    /**
     * GET /api/v1/cuenta/saldo
     * Saldo disponible y reservado de la cuenta del usuario autenticado. La primera vez se aplica
     * el saldo inicial simulado. 404 {@code NO_ENCONTRADO} si el usuario no tiene cuenta.
     */
    @GetMapping("/saldo")
    public SaldoResponse obtenerSaldo(Authentication autenticacion) {
        Saldo saldo = consultarSaldoUseCase.consultarSaldo(usuarioId(autenticacion).toString());
        return SaldoResponse.desde(saldo);
    }

    /** POST /api/v1/cuenta/depositos — 201 con el movimiento y el saldo resultante. */
    @PostMapping("/depositos")
    @PreAuthorize("hasRole('INVERSIONISTA')")
    public ResponseEntity<OperacionFondosResponse> depositar(
            Authentication autenticacion,
            @RequestHeader(name = IDEMPOTENCIA, required = false) String clave,
            @RequestBody MovimientoFondosRequest solicitud) {
        return responder(moverFondos.depositar(usuarioId(autenticacion), solicitud.monto(), clave));
    }

    /**
     * POST /api/v1/cuenta/retiros — 201 con el movimiento y el saldo resultante; 422
     * {@code FONDOS_INSUFICIENTES} si supera el disponible (el saldo no cambia).
     */
    @PostMapping("/retiros")
    @PreAuthorize("hasRole('INVERSIONISTA')")
    public ResponseEntity<OperacionFondosResponse> retirar(
            Authentication autenticacion,
            @RequestHeader(name = IDEMPOTENCIA, required = false) String clave,
            @RequestBody MovimientoFondosRequest solicitud) {
        return responder(moverFondos.retirar(usuarioId(autenticacion), solicitud.monto(), clave));
    }

    /**
     * POST /api/v1/cuenta/recargas — recarga con pasarela (Stripe Checkout). 201 con el depósito
     * {@code PENDIENTE} y {@code urlPago}, a donde el frontend redirige. El saldo se acredita cuando
     * Stripe confirma el pago por webhook. 503 {@code PASARELA_NO_CONFIGURADA} si no está habilitada.
     */
    @PostMapping("/recargas")
    @PreAuthorize("hasRole('INVERSIONISTA')")
    public ResponseEntity<RecargaResponse> recargar(
            Authentication autenticacion,
            @RequestHeader(name = IDEMPOTENCIA, required = false) String clave,
            @RequestBody MovimientoFondosRequest solicitud) {
        ResultadoRecarga resultado = recargas.iniciar(usuarioId(autenticacion), solicitud.monto(), clave);
        RecargaResponse cuerpo = RecargaResponse.desde(resultado);
        if (resultado.repetido()) {
            return ResponseEntity.ok().header(REPETIDA, "true").body(cuerpo);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(cuerpo);
    }

    /** GET /api/v1/cuenta/movimientos — historial paginado, más recientes primero. */
    @GetMapping("/movimientos")
    @PreAuthorize("hasAnyRole('INVERSIONISTA', 'COMISIONISTA')")
    public PaginaMovimientosResponse listarMovimientos(
            Authentication autenticacion,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) UUID inversionistaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        UUID actor = usuarioId(autenticacion);
        UUID titular;
        if (tieneRol(autenticacion, "ROLE_COMISIONISTA")) {
            if (inversionistaId == null) {
                throw new ValidacionException("inversionistaId", "es obligatorio para un comisionista");
            }
            accesoComisionista.exigirAcceso(actor, inversionistaId);
            titular = inversionistaId;
        } else {
            if (inversionistaId != null && !inversionistaId.equals(actor)) {
                throw new AccesoDenegadoException("Solo puedes consultar los movimientos de tu propia cuenta");
            }
            titular = actor;
        }
        return PaginaMovimientosResponse.desde(movimientos.listar(titular, tipo, pagina, tamano));
    }

    /**
     * GET /api/v1/cuenta/health
     * Comprueba que el modulo de cuenta (SCRUM-25) esta activo. Util para
     * verificaciones rapidas de despliegue e integracion.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "modulo", "cuenta",
                "historia", "SCRUM-25",
                "estado", "activo");
    }

    /** Un reintento con la misma clave responde 200 y marca {@code Idempotent-Replayed: true}. */
    private static ResponseEntity<OperacionFondosResponse> responder(ResultadoMovimiento resultado) {
        OperacionFondosResponse cuerpo = OperacionFondosResponse.desde(resultado);
        if (resultado.repetido()) {
            return ResponseEntity.ok().header(REPETIDA, "true").body(cuerpo);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(cuerpo);
    }

    private static boolean tieneRol(Authentication autenticacion, String rol) {
        return autenticacion.getAuthorities().stream().anyMatch(a -> rol.equals(a.getAuthority()));
    }

    private static UUID usuarioId(Authentication autenticacion) {
        try {
            return UUID.fromString(autenticacion.getName());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El token no identifica a un usuario");
        }
    }
}
