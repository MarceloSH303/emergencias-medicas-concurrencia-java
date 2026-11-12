package co.edu.iud.emergencias.despacho;

import co.edu.iud.emergencias.modelo.Emergencia;
import co.edu.iud.emergencias.recursos.Reserva;

/**
 * Resultado de una decisión del planificador: la emergencia, los recursos ya reservados para ella y si
 * se le concedieron saltándose a una emergencia más urgente que seguía bloqueada ("adelanto").
 */
public record Asignacion(Emergencia emergencia, Reserva reserva, boolean adelantada) {
}
