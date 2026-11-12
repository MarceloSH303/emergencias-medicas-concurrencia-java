package co.edu.iud.emergencias.modelo;

/**
 * Una emergencia médica. Es el objeto que viaja por todo el sistema: nace en
 * un operador, espera en el planificador, la atiende una ambulancia y termina
 * en un hospital.
 *
 * Es compartida entre hilos (operador, planificador, ambulancia, interfaz), por
 * eso:
 *  - los campos que cambian son {@code volatile} para que todos vean el último valor;
 *  - las operaciones compuestas (validar una transición y cambiar el estado,
 *    marcar "la primera vez") son {@code synchronized} para que sean atómicas.
 */
public final class Emergencia {

    private final int id;
    private final Ubicacion ubicacion;
    private final Gravedad gravedadInicial;
    private final long llegadaSimMs;

    private volatile Gravedad gravedad;
    private volatile EstadoEmergencia estado = EstadoEmergencia.PENDIENTE;
    private volatile double prioridad;
    private volatile long ultimoEscalonSimMs;
    private volatile int reasignaciones;
    private volatile long despachoSimMs = -1;
    private volatile long escenaSimMs = -1;
    private volatile long finSimMs = -1;
    private volatile String ambulanciaId = "-";

    public Emergencia(int id, Ubicacion ubicacion, Gravedad gravedad, long llegadaSimMs) {
        this.id = id;
        this.ubicacion = ubicacion;
        this.gravedadInicial = gravedad;
        this.gravedad = gravedad;
        this.llegadaSimMs = llegadaSimMs;
        this.ultimoEscalonSimMs = llegadaSimMs;
    }

    public int id() {
        return id;
    }

    public Ubicacion ubicacion() {
        return ubicacion;
    }

    public Gravedad gravedad() {
        return gravedad;
    }

    /** Gravedad con la que se reportó la llamada, sin los escalamientos posteriores. */
    public Gravedad gravedadInicial() {
        return gravedadInicial;
    }

    public EstadoEmergencia estado() {
        return estado;
    }

    public long llegadaSimMs() {
        return llegadaSimMs;
    }

    public double prioridad() {
        return prioridad;
    }

    public void setPrioridad(double prioridad) {
        this.prioridad = prioridad;
    }

    public int reasignaciones() {
        return reasignaciones;
    }

    public String ambulanciaId() {
        return ambulanciaId;
    }

    public void setAmbulanciaId(String ambulanciaId) {
        this.ambulanciaId = ambulanciaId;
    }

    public long ultimoEscalonSimMs() {
        return ultimoEscalonSimMs;
    }

    /** Cambia de estado validando que la transición exista en el ciclo de vida. */
    public synchronized void transicionar(EstadoEmergencia nuevo) {
        if (!estado.puedePasarA(nuevo)) {
            throw new IllegalStateException(
                    "Transición inválida en la emergencia #" + id + ": " + estado + " → " + nuevo);
        }
        estado = nuevo;
    }

    /** El paciente empeora: sube un nivel de gravedad y reinicia el reloj del escalamiento. */
    public synchronized void escalar(long ahoraSimMs) {
        gravedad = gravedad.siguienteNivel();
        ultimoEscalonSimMs = ahoraSimMs;
    }

    public synchronized void registrarReasignacion() {
        reasignaciones++;
    }

    /** Registra el primer despacho. Devuelve {@code true} solo la primera vez. */
    public synchronized boolean marcarDespacho(long ahoraSimMs) {
        if (despachoSimMs >= 0) {
            return false;
        }
        despachoSimMs = ahoraSimMs;
        return true;
    }

    /** Registra la primera llegada a la escena. Devuelve {@code true} solo la primera vez. */
    public synchronized boolean marcarEscena(long ahoraSimMs) {
        if (escenaSimMs >= 0) {
            return false;
        }
        escenaSimMs = ahoraSimMs;
        return true;
    }

    public synchronized void marcarFin(long ahoraSimMs) {
        finSimMs = ahoraSimMs;
    }

    /** Tiempo que esperó en la cola hasta su primer despacho. */
    public long esperaHastaDespachoSimMs() {
        return despachoSimMs - llegadaSimMs;
    }

    /** Tiempo de respuesta: desde la llamada hasta la primera llegada de una ambulancia a la escena. */
    public long respuestaSimMs() {
        return escenaSimMs - llegadaSimMs;
    }

    /** Duración total, desde la llamada hasta el ingreso al hospital. */
    public long duracionTotalSimMs() {
        return finSimMs - llegadaSimMs;
    }

    @Override
    public String toString() {
        return "#" + id + " " + gravedad + " " + estado;
    }
}
