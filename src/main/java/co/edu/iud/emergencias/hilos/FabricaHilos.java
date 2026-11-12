package co.edu.iud.emergencias.hilos;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Crea hilos daemon con nombre legible (ayuda a depurar y a leer un volcado de hilos). */
public final class FabricaHilos implements ThreadFactory {

    private final String prefijo;
    private final AtomicInteger contador = new AtomicInteger(1);

    public FabricaHilos(String prefijo) {
        this.prefijo = prefijo;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, prefijo + "-" + contador.getAndIncrement());
        t.setDaemon(true);
        return t;
    }
}
