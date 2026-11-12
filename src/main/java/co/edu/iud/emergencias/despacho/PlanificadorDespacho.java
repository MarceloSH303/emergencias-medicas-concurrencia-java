package co.edu.iud.emergencias.despacho;

import co.edu.iud.emergencias.comunicacion.BusEventos;
import co.edu.iud.emergencias.comunicacion.Evento;
import co.edu.iud.emergencias.comunicacion.TipoEvento;
import co.edu.iud.emergencias.config.Configuracion;
import co.edu.iud.emergencias.modelo.Emergencia;
import co.edu.iud.emergencias.modelo.EstadoEmergencia;
import co.edu.iud.emergencias.modelo.Gravedad;
import co.edu.iud.emergencias.prioridad.CalculadorPrioridad;
import co.edu.iud.emergencias.recursos.GestorRecursos;
import co.edu.iud.emergencias.recursos.Reserva;
import co.edu.iud.emergencias.util.Reloj;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cola de emergencias pendientes y decisión de a quién atender.
 *
 * <h3>Por qué no una PriorityBlockingQueue</h3>
 * La prioridad aquí depende del tiempo (envejecimiento) y puede escalar. Modificar la clave de un
 * elemento dentro de un montículo rompe su invariante. En su lugar se guarda una lista simple y en cada
 * decisión se recalcula el puntaje de todas y se elige la mayor. Con decenas de pendientes el costo es
 * despreciable y la corrección es evidente.
 *
 * <h3>Sincronización</h3>
 * Un único cerrojo protege la lista. Los despachadores esperan en una {@link Condition} cuando no hay
 * trabajo o no alcanzan los recursos, y se despiertan cuando entra una emergencia o se libera un recurso.
 * Orden de cerrojos: planificador → gestor de recursos (nunca al revés).
 *
 * <h3>Adelanto (backfill)</h3>
 * Si la emergencia más urgente no puede atenderse por falta de un recurso, se evalúan las siguientes,
 * pero solo se les concede lo que no retrase a la primera. Así los recursos no se quedan ociosos y las
 * emergencias graves no pierden su turno.
 */
public final class PlanificadorDespacho {

    private static final long ESPERA_MAX_REAL_MS = 200;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition hayTrabajo = lock.newCondition();
    private final List<Emergencia> pendientes = new ArrayList<>();

    private final GestorRecursos recursos;
    private final CalculadorPrioridad calculador;
    private final BusEventos bus;
    private final Reloj reloj;
    private final long escalarCadaMs;
    private volatile boolean detenido;

    public PlanificadorDespacho(Configuracion config, GestorRecursos recursos, BusEventos bus, Reloj reloj) {
        this.recursos = recursos;
        this.calculador = new CalculadorPrioridad(config);
        this.bus = bus;
        this.reloj = reloj;
        this.escalarCadaMs = config.escalarGravedadCadaMs();
    }

    /** Una llamada nueva entra a la cola. */
    public void encolar(Emergencia e) {
        lock.lock();
        try {
            pendientes.add(e);
            bus.publicar(Evento.de(reloj.ahoraSimMs(), TipoEvento.EN_COLA, e, "",
                    "En cola (" + e.gravedad() + ")", -1));
            hayTrabajo.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Una emergencia vuelve a la cola tras un fallo; conserva su hora original de llegada. */
    public void reencolar(Emergencia e) {
        lock.lock();
        try {
            if (!pendientes.contains(e)) {
                pendientes.add(e);
            }
            hayTrabajo.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Avisa que se liberó algo (ambulancia, médico, equipo) para que se reconsidere la cola. */
    public void notificarCambio() {
        lock.lock();
        try {
            hayTrabajo.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void detener() {
        detenido = true;
        notificarCambio();
    }

    public void reanudar() {
        detenido = false;
    }

    public int pendientes() {
        lock.lock();
        try {
            return pendientes.size();
        } finally {
            lock.unlock();
        }
    }

    /** Copia de las pendientes con su prioridad actual (para el monitor). */
    public List<Emergencia> copiaPendientes() {
        lock.lock();
        try {
            long ahora = reloj.ahoraSimMs();
            for (Emergencia e : pendientes) {
                e.setPrioridad(calculador.puntaje(e, ahora));
            }
            return new ArrayList<>(pendientes);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Bloquea hasta que haya una emergencia que se pueda atender y devuelve su asignación con los
     * recursos ya reservados. Devuelve vacío si el planificador fue detenido o el hilo interrumpido.
     */
    public Optional<Asignacion> siguienteAsignacion() {
        lock.lock();
        try {
            while (!detenido) {
                if (pendientes.isEmpty()) {
                    hayTrabajo.await();
                    continue;
                }
                Optional<Asignacion> a = elegir();
                if (a.isPresent()) {
                    return a;
                }
                // Hay pendientes pero no alcanzan los recursos: se reintenta al liberarse algo o cada 200 ms
                // (el puntaje cambia con el tiempo aunque nada se libere).
                hayTrabajo.await(ESPERA_MAX_REAL_MS, TimeUnit.MILLISECONDS);
            }
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    /** Debe llamarse con el cerrojo tomado. */
    private Optional<Asignacion> elegir() {
        long ahora = reloj.ahoraSimMs();
        for (Emergencia e : pendientes) {
            escalarSiCorresponde(e, ahora);
            e.setPrioridad(calculador.puntaje(e, ahora));
        }
        pendientes.sort(Comparator.comparingDouble(Emergencia::prioridad).reversed()
                .thenComparingLong(Emergencia::llegadaSimMs)
                .thenComparingInt(Emergencia::id));

        Emergencia cabeza = pendientes.get(0);
        var reqCabeza = cabeza.gravedad().requisitos();
        for (int i = 0; i < pendientes.size(); i++) {
            Emergencia e = pendientes.get(i);
            boolean adelanto = i > 0;
            Optional<Reserva> r = recursos.intentarReservar(e.gravedad().requisitos(), e.ubicacion(),
                    adelanto ? reqCabeza : null);
            if (r.isPresent()) {
                pendientes.remove(i);
                e.transicionar(EstadoEmergencia.DESPACHADA);
                boolean primera = e.marcarDespacho(ahora);
                Reserva reserva = r.get();
                e.setAmbulanciaId(reserva.ambulancia().id());
                if (adelanto) {
                    bus.publicar(Evento.de(ahora, TipoEvento.ADELANTO, e, reserva.ambulancia().id(),
                            "Se atiende antes que #" + cabeza.id() + " (a esa le falta un recurso)", -1));
                }
                bus.publicar(Evento.de(ahora, TipoEvento.DESPACHADA, e, reserva.ambulancia().id(),
                        "Despacha " + reserva.ambulancia().id() + " (" + e.gravedad() + ")",
                        primera ? e.esperaHastaDespachoSimMs() : -1));
                return Optional.of(new Asignacion(e, reserva, adelanto));
            }
        }
        return Optional.empty();
    }

    private void escalarSiCorresponde(Emergencia e, long ahora) {
        if (e.gravedad() == Gravedad.CRITICO) {
            return;
        }
        if (ahora - e.ultimoEscalonSimMs() >= escalarCadaMs) {
            Gravedad antes = e.gravedad();
            e.escalar(ahora);
            bus.publicar(Evento.de(ahora, TipoEvento.PRIORIDAD_MODIFICADA, e, "",
                    "Empeora mientras espera: " + antes + " → " + e.gravedad(), -1));
        }
    }
}
