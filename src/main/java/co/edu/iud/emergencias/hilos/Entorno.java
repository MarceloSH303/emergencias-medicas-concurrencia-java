package co.edu.iud.emergencias.hilos;

import co.edu.iud.emergencias.comunicacion.BusEventos;
import co.edu.iud.emergencias.config.Configuracion;
import co.edu.iud.emergencias.despacho.PlanificadorDespacho;
import co.edu.iud.emergencias.modelo.Emergencia;
import co.edu.iud.emergencias.recursos.GestorRecursos;
import co.edu.iud.emergencias.util.Reloj;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/** Dependencias compartidas por todos los hilos de la simulación (se pasan por constructor, sin estáticos). */
public record Entorno(Configuracion config, Reloj reloj, BusEventos bus, GestorRecursos recursos,
                      PlanificadorDespacho planificador, ScheduledExecutorService programador,
                      Map<Integer, Emergencia> activas) {
}
