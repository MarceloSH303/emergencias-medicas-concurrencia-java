package co.edu.iud.emergencias.hilos;

import co.edu.iud.emergencias.modelo.Gravedad;
import co.edu.iud.emergencias.modelo.Ubicacion;

/** Llamada entrante: aún no es una emergencia registrada hasta que un operador la atiende. */
public record Llamada(Gravedad gravedad, Ubicacion ubicacion, long recibidaSimMs) {
}
