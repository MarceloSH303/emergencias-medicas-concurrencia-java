package co.edu.iud.emergencias.comunicacion;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bus de eventos (patrón publicador/suscriptor, o Observer asíncrono).
 *
 * Los subsistemas (operadores, despachadores, ambulancias) publican eventos
 * sin conocer quién los consume; el monitor, el registro y la interfaz se
 * suscriben. Un único hilo dedicado entrega los eventos, en el mismo orden en
 * que se publicaron, de modo que:
 *  - publicar nunca bloquea al productor (es un {@code offer} en una cola sin límite);
 *  - un suscriptor lento o con errores no frena a las ambulancias ni al despacho;
 *  - los suscriptores no necesitan sincronizarse entre sí.
 */
public final class BusEventos implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(BusEventos.class.getName());
    private static final Evento FIN = Evento.sistema(0, TipoEvento.RECUPERADO, "", "fin");

    private final BlockingQueue<Evento> cola = new LinkedBlockingQueue<>();
    private final List<Consumer<Evento>> suscriptores = new CopyOnWriteArrayList<>();
    private final AtomicLong publicados = new AtomicLong();
    private final AtomicLong entregados = new AtomicLong();
    private final Thread hilo;

    public BusEventos() {
        hilo = new Thread(this::bucle, "bus-eventos");
        hilo.setDaemon(true);
        hilo.start();
    }

    public void suscribir(Consumer<Evento> suscriptor) {
        suscriptores.add(suscriptor);
    }

    public void publicar(Evento evento) {
        publicados.incrementAndGet();
        cola.offer(evento);
    }

    /** Espera a que todo lo publicado hasta ahora haya sido entregado. Sirve para leer estadísticas exactas. */
    public boolean esperarEntrega(long timeoutRealMs) throws InterruptedException {
        long limite = System.nanoTime() + timeoutRealMs * 1_000_000L;
        while (entregados.get() < publicados.get()) {
            if (System.nanoTime() > limite) {
                return false;
            }
            Thread.sleep(2);
        }
        return true;
    }

    private void bucle() {
        try {
            while (true) {
                Evento evento = cola.take();
                if (evento == FIN) {
                    return;
                }
                for (Consumer<Evento> s : suscriptores) {
                    try {
                        s.accept(evento);
                    } catch (RuntimeException e) {
                        // Un suscriptor defectuoso no debe tumbar el bus ni a los demás.
                        LOG.log(Level.WARNING, "Un suscriptor del bus lanzó una excepción", e);
                    }
                }
                entregados.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        cola.offer(FIN);
        try {
            hilo.join(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
