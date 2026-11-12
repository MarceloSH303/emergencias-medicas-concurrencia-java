package co.edu.iud.emergencias.hilos;

import co.edu.iud.emergencias.comunicacion.Evento;
import co.edu.iud.emergencias.comunicacion.TipoEvento;
import co.edu.iud.emergencias.excepciones.FalloConexionException;
import co.edu.iud.emergencias.modelo.Emergencia;
import co.edu.iud.emergencias.modelo.EstadoEmergencia;
import co.edu.iud.emergencias.modelo.Ubicacion;
import co.edu.iud.emergencias.recursos.Ambulancia;
import co.edu.iud.emergencias.recursos.Hospital;
import co.edu.iud.emergencias.recursos.Reserva;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hilo de una ambulancia (modelo de actor): vive esperando misiones en su buzón y las ejecuta una a una.
 * Solo este hilo escribe la posición y el estado de su misión, así que no necesita cerrojos propios.
 *
 * Tolerancia a fallos: si durante el viaje se pierde el enlace, se lanza {@link FalloConexionException}.
 * La misión se cancela, la emergencia vuelve a la cola (conservando su hora de llegada y con bonus de
 * prioridad) y el bloque {@code finally} devuelve los recursos pase lo que pase.
 */
public final class HiloAmbulancia implements Runnable {

    private static final Logger LOG = Logger.getLogger(HiloAmbulancia.class.getName());
    private static final long PASO_SIM_MS = 200;

    private final Ambulancia ambulancia;
    private final Entorno entorno;
    private final BlockingQueue<Mision> buzon = new LinkedBlockingQueue<>();

    public HiloAmbulancia(Ambulancia ambulancia, Entorno entorno) {
        this.ambulancia = ambulancia;
        this.entorno = entorno;
    }

    public void recibir(Mision m) {
        buzon.offer(m);
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ejecutar(buzon.take());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void ejecutar(Mision mision) throws InterruptedException {
        Emergencia e = mision.emergencia();
        Reserva reserva = mision.reserva();
        Hospital conCama = null;
        try {
            ambulancia.iniciarMision(e.id(), entorno.reloj().ahoraSimMs());

            viajar(ambulancia.base(), e.ubicacion(), true);
            entorno.recursos().marcarOcupada(ambulancia);
            e.transicionar(EstadoEmergencia.EN_ATENCION);
            long ahora = entorno.reloj().ahoraSimMs();
            boolean primera = e.marcarEscena(ahora);
            publicar(TipoEvento.EN_ESCENA, e, "Llega " + ambulancia.id() + " a la escena",
                    primera ? e.respuestaSimMs() : -1);

            entorno.reloj().dormirSim(e.gravedad().atencionMs());

            conCama = conseguirCama(e);
            e.transicionar(EstadoEmergencia.EN_TRASLADO);
            publicar(TipoEvento.EN_TRASLADO, e, "Traslado a " + conCama.nombre(), -1);
            viajar(e.ubicacion(), conCama.ubicacion(), true);

            e.marcarFin(entorno.reloj().ahoraSimMs());
            e.transicionar(EstadoEmergencia.FINALIZADA);
            publicar(TipoEvento.FINALIZADA, e, "Ingresa a " + conCama.nombre(), e.duracionTotalSimMs());
            Hospital h = conCama;
            conCama = null; // la cama se libera por el alta programada
            entorno.programador().schedule(h::darAlta,
                    entorno.reloj().aRealMs(entorno.config().estadiaHospitalMs()), TimeUnit.MILLISECONDS);
            entorno.activas().remove(e.id());

            entorno.recursos().liberarPersonal(reserva);
            viajar(h.ubicacion(), ambulancia.base(), false);
        } catch (FalloConexionException ex) {
            if (conCama != null) {
                conCama.darAlta();
                conCama = null;
            }
            publicar(TipoEvento.FALLO, e, ex.getMessage() + ": misión cancelada", -1);
            reencolar(e, "Vuelve a la cola tras perder a " + ambulancia.id());
        } catch (InterruptedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "Error inesperado en la misión de la emergencia #" + e.id(), ex);
            if (e.estado() != EstadoEmergencia.FINALIZADA && e.estado().puedePasarA(EstadoEmergencia.PENDIENTE)) {
                reencolar(e, "Vuelve a la cola por un error interno");
            }
        } finally {
            if (conCama != null) {
                conCama.darAlta();
            }
            ambulancia.finalizarMision(entorno.reloj().ahoraSimMs());
            entorno.recursos().liberarPersonal(reserva);
            entorno.recursos().liberarAmbulancia(reserva);
        }
    }

    private void reencolar(Emergencia e, String detalle) {
        e.registrarReasignacion();
        e.transicionar(EstadoEmergencia.PENDIENTE);
        publicar(TipoEvento.REASIGNADA, e, detalle, -1);
        entorno.planificador().reencolar(e);
    }

    /**
     * Busca cama en el hospital más cercano que responda. Si todos están llenos o caídos, espera y
     * reintenta (sin sostener ningún cerrojo) hasta que alguno libere una cama.
     */
    private Hospital conseguirCama(Emergencia e) throws InterruptedException, FalloConexionException {
        boolean avisado = false;
        while (true) {
            comprobarEnlace();
            for (Hospital h : entorno.recursos().hospitalesPorDistancia(e.ubicacion())) {
                if (h.intentarAdmitir()) {
                    return h;
                }
            }
            if (!avisado) {
                publicar(TipoEvento.HOSPITAL_SATURADO, e, "Sin camas disponibles: " + ambulancia.id() + " espera", -1);
                avisado = true;
            }
            entorno.reloj().dormirSim(1_000);
        }
    }

    private void comprobarEnlace() throws FalloConexionException {
        if (!ambulancia.enlaceActivo()) {
            throw new FalloConexionException(ambulancia.id());
        }
    }

    /** Avanza en pasos cortos; en cada uno comprueba el enlace, así el fallo se detecta a mitad de camino. */
    private void viajar(Ubicacion desde, Ubicacion hasta, boolean verificarEnlace)
            throws InterruptedException, FalloConexionException {
        long total = Math.max(1, (long) (desde.distanciaA(hasta) * entorno.config().msPorKm()));
        long recorrido = 0;
        while (recorrido < total) {
            if (verificarEnlace) {
                comprobarEnlace();
            }
            long paso = Math.min(PASO_SIM_MS, total - recorrido);
            entorno.reloj().dormirSim(paso);
            recorrido += paso;
            ambulancia.setPosicion(desde.interpolar(hasta, recorrido / (double) total));
        }
        ambulancia.setPosicion(hasta);
    }

    private void publicar(TipoEvento tipo, Emergencia e, String detalle, long valor) {
        entorno.bus().publicar(Evento.de(entorno.reloj().ahoraSimMs(), tipo, e, ambulancia.id(), detalle, valor));
    }

    public List<Mision> pendientesEnBuzon() {
        return List.copyOf(buzon);
    }
}
