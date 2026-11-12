package co.edu.iud.emergencias.demo;

import co.edu.iud.emergencias.modelo.Ciudad;
import co.edu.iud.emergencias.modelo.Gravedad;
import co.edu.iud.emergencias.modelo.Ubicacion;
import co.edu.iud.emergencias.recursos.Ambulancia;
import co.edu.iud.emergencias.recursos.GestorRecursos;
import co.edu.iud.emergencias.recursos.Hospital;
import co.edu.iud.emergencias.recursos.Reserva;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Demostración del problema clásico y de su solución (para el video):
 *
 * 1. Versión ingensuch (NAIVE): dos despachadores toman la ambulancia y el médico en orden contrario,
 *    reteniendo uno mientras esperan el otro → deadlock. Se detecta con {@link ThreadMXBean}.
 * 2. Versión del proyecto: la reserva "todo o nada" de {@link GestorRecursos} bajo el mismo choque
 *    de intereses termina siempre.
 *
 * Ejecutar: {@code java -cp target/classes co.edu.iud.emergencias.demo.DeadlockDemo}
 */
public final class DeadlockDemo {

    private DeadlockDemo() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. Versión ingenua: bloqueos en orden contrario ===");
        versionIngenua();
        System.out.println();
        System.out.println("=== 2. Versión del proyecto: reserva atómica todo-o-nada ===");
        versionSegura();
    }

    private static void versionIngenua() throws InterruptedException {
        ReentrantLock ambulancia = new ReentrantLock();
        ReentrantLock medico = new ReentrantLock();
        CountDownLatch ambosTienenSuPrimero = new CountDownLatch(2);

        Thread d1 = new Thread(() -> tomarEnOrden("Despachador-1", ambulancia, "ambulancia", medico, "médico", ambosTienenSuPrimero),
                "Despachador-1");
        Thread d2 = new Thread(() -> tomarEnOrden("Despachador-2", medico, "médico", ambulancia, "ambulancia", ambosTienenSuPrimero),
                "Despachador-2");
        d1.start();
        d2.start();

        Thread.sleep(800);
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long[] ids = mx.findDeadlockedThreads();
        if (ids == null) {
            System.out.println("(no se detectó deadlock; el planificador del SO no entrelazó los hilos)");
        } else {
            System.out.println("DEADLOCK DETECTADO. Hilos bloqueados:");
            for (ThreadInfo info : mx.getThreadInfo(ids)) {
                System.out.println("  - " + info.getThreadName() + " espera '" + info.getLockName()
                        + "' que tiene " + info.getLockOwnerName());
            }
            System.out.println("Cada uno sostiene un recurso y espera el que tiene el otro: espera circular.");
        }
        d1.interrupt(); // se rompe a la fuerza solo para que la demo termine
        d2.interrupt();
        d1.join(1_000);
        d2.join(1_000);
    }

    private static void tomarEnOrden(String quien, ReentrantLock primero, String nombrePrimero,
                                     ReentrantLock segundo, String nombreSegundo, CountDownLatch sincronia) {
        try {
            primero.lockInterruptibly();
            System.out.println(quien + " tomó " + nombrePrimero);
            sincronia.countDown();
            sincronia.await(); // ambos ya tienen su primer recurso
            System.out.println(quien + " espera " + nombreSegundo + " ...");
            segundo.lockInterruptibly();
            System.out.println(quien + " tomó " + nombreSegundo + " (esto no se imprime con deadlock)");
            segundo.unlock();
        } catch (InterruptedException e) {
            System.out.println(quien + " fue interrumpido para terminar la demo");
        } finally {
            if (primero.isHeldByCurrentThread()) {
                primero.unlock();
            }
        }
    }

    private static void versionSegura() throws Exception {
        List<Ambulancia> l = new ArrayList<>();
        for (Ciudad.PuntoNombrado b : Ciudad.BASES.subList(0, 2)) {
            l.add(new Ambulancia(b.nombre(), b.ubicacion()));
        }
        GestorRecursos g = new GestorRecursos(l, List.of(new Hospital("H", new Ubicacion(10, 10), 1)), 1, 1);
        int[] hechas = new int[2];
        Thread[] hilos = new Thread[2];
        for (int t = 0; t < 2; t++) {
            int n = t;
            hilos[t] = new Thread(() -> {
                for (int i = 0; i < 20_000; i++) {
                    Optional<Reserva> r = g.intentarReservar(Gravedad.CRITICO.requisitos(), new Ubicacion(5, 5), null);
                    if (r.isPresent()) {
                        hechas[n]++;
                        g.liberarPersonal(r.get());
                        g.liberarAmbulancia(r.get());
                    }
                }
            }, "Despachador-" + (t + 1));
        }
        long t0 = System.nanoTime();
        for (Thread h : hilos) {
            h.start();
        }
        for (Thread h : hilos) {
            h.join(30_000);
        }
        boolean vivos = hilos[0].isAlive() || hilos[1].isAlive();
        System.out.printf("Dos hilos compitiendo por 1 médico + 1 equipo + ambulancia: %d y %d reservas en %d ms.%n",
                hechas[0], hechas[1], (System.nanoTime() - t0) / 1_000_000);
        System.out.println(vivos ? "¡Hilos colgados!" : "Terminó sin deadlock: nadie retiene un recurso mientras espera otro.");
    }
}
