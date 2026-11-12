package co.edu.iud.emergencias;

import co.edu.iud.emergencias.config.Configuracion;
import co.edu.iud.emergencias.monitoreo.Estadisticas;
import co.edu.iud.emergencias.recursos.EstadoRecurso;
import co.edu.iud.emergencias.recursos.Hospital;
import co.edu.iud.emergencias.sistema.SistemaEmergencias;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de integración: todos los hilos juntos, a 50x de velocidad, con fallos automáticos activos. */
class SistemaEmergenciasTest {

    @Test
    @DisplayName("Con carga alta y fallos, toda llamada se atiende y al final no se pierde ningún recurso")
    void sinFugasDeRecursos() {
        Configuracion cfg = Configuracion.porDefecto().modificar()
                .escalaTiempo(0.02)
                .llamadasPorMin(60)
                .intervaloFallosMs(15_000)
                .duracionFalloMs(4_000)
                .construir();
        assertTimeoutPreemptively(Duration.ofSeconds(90), () -> {
            try (SistemaEmergencias s = new SistemaEmergencias(cfg)) {
                s.iniciar();
                Thread.sleep(6_000); // 300 s simulados
                s.setGeneracion(false);
                assertTrue(s.esperarReposo(60_000), "el sistema debe quedar en reposo");

                Estadisticas.Resumen r = s.estadisticas().resumen();
                assertTrue(r.llamadas() > 50);
                assertEquals(r.llamadas(), r.finalizadas(), "cada llamada debe terminar atendida");
                assertEquals(s.config().medicos(), s.recursos().medicosLibres());
                assertEquals(s.config().equipos(), s.recursos().equiposLibres());
                assertTrue(r.fallos() > 0, "el inyector debió provocar algún fallo");
                for (var a : s.recursos().ambulancias()) {
                    assertTrue(a.estado() == EstadoRecurso.DISPONIBLE || a.estado() == EstadoRecurso.FUERA_DE_SERVICIO);
                }
                // Las altas de hospital son programadas: se espera a que devuelvan todas las camas
                long limite = System.nanoTime() + 10_000_000_000L;
                while (System.nanoTime() < limite && camasOcupadas(s) > 0) {
                    Thread.sleep(50);
                }
                assertEquals(0, camasOcupadas(s));
            }
        });
    }

    private static int camasOcupadas(SistemaEmergencias s) {
        int n = 0;
        for (Hospital h : s.recursos().hospitales()) {
            n += h.capacidad() - h.camasLibres();
        }
        return n;
    }
}
