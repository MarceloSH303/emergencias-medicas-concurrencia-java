package co.edu.iud.emergencias.hilos;

import co.edu.iud.emergencias.despacho.Asignacion;

import java.util.Map;
import java.util.Optional;

/**
 * Despachador: pide al planificador la siguiente asignación (bloqueándose si no hay nada que hacer) y
 * entrega la misión a la ambulancia elegida. Nunca ejecuta la misión: solo coordina, por eso es rápido.
 */
public final class Despachador implements Runnable {

    private final Entorno entorno;
    private final Map<String, HiloAmbulancia> ambulancias;

    public Despachador(Entorno entorno, Map<String, HiloAmbulancia> ambulancias) {
        this.entorno = entorno;
        this.ambulancias = ambulancias;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            Optional<Asignacion> a = entorno.planificador().siguienteAsignacion();
            if (a.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                try {
                    Thread.sleep(50); // planificador detenido: esperar a que se reanude
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }
            Asignacion asignacion = a.get();
            ambulancias.get(asignacion.reserva().ambulancia().id())
                    .recibir(new Mision(asignacion.emergencia(), asignacion.reserva()));
        }
    }
}
