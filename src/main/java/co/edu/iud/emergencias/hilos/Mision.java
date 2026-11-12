package co.edu.iud.emergencias.hilos;

import co.edu.iud.emergencias.modelo.Emergencia;
import co.edu.iud.emergencias.recursos.Reserva;

/** Mensaje que el despachador envía al buzón de una ambulancia (modelo de actores: se comunica por mensajes). */
public record Mision(Emergencia emergencia, Reserva reserva) {
}
