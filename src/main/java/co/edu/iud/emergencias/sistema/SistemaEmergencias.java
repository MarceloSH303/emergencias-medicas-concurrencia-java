package co.edu.iud.emergencias.sistema;

import co.edu.iud.emergencias.comunicacion.BusEventos;
import co.edu.iud.emergencias.config.Configuracion;
import co.edu.iud.emergencias.despacho.PlanificadorDespacho;
import co.edu.iud.emergencias.hilos.Despachador;
import co.edu.iud.emergencias.hilos.Entorno;
import co.edu.iud.emergencias.hilos.FabricaHilos;
import co.edu.iud.emergencias.hilos.GeneradorEmergencias;
import co.edu.iud.emergencias.hilos.HiloAmbulancia;
import co.edu.iud.emergencias.hilos.InyectorFallos;
import co.edu.iud.emergencias.hilos.Llamada;
import co.edu.iud.emergencias.hilos.Operador;
import co.edu.iud.emergencias.modelo.Ciudad;
import co.edu.iud.emergencias.modelo.Emergencia;
import co.edu.iud.emergencias.modelo.EstadoEmergencia;
import co.edu.iud.emergencias.modelo.Gravedad;
import co.edu.iud.emergencias.monitoreo.Estadisticas;
import co.edu.iud.emergencias.monitoreo.Instantanea;
import co.edu.iud.emergencias.recursos.Ambulancia;
import co.edu.iud.emergencias.recursos.EstadoRecurso;
import co.edu.iud.emergencias.recursos.GestorRecursos;
import co.edu.iud.emergencias.recursos.Hospital;
import co.edu.iud.emergencias.util.Reloj;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fachada y "cableado" del sistema: crea los recursos y los hilos, los conecta y ofrece una API simple
 * a la interfaz gráfica, al benchmark y a las pruebas.
 *
 * Hilos: 1 generador de llamadas, N operadores, M despachadores, una ambulancia por hilo, 1 inyector de
 * fallos, 1 hilo del bus de eventos y un pequeño pool programador (altas de hospital y recuperaciones).
 */
public final class SistemaEmergencias implements AutoCloseable {

    private final Configuracion config;
    private final Reloj reloj;
    private final BusEventos bus = new BusEventos();
    private final Estadisticas estadisticas = new Estadisticas();
    private final GestorRecursos recursos;
    private final PlanificadorDespacho planificador;
    private final ScheduledThreadPoolExecutor programador = new ScheduledThreadPoolExecutor(2, new FabricaHilos("programador"));
    private final Map<Integer, Emergencia> activas = new ConcurrentHashMap<>();
    private final BlockingQueue<Llamada> llamadas = new LinkedBlockingQueue<>();
    private final Entorno entorno;
    private final GeneradorEmergencias generador;
    private final InyectorFallos inyector;
    private final Map<String, HiloAmbulancia> hilosAmbulancia = new LinkedHashMap<>();
    private final List<Thread> hilos = new ArrayList<>();
    private volatile boolean enMarcha;

    public SistemaEmergencias(Configuracion config) {
        this(config, new Reloj(config.escalaTiempo()));
    }

    /** Constructor para pruebas: permite inyectar un reloj propio. */
    public SistemaEmergencias(Configuracion config, Reloj reloj) {
        this.config = config;
        this.reloj = reloj;
        bus.suscribir(estadisticas);

        List<Ambulancia> ambulancias = new ArrayList<>();
        for (Ciudad.PuntoNombrado b : Ciudad.BASES) {
            ambulancias.add(new Ambulancia(b.nombre(), b.ubicacion()));
        }
        List<Hospital> hospitales = new ArrayList<>();
        for (Ciudad.PuntoNombrado h : Ciudad.HOSPITALES) {
            hospitales.add(new Hospital(h.nombre(), h.ubicacion(), config.camasPorHospital()));
        }
        recursos = new GestorRecursos(ambulancias, hospitales, config.medicos(), config.equipos());
        planificador = new PlanificadorDespacho(config, recursos, bus, reloj);
        recursos.setAlCambiar(planificador::notificarCambio);
        entorno = new Entorno(config, reloj, bus, recursos, planificador, programador, activas);
        generador = new GeneradorEmergencias(llamadas, reloj, config.llamadasPorMin());
        inyector = new InyectorFallos(entorno);
    }

    public synchronized void iniciar() {
        if (enMarcha) {
            return;
        }
        enMarcha = true;
        AtomicInteger secuencia = new AtomicInteger();
        for (Ambulancia a : recursos.ambulancias()) {
            HiloAmbulancia h = new HiloAmbulancia(a, entorno);
            hilosAmbulancia.put(a.id(), h);
            lanzar(h, "ambulancia-" + a.id());
        }
        for (int i = 1; i <= config.operadores(); i++) {
            lanzar(new Operador("Operador " + i, llamadas, secuencia, entorno), "operador-" + i);
        }
        for (int i = 1; i <= config.despachadores(); i++) {
            lanzar(new Despachador(entorno, hilosAmbulancia), "despachador-" + i);
        }
        lanzar(generador, "generador");
        lanzar(inyector, "inyector-fallos");
        // La secuencia de ids se comparte con las llamadas manuales a través de los operadores.
    }

    private void lanzar(Runnable r, String nombre) {
        Thread t = new Thread(r, nombre);
        t.setDaemon(true);
        hilos.add(t);
        t.start();
    }

    public synchronized void detener() {
        if (!enMarcha) {
            return;
        }
        enMarcha = false;
        planificador.detener();
        for (Thread t : hilos) {
            t.interrupt();
        }
        programador.shutdownNow();
        hilos.clear();
    }

    @Override
    public void close() {
        detener();
        bus.close();
    }

    // ---- control desde la interfaz ----

    public boolean enMarcha() {
        return enMarcha;
    }

    public void setLlamadasPorMin(double v) {
        generador.setLlamadasPorMin(v);
    }

    public double llamadasPorMin() {
        return generador.llamadasPorMin();
    }

    /** Pausa o reanuda la llegada automática de llamadas (los hilos siguen vivos). */
    public void setGeneracion(boolean activa) {
        generador.setActivo(activa);
    }

    public void setFallosAutomaticos(boolean v) {
        inyector.setAutomatico(v);
    }

    public void crearManual(Gravedad g) {
        generador.llamadaManual(g);
    }

    /** Provoca la caída de una ambulancia (prefiere una que esté en misión para mostrar la recuperación). */
    public boolean fallarAmbulanciaAleatoria() {
        List<Ambulancia> candidatas = new ArrayList<>();
        for (Ambulancia a : recursos.ambulancias()) {
            if (a.enlaceActivo() && (a.estado() == EstadoRecurso.EN_CAMINO || a.estado() == EstadoRecurso.OCUPADA)) {
                candidatas.add(a);
            }
        }
        if (candidatas.isEmpty()) {
            for (Ambulancia a : recursos.ambulancias()) {
                if (a.enlaceActivo()) {
                    candidatas.add(a);
                }
            }
        }
        if (candidatas.isEmpty()) {
            return false;
        }
        return inyector.fallarAmbulancia(candidatas.get(ThreadLocalRandom.current().nextInt(candidatas.size())));
    }

    // ---- consultas ----

    public Configuracion config() {
        return config;
    }

    public Reloj reloj() {
        return reloj;
    }

    public BusEventos bus() {
        return bus;
    }

    public Estadisticas estadisticas() {
        return estadisticas;
    }

    public GestorRecursos recursos() {
        return recursos;
    }

    public PlanificadorDespacho planificador() {
        return planificador;
    }

    /** Utilización media de las ambulancias (0..1) hasta este momento. */
    public double utilizacion() {
        long ahora = Math.max(1, reloj.ahoraSimMs());
        double suma = 0;
        for (Ambulancia a : recursos.ambulancias()) {
            suma += a.ocupadoSimMs(ahora) / (double) ahora;
        }
        return suma / recursos.ambulancias().size();
    }

    public Instantanea instantanea() {
        long ahora = reloj.ahoraSimMs();
        planificador.copiaPendientes(); // refresca el puntaje de las pendientes
        List<Instantanea.FilaEmergencia> filas = new ArrayList<>();
        for (Emergencia e : activas.values()) {
            filas.add(new Instantanea.FilaEmergencia(e.id(), e.gravedad(), e.gravedadInicial(), e.estado(),
                    e.prioridad(), Math.max(0, ahora - e.llegadaSimMs()) / 1000.0, e.ambulanciaId(),
                    e.ubicacion().x(), e.ubicacion().y()));
        }
        filas.sort(Comparator.comparing((Instantanea.FilaEmergencia f) -> f.estado() != EstadoEmergencia.PENDIENTE)
                .thenComparing(Comparator.comparingDouble(Instantanea.FilaEmergencia::prioridad).reversed())
                .thenComparingInt(Instantanea.FilaEmergencia::id));

        List<Instantanea.FilaAmbulancia> amb = new ArrayList<>();
        for (Ambulancia a : recursos.ambulancias()) {
            amb.add(new Instantanea.FilaAmbulancia(a.id(), a.estado(), a.enlaceActivo(), a.posicion().x(),
                    a.posicion().y(), a.emergenciaId(), a.misiones(),
                    a.ocupadoSimMs(ahora) / (double) Math.max(1, ahora)));
        }
        List<Instantanea.FilaHospital> hos = new ArrayList<>();
        for (Hospital h : recursos.hospitales()) {
            hos.add(new Instantanea.FilaHospital(h.nombre(), h.camasLibres(), h.capacidad(), h.enLinea(),
                    h.ubicacion().x(), h.ubicacion().y()));
        }
        return new Instantanea(ahora, filas, amb, hos, planificador.pendientes(), recursos.medicosLibres(),
                recursos.totalMedicos(), recursos.equiposLibres(), recursos.totalEquipos(), enMarcha,
                estadisticas.resumen());
    }

    /** ¿No queda nada en curso? (para pruebas y benchmark). Las camas pueden tardar en liberarse por las altas. */
    public boolean enReposo() {
        if (!activas.isEmpty() || !llamadas.isEmpty() || planificador.pendientes() > 0) {
            return false;
        }
        for (Ambulancia a : recursos.ambulancias()) {
            if (a.estado() == EstadoRecurso.EN_CAMINO || a.estado() == EstadoRecurso.OCUPADA) {
                return false;
            }
        }
        return true;
    }

    /** Espera (tiempo real) a que el sistema quede en reposo y el bus entregue todo. */
    public boolean esperarReposo(long timeoutRealMs) throws InterruptedException {
        long limite = System.nanoTime() + timeoutRealMs * 1_000_000L;
        while (!enReposo()) {
            if (System.nanoTime() > limite) {
                return false;
            }
            Thread.sleep(20);
        }
        return bus.esperarEntrega(Math.max(100, (limite - System.nanoTime()) / 1_000_000L));
    }
}
