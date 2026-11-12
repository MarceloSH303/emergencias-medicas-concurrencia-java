package co.edu.iud.emergencias.recursos;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Conjunto de recursos que el {@link GestorRecursos} entregó a una emergencia:
 * una ambulancia y, según la gravedad, médicos y equipos.
 *
 * La liberación es idempotente: aunque un bloque {@code finally} y la ruta
 * normal intenten liberar lo mismo, los recursos se devuelven una sola vez
 * (si no, el contador de médicos libres podría superar el total).
 */
public final class Reserva {

    private final Ambulancia ambulancia;
    private final int medicos;
    private final int equipos;
    private final AtomicBoolean personalLiberado = new AtomicBoolean();
    private final AtomicBoolean ambulanciaLiberada = new AtomicBoolean();

    Reserva(Ambulancia ambulancia, int medicos, int equipos) {
        this.ambulancia = ambulancia;
        this.medicos = medicos;
        this.equipos = equipos;
    }

    public Ambulancia ambulancia() {
        return ambulancia;
    }

    public int medicos() {
        return medicos;
    }

    public int equipos() {
        return equipos;
    }

    boolean marcarPersonalLiberado() {
        return personalLiberado.compareAndSet(false, true);
    }

    boolean marcarAmbulanciaLiberada() {
        return ambulanciaLiberada.compareAndSet(false, true);
    }
}
