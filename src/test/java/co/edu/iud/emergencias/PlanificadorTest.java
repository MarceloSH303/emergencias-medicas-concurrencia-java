package co.edu.iud.emergencias;

import co.edu.iud.emergencias.comunicacion.BusEventos;
import co.edu.iud.emergencias.config.Configuracion;
import co.edu.iud.emergencias.despacho.Asignacion;
import co.edu.iud.emergencias.despacho.PlanificadorDespacho;
import co.edu.iud.emergencias.modelo.Emergencia;
import co.edu.iud.emergencias.modelo.EstadoEmergencia;
import co.edu.iud.emergencias.modelo.Gravedad;
import co.edu.iud.emergencias.modelo.Ubicacion;
import co.edu.iud.emergencias.recursos.GestorRecursos;
import co.edu.iud.emergencias.util.Reloj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanificadorTest {

    /** Reloj controlado por la prueba: el tiempo solo avanza cuando se le ordena. */
    static final class RelojManual extends Reloj {
        private volatile long ahora;

        RelojManual() {
            super(1.0);
        }

        void avanzar(long ms) {
            ahora += ms;
        }

        @Override
        public long ahoraSimMs() {
            return ahora;
        }
    }

    private static final Ubicacion AQUI = new Ubicacion(5, 5);

    private final RelojManual reloj = new RelojManual();
    private final BusEventos bus = new BusEventos();

    private PlanificadorDespacho planificador(GestorRecursos g) {
        PlanificadorDespacho p = new PlanificadorDespacho(Configuracion.porDefecto(), g, bus, reloj);
        g.setAlCambiar(p::notificarCambio);
        return p;
    }

    @Test
    @DisplayName("Entre dos llamadas simultáneas se atiende primero la más grave")
    void masGraveprimero() {
        PlanificadorDespacho p = planificador(GestorRecursosTest.gestor(1, 1, 1));
        p.encolar(new Emergencia(1, AQUI, Gravedad.LEVE, 0));
        p.encolar(new Emergencia(2, AQUI, Gravedad.CRITICO, 0));
        Asignacion a = p.siguienteAsignacion().orElseThrow();
        assertEquals(2, a.emergencia().id());
        assertEquals(EstadoEmergencia.DESPACHADA, a.emergencia().estado());
    }

    @Test
    @DisplayName("Un caso leve que espera mucho acaba pasando delante de uno grave nuevo")
    void envejecimientoEvitaInanicion() {
        PlanificadorDespacho p = planificador(GestorRecursosTest.gestor(1, 1, 1));
        p.encolar(new Emergencia(1, AQUI, Gravedad.LEVE, 0));
        reloj.avanzar(200_000);
        p.encolar(new Emergencia(2, AQUI, Gravedad.GRAVE, 200_000));
        assertEquals(1, p.siguienteAsignacion().orElseThrow().emergencia().id());
    }

    @Test
    @DisplayName("La gravedad escala si el paciente espera demasiado")
    void escalamiento() {
        PlanificadorDespacho p = planificador(GestorRecursosTest.gestor(1, 1, 1));
        Emergencia leve = new Emergencia(1, AQUI, Gravedad.LEVE, 0);
        p.encolar(leve);
        reloj.avanzar(50_000); // > 45 s
        p.siguienteAsignacion().orElseThrow();
        assertEquals(Gravedad.MODERADO, leve.gravedad());
        assertEquals(Gravedad.LEVE, leve.gravedadInicial());
    }

    @Test
    @DisplayName("Backfill: con la cabeza bloqueada por falta de equipos, una leve usa la ambulancia ociosa")
    void adelanto() {
        PlanificadorDespacho p = planificador(GestorRecursosTest.gestor(2, 1, 0));
        p.encolar(new Emergencia(1, AQUI, Gravedad.CRITICO, 0));
        p.encolar(new Emergencia(2, AQUI, Gravedad.LEVE, 0));
        Asignacion a = p.siguienteAsignacion().orElseThrow();
        assertEquals(2, a.emergencia().id());
        assertTrue(a.adelantada());
    }

    @Test
    @DisplayName("Backfill: si atender a la leve retrasaría al crítico, se espera (no se roba el recurso)")
    void noRobaALaCabeza() throws Exception {
        PlanificadorDespacho p = planificador(GestorRecursosTest.gestor(1, 1, 0));
        p.encolar(new Emergencia(1, AQUI, Gravedad.CRITICO, 0));
        p.encolar(new Emergencia(2, AQUI, Gravedad.LEVE, 0));
        CompletableFuture<Optional<Asignacion>> f = CompletableFuture.supplyAsync(p::siguienteAsignacion);
        assertThrows(TimeoutException.class, () -> f.get(600, TimeUnit.MILLISECONDS));
        assertEquals(2, p.pendientes());
        p.detener();
        assertTrue(f.get(2, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    @DisplayName("Un despachador bloqueado se despierta cuando entra una emergencia")
    void despiertaAlEncolar() throws ExecutionException, InterruptedException, TimeoutException {
        PlanificadorDespacho p = planificador(GestorRecursosTest.gestor(1, 1, 1));
        CompletableFuture<Optional<Asignacion>> f = CompletableFuture.supplyAsync(p::siguienteAsignacion);
        Thread.sleep(150);
        assertFalse(f.isDone());
        p.encolar(new Emergencia(7, AQUI, Gravedad.MODERADO, 0));
        assertEquals(7, f.get(2, TimeUnit.SECONDS).orElseThrow().emergencia().id());
    }
}
