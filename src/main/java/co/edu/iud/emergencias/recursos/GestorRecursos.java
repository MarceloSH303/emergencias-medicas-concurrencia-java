package co.edu.iud.emergencias.recursos;

import co.edu.iud.emergencias.modelo.Requisitos;
import co.edu.iud.emergencias.modelo.Ubicacion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Dueño de los recursos compartidos: ambulancias, médicos y equipos (los
 * hospitales se administran con su propio semáforo de camas).
 *
 * <h3>Cómo evita las condiciones de carrera</h3>
 * Todo el estado mutable (contadores de médicos y equipos, estado de las
 * ambulancias) se lee y se modifica solo con el cerrojo {@code lock} tomado.
 *
 * <h3>Cómo evita el deadlock</h3>
 * Un deadlock necesita que un hilo SOSTENGA un recurso mientras ESPERA otro
 * (por ejemplo, tener la ambulancia y esperar un médico, mientras otro hilo
 * tiene el médico y espera la ambulancia). Aquí nunca ocurre porque la
 * reserva es de "todo o nada": {@link #intentarReservar} comprueba que alcanza
 * TODO lo que la emergencia necesita y, solo entonces, lo toma en una única
 * sección crítica. Si algo falta no toma nada y no espera dentro del método.
 * Además el cerrojo se mantiene durante muy poco tiempo y nunca se llama a
 * código externo mientras está tomado (la notificación {@code alCambiar} se
 * dispara después de soltarlo).
 *
 * <h3>Jerarquía de cerrojos</h3>
 * Si un hilo necesita los dos, el orden es siempre planificador → gestor de
 * recursos. Este gestor jamás llama al planificador con su cerrojo tomado.
 */
public final class GestorRecursos {

    private final ReentrantLock lock = new ReentrantLock();
    private final List<Ambulancia> ambulancias;
    private final List<Hospital> hospitales;
    private final int totalMedicos;
    private final int totalEquipos;

    // Protegidos por 'lock'
    private int medicosLibres;
    private int equiposLibres;

    private volatile Runnable alCambiar = () -> {
    };

    public GestorRecursos(List<Ambulancia> ambulancias, List<Hospital> hospitales,
                          int totalMedicos, int totalEquipos) {
        this.ambulancias = List.copyOf(ambulancias);
        this.hospitales = List.copyOf(hospitales);
        this.totalMedicos = totalMedicos;
        this.totalEquipos = totalEquipos;
        this.medicosLibres = totalMedicos;
        this.equiposLibres = totalEquipos;
    }

    /** Callback que se ejecuta (sin cerrojos tomados) cada vez que se libera algo que otros esperan. */
    public void setAlCambiar(Runnable alCambiar) {
        this.alCambiar = alCambiar;
    }

    public List<Ambulancia> ambulancias() {
        return ambulancias;
    }

    public List<Hospital> hospitales() {
        return hospitales;
    }

    public int totalMedicos() {
        return totalMedicos;
    }

    public int totalEquipos() {
        return totalEquipos;
    }

    // ------------------------------------------------------------------
    // Reserva (todo o nada)
    // ------------------------------------------------------------------

    /**
     * Intenta reservar, de una sola vez, todo lo que pide {@code requisitos}.
     *
     * @param destino   ubicación de la emergencia: se elige la ambulancia libre más cercana
     * @param protegido requisitos de la emergencia más prioritaria que está bloqueada (o {@code null}).
     *                  Si se pasa, la reserva solo se concede cuando NO retrasa a esa emergencia
     *                  (ver {@link #retrasaria}). Permite aprovechar recursos ociosos sin
     *                  quitarle el turno a los casos más graves.
     * @return la reserva, o vacío si no alcanzan los recursos (sin haber tomado nada)
     */
    public Optional<Reserva> intentarReservar(Requisitos requisitos, Ubicacion destino,
                                              Requisitos protegido) {
        lock.lock();
        try {
            int ambulanciasLibres = contarAmbulanciasLibres();
            if (ambulanciasLibres < requisitos.ambulancias()
                    || medicosLibres < requisitos.medicos()
                    || equiposLibres < requisitos.equipos()) {
                return Optional.empty();
            }
            if (protegido != null && retrasaria(requisitos, protegido, ambulanciasLibres)) {
                return Optional.empty();
            }
            Ambulancia elegida = ambulanciaLibreMasCercana(destino);
            elegida.setEstado(EstadoRecurso.EN_CAMINO);
            medicosLibres -= requisitos.medicos();
            equiposLibres -= requisitos.equipos();
            return Optional.of(new Reserva(elegida, requisitos.medicos(), requisitos.equipos()));
        } finally {
            lock.unlock();
        }
    }

    /**
     * ¿Conceder {@code candidato} dejaría a {@code cabeza} sin algo que hoy sí tiene cubierto, o
     * le quitaría un recurso en el que ya está corta? Se evalúa recurso por recurso.
     * Debe llamarse con el cerrojo tomado.
     */
    private boolean retrasaria(Requisitos candidato, Requisitos cabeza, int ambulanciasLibres) {
        return retrasa(ambulanciasLibres, candidato.ambulancias(), cabeza.ambulancias())
                || retrasa(medicosLibres, candidato.medicos(), cabeza.medicos())
                || retrasa(equiposLibres, candidato.equipos(), cabeza.equipos());
    }

    private static boolean retrasa(int libres, int pideCandidato, int necesitaCabeza) {
        if (pideCandidato == 0) {
            return false;
        }
        if (libres >= necesitaCabeza) {
            // La cabeza ya tiene este recurso cubierto: debe quedarle lo suficiente.
            return libres - pideCandidato < necesitaCabeza;
        }
        // La cabeza está corta de este recurso: no se le puede quitar el que llegue.
        return true;
    }

    private int contarAmbulanciasLibres() {
        int libres = 0;
        for (Ambulancia a : ambulancias) {
            if (a.estado() == EstadoRecurso.DISPONIBLE) {
                libres++;
            }
        }
        return libres;
    }

    private Ambulancia ambulanciaLibreMasCercana(Ubicacion destino) {
        Ambulancia mejor = null;
        double mejorDistancia = Double.MAX_VALUE;
        for (Ambulancia a : ambulancias) {
            if (a.estado() == EstadoRecurso.DISPONIBLE) {
                double d = a.base().distanciaA(destino);
                if (d < mejorDistancia) {
                    mejorDistancia = d;
                    mejor = a;
                }
            }
        }
        return mejor;
    }

    // ------------------------------------------------------------------
    // Liberación (idempotente)
    // ------------------------------------------------------------------

    /** La ambulancia llegó a la escena: pasa de "en camino" a "ocupada". */
    public void marcarOcupada(Ambulancia ambulancia) {
        lock.lock();
        try {
            ambulancia.setEstado(EstadoRecurso.OCUPADA);
        } finally {
            lock.unlock();
        }
    }

    /** Devuelve médicos y equipos (cuando el paciente ya está en el hospital o la misión se cancela). */
    public void liberarPersonal(Reserva reserva) {
        if (!reserva.marcarPersonalLiberado()) {
            return;
        }
        lock.lock();
        try {
            medicosLibres += reserva.medicos();
            equiposLibres += reserva.equipos();
            if (medicosLibres > totalMedicos || equiposLibres > totalEquipos) {
                throw new IllegalStateException("Se liberaron más recursos de los que existen.");
            }
        } finally {
            lock.unlock();
        }
        alCambiar.run();
    }

    /** La ambulancia volvió a su base (o la misión se canceló): queda disponible o sin enlace. */
    public void liberarAmbulancia(Reserva reserva) {
        if (!reserva.marcarAmbulanciaLiberada()) {
            return;
        }
        Ambulancia a = reserva.ambulancia();
        lock.lock();
        try {
            a.setPosicion(a.base());
            a.setEstado(a.enlaceActivo() ? EstadoRecurso.DISPONIBLE : EstadoRecurso.FUERA_DE_SERVICIO);
        } finally {
            lock.unlock();
        }
        alCambiar.run();
    }

    // ------------------------------------------------------------------
    // Fallos de comunicación
    // ------------------------------------------------------------------

    /**
     * La ambulancia pierde el enlace. Si estaba libre queda fuera de servicio de inmediato; si estaba
     * en una misión, su hilo lo detecta en el siguiente paso y cancela la misión.
     *
     * @return {@code false} si ya estaba sin enlace
     */
    public boolean caerEnlace(Ambulancia ambulancia) {
        lock.lock();
        try {
            if (!ambulancia.enlaceActivo()) {
                return false;
            }
            ambulancia.setEnlace(false);
            if (ambulancia.estado() == EstadoRecurso.DISPONIBLE) {
                ambulancia.setEstado(EstadoRecurso.FUERA_DE_SERVICIO);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Se restablece el enlace: si la ambulancia estaba parada, vuelve a estar disponible. */
    public void restaurarEnlace(Ambulancia ambulancia) {
        lock.lock();
        try {
            ambulancia.setEnlace(true);
            if (ambulancia.estado() == EstadoRecurso.FUERA_DE_SERVICIO) {
                ambulancia.setEstado(EstadoRecurso.DISPONIBLE);
            }
        } finally {
            lock.unlock();
        }
        alCambiar.run();
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    public int medicosLibres() {
        lock.lock();
        try {
            return medicosLibres;
        } finally {
            lock.unlock();
        }
    }

    public int equiposLibres() {
        lock.lock();
        try {
            return equiposLibres;
        } finally {
            lock.unlock();
        }
    }

    /** Hospitales ordenados del más cercano al más lejano de {@code desde}. */
    public List<Hospital> hospitalesPorDistancia(Ubicacion desde) {
        List<Hospital> copia = new ArrayList<>(hospitales);
        copia.sort(Comparator.comparingDouble(h -> h.ubicacion().distanciaA(desde)));
        return copia;
    }
}
