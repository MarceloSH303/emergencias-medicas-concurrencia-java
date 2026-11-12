package co.edu.iud.emergencias.util;

import java.time.Duration;

/**
 * Reloj de la simulación. Convierte tiempo real a tiempo simulado y viceversa.
 *
 * Con {@code escala = 1.0} un segundo simulado dura un segundo real; con
 * {@code 0.1} dura una décima. Todas las esperas de los hilos pasan por
 * {@link #dormirSim(long)}, así que cambiar la escala acelera todo el sistema
 * de forma coherente. La clase no es final para poder sustituirla por un reloj
 * manual en las pruebas.
 */
public class Reloj {

    private final long inicioNanos = System.nanoTime();
    private final double escala;

    public Reloj(double escala) {
        if (escala <= 0) {
            throw new IllegalArgumentException("La escala debe ser positiva.");
        }
        this.escala = escala;
    }

    public double escala() {
        return escala;
    }

    /** Milisegundos simulados transcurridos desde que se creó el reloj. */
    public long ahoraSimMs() {
        return Math.round((System.nanoTime() - inicioNanos) / 1_000_000.0 / escala);
    }

    /** Duerme el hilo actual el equivalente real de {@code simMs} milisegundos simulados. */
    public void dormirSim(long simMs) throws InterruptedException {
        if (simMs <= 0) {
            return;
        }
        long nanos = Math.max(100_000L, Math.round(simMs * escala * 1_000_000.0));
        Thread.sleep(Duration.ofNanos(nanos));
    }

    /** Milisegundos reales (mínimo 1) que equivalen a {@code simMs} simulados. */
    public long aRealMs(long simMs) {
        return Math.max(1L, Math.round(simMs * escala));
    }
}
