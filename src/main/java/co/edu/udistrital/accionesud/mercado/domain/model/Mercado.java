package co.edu.udistrital.accionesud.mercado.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Agregado de dominio que modela un mercado bursátil y encapsula toda la lógica
 * de cálculo de estado considerando zonas horarias IANA, fines de semana,
 * cambios de horario de verano (DST) y feriados configurables.
 *
 * <p>Esta clase es pura (no depende de Spring ni de infraestructura) para poder
 * ser probada de forma aislada inyectando el instante a evaluar.</p>
 */
public final class Mercado {

    /** Límite de búsqueda hacia adelante para la próxima apertura/cierre. */
    private static final int DIAS_MAXIMO_BUSQUEDA = 14;

    private final CodigoMercado codigo;
    private final String nombre;
    private final ZoneId zonaHoraria;
    private final HorarioOperacion horario;
    private final Set<DayOfWeek> diasHabiles;
    private final CalendarioFeriados feriados;

    public Mercado(CodigoMercado codigo,
                   String nombre,
                   ZoneId zonaHoraria,
                   HorarioOperacion horario,
                   Set<DayOfWeek> diasHabiles,
                   CalendarioFeriados feriados) {
        this.codigo = Objects.requireNonNull(codigo, "El código es obligatorio");
        this.nombre = Objects.requireNonNull(nombre, "El nombre es obligatorio");
        this.zonaHoraria = Objects.requireNonNull(zonaHoraria, "La zona horaria es obligatoria");
        this.horario = Objects.requireNonNull(horario, "El horario es obligatorio");
        Set<DayOfWeek> dias = Objects.requireNonNull(diasHabiles, "Los días hábiles son obligatorios");
        if (dias.isEmpty()) {
            throw new IllegalArgumentException("Debe existir al menos un día hábil");
        }
        this.diasHabiles = Collections.unmodifiableSet(EnumSet.copyOf(dias));
        this.feriados = Objects.requireNonNull(feriados, "El calendario de feriados es obligatorio");
    }

    /**
     * Crea un mercado con la configuración por defecto de días hábiles (lunes a viernes).
     */
    public static Mercado conDiasHabilesEstandar(CodigoMercado codigo,
                                                 String nombre,
                                                 ZoneId zonaHoraria,
                                                 HorarioOperacion horario,
                                                 CalendarioFeriados feriados) {
        return new Mercado(codigo, nombre, zonaHoraria, horario,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                feriados);
    }

    public CodigoMercado codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    public ZoneId zonaHoraria() {
        return zonaHoraria;
    }

    public HorarioOperacion horario() {
        return horario;
    }

    public CalendarioFeriados feriados() {
        return feriados;
    }

    /**
     * Evalúa el estado del mercado en el instante dado.
     *
     * @param instante instante absoluto (normalmente provisto por el RelojPort).
     * @return snapshot inmutable con estado, próxima apertura, próximo cierre y restricciones.
     */
    public EstadoMercadoSnapshot evaluarEstado(java.time.Instant instante) {
        return evaluarEstado(instante, null);
    }

    /**
     * Evalúa el estado del mercado aplicando, si existe, un override reportado por
     * un proveedor externo (p. ej. un cierre no planeado o una apertura especial).
     *
     * @param instante         instante absoluto (normalmente provisto por el RelojPort).
     * @param abiertoReportado override de estado: {@code null} = usar el cálculo por horario.
     * @return snapshot inmutable con estado, próxima apertura, próximo cierre y restricciones.
     */
    public EstadoMercadoSnapshot evaluarEstado(java.time.Instant instante, Boolean abiertoReportado) {
        Objects.requireNonNull(instante, "El instante es obligatorio");
        ZonedDateTime ahora = instante.atZone(zonaHoraria);

        boolean abiertoPorHorario = estaAbierto(ahora);
        boolean abierto = abiertoReportado != null ? abiertoReportado : abiertoPorHorario;

        ZonedDateTime proximaApertura = calcularProximaApertura(ahora);
        ZonedDateTime proximoCierre = calcularProximoCierre(ahora);

        List<Restriccion> restricciones = new java.util.ArrayList<>(restriccionesVigentes(ahora.toLocalDate()));
        if (abiertoReportado != null && abiertoReportado != abiertoPorHorario) {
            restricciones.add(Restriccion.de(
                    "ESTADO_REPORTADO",
                    abiertoReportado
                            ? "Apertura no planeada reportada por el proveedor de datos"
                            : "Cierre no planeado reportado por el proveedor de datos"));
        }

        return new EstadoMercadoSnapshot(
                codigo,
                abierto ? EstadoMercado.ABIERTO : EstadoMercado.CERRADO,
                ahora,
                abierto ? null : proximaApertura.toInstant(),
                abierto ? proximoCierre.toInstant() : null,
                restricciones);
    }

    private boolean estaAbierto(ZonedDateTime ahora) {
        return esDiaOperativo(ahora.toLocalDate())
                && horario.contiene(ahora.toLocalTime());
    }

    /**
     * Un día es operativo si es día hábil (según la semana) y no es feriado.
     */
    private boolean esDiaOperativo(LocalDate fecha) {
        return diasHabiles.contains(fecha.getDayOfWeek()) && !feriados.esFeriado(codigo, fecha);
    }

    private ZonedDateTime calcularProximaApertura(ZonedDateTime ahora) {
        // Si hoy es operativo y aún no abre, la próxima apertura es hoy.
        if (esDiaOperativo(ahora.toLocalDate()) && ahora.toLocalTime().isBefore(horario.apertura())) {
            return aperturaEn(ahora.toLocalDate());
        }
        // Buscar el siguiente día operativo.
        for (int offset = 1; offset <= DIAS_MAXIMO_BUSQUEDA; offset++) {
            LocalDate fecha = ahora.toLocalDate().plusDays(offset);
            if (esDiaOperativo(fecha)) {
                return aperturaEn(fecha);
            }
        }
        throw new IllegalStateException(
                "No se encontró próxima apertura en " + DIAS_MAXIMO_BUSQUEDA + " días para " + codigo);
    }

    private ZonedDateTime calcularProximoCierre(ZonedDateTime ahora) {
        if (estaAbierto(ahora)) {
            return cierreEn(ahora.toLocalDate());
        }
        // Si el mercado está cerrado, el próximo cierre corresponde al siguiente día operativo.
        if (esDiaOperativo(ahora.toLocalDate()) && ahora.toLocalTime().isBefore(horario.apertura())) {
            return cierreEn(ahora.toLocalDate());
        }
        for (int offset = 1; offset <= DIAS_MAXIMO_BUSQUEDA; offset++) {
            LocalDate fecha = ahora.toLocalDate().plusDays(offset);
            if (esDiaOperativo(fecha)) {
                return cierreEn(fecha);
            }
        }
        throw new IllegalStateException(
                "No se encontró próximo cierre en " + DIAS_MAXIMO_BUSQUEDA + " días para " + codigo);
    }

    /**
     * Resuelve la apertura en una fecha concreta respetando la zona horaria.
     * El uso de {@link ZonedDateTime#of} con la zona garantiza el ajuste de DST.
     */
    private ZonedDateTime aperturaEn(LocalDate fecha) {
        return ZonedDateTime.of(fecha, horario.apertura(), zonaHoraria);
    }

    private ZonedDateTime cierreEn(LocalDate fecha) {
        return ZonedDateTime.of(fecha, horario.cierre(), zonaHoraria);
    }

    private List<Restriccion> restriccionesVigentes(LocalDate fecha) {
        return feriados.restriccionesPara(codigo, fecha);
    }

    /**
     * Duración restante de la sesión si el mercado está abierto en el instante dado.
     */
    public Optional<Duration> tiempoRestanteSesion(java.time.Instant instante) {
        ZonedDateTime ahora = instante.atZone(zonaHoraria);
        if (!estaAbierto(ahora)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(ahora, cierreEn(ahora.toLocalDate())));
    }
}
