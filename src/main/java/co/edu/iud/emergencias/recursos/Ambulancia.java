package co.edu.iud.emergencias.recursos;

import co.edu.iud.emergencias.modelo.Ubicacion;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Datos y estado de una ambulancia.
 *
 * Quién escribe cada campo (para no necesitar más sincronización):
 *  - {@code estado}: solo {@link GestorRecursos}, siempre dentro de su cerrojo
 *    (los métodos {@code setEstado} y {@code setEnlace} son de paquete a propósito);
 *  - {@code posicion}, {@code emergenciaId} e inicio de misión: solo el hilo de
 *    la propia ambulancia;
 *  - el resto de hilos (monitor, interfaz) solo leen, y por eso los campos son {@code volatile}.
 */
public final class Ambulancia {

    private final String id;
    private final Ubicacion base;

    private volatile Ubicacion posicion;
    private volatile EstadoRecurso estado = EstadoRecurso.DISPONIBLE;
    private volatile boolean enlaceActivo = true;
    private volatile int emergenciaId = -1;
    private volatile long inicioMisionSimMs = -1;

    private final LongAdder ocupadoSimMs = new LongAdder();
    private final AtomicInteger misiones = new AtomicInteger();

    public Ambulancia(String id, Ubicacion base) {
        this.id = id;
        this.base = base;
        this.posicion = base;
    }

    public String id() {
        return id;
    }

    public Ubicacion base() {
        return base;
    }

    public Ubicacion posicion() {
        return posicion;
    }

    public void setPosicion(Ubicacion posicion) {
        this.posicion = posicion;
    }

    public EstadoRecurso estado() {
        return estado;
    }

    void setEstado(EstadoRecurso estado) {
        this.estado = estado;
    }

    public boolean enlaceActivo() {
        return enlaceActivo;
    }

    void setEnlace(boolean activo) {
        this.enlaceActivo = activo;
    }

    public int emergenciaId() {
        return emergenciaId;
    }

    public int misiones() {
        return misiones.get();
    }

    public void iniciarMision(int idEmergencia, long ahoraSimMs) {
        this.emergenciaId = idEmergencia;
        this.inicioMisionSimMs = ahoraSimMs;
        misiones.incrementAndGet();
    }

    public void finalizarMision(long ahoraSimMs) {
        long inicio = inicioMisionSimMs;
        if (inicio >= 0) {
            ocupadoSimMs.add(Math.max(0, ahoraSimMs - inicio));
        }
        inicioMisionSimMs = -1;
        emergenciaId = -1;
    }

    /** Tiempo total ocupada, incluida la misión en curso. */
    public long ocupadoSimMs(long ahoraSimMs) {
        long inicio = inicioMisionSimMs;
        return ocupadoSimMs.sum() + (inicio >= 0 ? Math.max(0, ahoraSimMs - inicio) : 0);
    }

    @Override
    public String toString() {
        return id + " (" + estado + ")";
    }
}
