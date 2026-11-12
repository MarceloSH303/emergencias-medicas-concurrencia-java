package co.edu.iud.emergencias.hilos;

import co.edu.iud.emergencias.modelo.Ciudad;
import co.edu.iud.emergencias.modelo.Gravedad;
import co.edu.iud.emergencias.modelo.Ubicacion;
import co.edu.iud.emergencias.util.Reloj;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Productor: simula a la ciudad llamando. Los tiempos entre llamadas son exponenciales (proceso de
 * Poisson), el modelo estándar para llegadas independientes. Las llamadas van a una cola bloqueante de
 * la que consumen los operadores.
 */
public final class GeneradorEmergencias implements Runnable {

    private static final long TROZO_SIM_MS = 500;

    private final BlockingQueue<Llamada> llamadas;
    private final Reloj reloj;
    private volatile double llamadasPorMin;
    private volatile boolean activo = true;

    public GeneradorEmergencias(BlockingQueue<Llamada> llamadas, Reloj reloj, double llamadasPorMin) {
        this.llamadas = llamadas;
        this.reloj = reloj;
        this.llamadasPorMin = llamadasPorMin;
    }

    public void setLlamadasPorMin(double valor) {
        this.llamadasPorMin = Math.max(0.1, valor);
    }

    public double llamadasPorMin() {
        return llamadasPorMin;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /** Llamada creada a mano desde la interfaz. */
    public void llamadaManual(Gravedad g) {
        llamadas.offer(new Llamada(g, ubicacionAleatoria(), reloj.ahoraSimMs()));
    }

    @Override
    public void run() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                double media = 60_000.0 / llamadasPorMin;
                long espera = (long) (-Math.log(1 - rnd.nextDouble()) * media);
                // Se duerme por trozos para que un cambio en la tasa o una pausa surta efecto pronto.
                while (espera > 0) {
                    long paso = Math.min(espera, TROZO_SIM_MS);
                    reloj.dormirSim(paso);
                    espera -= paso;
                }
                if (activo) {
                    llamadas.offer(new Llamada(gravedadAleatoria(rnd.nextInt(100)), ubicacionAleatoria(),
                            reloj.ahoraSimMs()));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 10 % críticos, 25 % graves, 35 % moderados, 30 % leves. */
    static Gravedad gravedadAleatoria(int p) {
        if (p < 10) {
            return Gravedad.CRITICO;
        }
        if (p < 35) {
            return Gravedad.GRAVE;
        }
        if (p < 70) {
            return Gravedad.MODERADO;
        }
        return Gravedad.LEVE;
    }

    private static Ubicacion ubicacionAleatoria() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new Ubicacion(1 + r.nextDouble() * (Ciudad.LADO_KM - 2), 1 + r.nextDouble() * (Ciudad.LADO_KM - 2));
    }
}
