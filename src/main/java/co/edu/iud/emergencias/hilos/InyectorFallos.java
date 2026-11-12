package co.edu.iud.emergencias.hilos;

import co.edu.iud.emergencias.comunicacion.Evento;
import co.edu.iud.emergencias.comunicacion.TipoEvento;
import co.edu.iud.emergencias.recursos.Ambulancia;
import co.edu.iud.emergencias.recursos.Hospital;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Simula caídas temporales de comunicación (la "tolerancia a fallos" del caso de estudio): una
 * ambulancia pierde el enlace con la central o un hospital deja de responder. Pasado un tiempo, el
 * recurso se recupera solo. También lo usa la interfaz para provocar fallos a mano.
 */
public final class InyectorFallos implements Runnable {

    private final Entorno entorno;
    private volatile boolean automatico;

    public InyectorFallos(Entorno entorno) {
        this.entorno = entorno;
        this.automatico = entorno.config().fallosAutomaticos();
    }

    public void setAutomatico(boolean automatico) {
        this.automatico = automatico;
    }

    public boolean automatico() {
        return automatico;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                long intervalo = (long) (entorno.config().intervaloFallosMs() * (0.5 + r.nextDouble()));
                entorno.reloj().dormirSim(intervalo);
                if (!automatico) {
                    continue;
                }
                if (r.nextInt(100) < 75) {
                    List<Ambulancia> l = entorno.recursos().ambulancias();
                    fallarAmbulancia(l.get(r.nextInt(l.size())));
                } else {
                    List<Hospital> l = entorno.recursos().hospitales();
                    fallarHospital(l.get(r.nextInt(l.size())));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean fallarAmbulancia(Ambulancia a) {
        if (!entorno.recursos().caerEnlace(a)) {
            return false;
        }
        entorno.bus().publicar(Evento.sistema(entorno.reloj().ahoraSimMs(), TipoEvento.FALLO, a.id(),
                "Se perdió el enlace con " + a.id()));
        entorno.programador().schedule(() -> {
            entorno.recursos().restaurarEnlace(a);
            entorno.bus().publicar(Evento.sistema(entorno.reloj().ahoraSimMs(), TipoEvento.RECUPERADO, a.id(),
                    "Enlace restablecido con " + a.id()));
        }, entorno.reloj().aRealMs(entorno.config().duracionFalloMs()), TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean fallarHospital(Hospital h) {
        if (!h.enLinea()) {
            return false;
        }
        h.setEnLinea(false);
        entorno.bus().publicar(Evento.sistema(entorno.reloj().ahoraSimMs(), TipoEvento.FALLO, h.nombre(),
                h.nombre() + " no responde"));
        entorno.programador().schedule(() -> {
            h.setEnLinea(true);
            entorno.planificador().notificarCambio();
            entorno.bus().publicar(Evento.sistema(entorno.reloj().ahoraSimMs(), TipoEvento.RECUPERADO, h.nombre(),
                    h.nombre() + " vuelve a responder"));
        }, entorno.reloj().aRealMs(entorno.config().duracionFalloMs()), TimeUnit.MILLISECONDS);
        return true;
    }
}
