package co.edu.iud.emergencias;

import co.edu.iud.emergencias.config.Configuracion;
import co.edu.iud.emergencias.modelo.Emergencia;
import co.edu.iud.emergencias.modelo.Gravedad;
import co.edu.iud.emergencias.modelo.Ubicacion;
import co.edu.iud.emergencias.prioridad.CalculadorPrioridad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadorPrioridadTest {

    private final CalculadorPrioridad calc = new CalculadorPrioridad(Configuracion.porDefecto());
    private static final Ubicacion BASE_A1 = new Ubicacion(4, 4);

    private static Emergencia em(int id, Gravedad g, Ubicacion u, long llegada) {
        return new Emergencia(id, u, g, llegada);
    }

    @Test
    @DisplayName("A igual espera y distancia, más gravedad = más prioridad")
    void gravedadOrdena() {
        double critico = calc.puntaje(em(1, Gravedad.CRITICO, BASE_A1, 0), 0);
        double grave = calc.puntaje(em(2, Gravedad.GRAVE, BASE_A1, 0), 0);
        double moderado = calc.puntaje(em(3, Gravedad.MODERADO, BASE_A1, 0), 0);
        double leve = calc.puntaje(em(4, Gravedad.LEVE, BASE_A1, 0), 0);
        assertTrue(critico > grave && grave > moderado && moderado > leve);
    }

    @Test
    @DisplayName("Esperar sube el puntaje (envejecimiento): evita la inanición")
    void esperaSuma() {
        Emergencia leve = em(1, Gravedad.LEVE, BASE_A1, 0);
        assertTrue(calc.puntaje(leve, 60_000) > calc.puntaje(leve, 0));
        // Un leve que espera 60 s supera a un grave recién llegado
        Emergencia grave = em(2, Gravedad.GRAVE, BASE_A1, 60_000);
        assertTrue(calc.puntaje(leve, 60_000) > calc.puntaje(grave, 60_000));
    }

    @Test
    @DisplayName("Más lejos de las bases = menos prioridad (a igualdad de lo demás)")
    void distanciaResta() {
        double cerca = calc.puntaje(em(1, Gravedad.MODERADO, BASE_A1, 0), 0);
        double lejos = calc.puntaje(em(2, Gravedad.MODERADO, new Ubicacion(19, 1), 0), 0);
        assertTrue(cerca > lejos);
    }

    @Test
    @DisplayName("Quien perdió una ambulancia por un fallo recibe un bono")
    void reasignacionSuma() {
        Emergencia e = em(1, Gravedad.MODERADO, BASE_A1, 0);
        double antes = calc.puntaje(e, 0);
        e.registrarReasignacion();
        assertEquals(antes + Configuracion.porDefecto().bonusReasignacion(), calc.puntaje(e, 0), 1e-9);
    }

    @Test
    @DisplayName("La política FIFO ignora la gravedad: solo cuenta quién esperó más")
    void fifoIgnoraGravedad() {
        CalculadorPrioridad fifo = new CalculadorPrioridad(Configuracion.porDefecto().modificar().politicaFifo().construir());
        Emergencia leveAntiguo = em(1, Gravedad.LEVE, BASE_A1, 0);
        Emergencia criticoNuevo = em(2, Gravedad.CRITICO, BASE_A1, 10_000);
        assertTrue(fifo.puntaje(leveAntiguo, 10_000) > fifo.puntaje(criticoNuevo, 10_000));
    }
}
