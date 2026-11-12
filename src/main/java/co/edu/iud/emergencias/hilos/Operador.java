package co.edu.iud.emergencias.hilos;

import co.edu.iud.emergencias.comunicacion.Evento;
import co.edu.iud.emergencias.comunicacion.TipoEvento;
import co.edu.iud.emergencias.modelo.Emergencia;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Operador del centro de llamadas (consumidor de la cola de llamadas). Atiende una llamada a la vez,
 * tarda un tiempo en tomar los datos, registra la emergencia y la entrega al planificador. Con pocos
 * operadores y muchas llamadas, las llamadas esperan aquí: es un cuello de botella real y medible.
 */
public final class Operador implements Runnable {

    private final String nombre;
    private final BlockingQueue<Llamada> llamadas;
    private final AtomicInteger secuencia;
    private final Entorno entorno;

    public Operador(String nombre, BlockingQueue<Llamada> llamadas, AtomicInteger secuencia, Entorno entorno) {
        this.nombre = nombre;
        this.llamadas = llamadas;
        this.secuencia = secuencia;
        this.entorno = entorno;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Llamada llamada = llamadas.take();
                entorno.reloj().dormirSim(ThreadLocalRandom.current().nextLong(1_000, 3_001));
                Emergencia e = new Emergencia(secuencia.incrementAndGet(), llamada.ubicacion(),
                        llamada.gravedad(), llamada.recibidaSimMs());
                entorno.activas().put(e.id(), e);
                entorno.bus().publicar(Evento.de(entorno.reloj().ahoraSimMs(), TipoEvento.LLAMADA_RECIBIDA, e,
                        nombre, "Atendida por " + nombre + ": " + e.gravedad(), -1));
                entorno.planificador().encolar(e);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
