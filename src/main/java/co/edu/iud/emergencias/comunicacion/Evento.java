package co.edu.iud.emergencias.comunicacion;

import co.edu.iud.emergencias.modelo.Emergencia;
import co.edu.iud.emergencias.modelo.Gravedad;

/**
 * Mensaje inmutable que un subsistema publica en el bus. Al ser inmutable
 * puede pasar de un hilo a otro sin sincronización.
 *
 * @param simMs           instante simulado en que ocurrió
 * @param emergenciaId    emergencia involucrada, o -1 si es un evento del sistema
 * @param gravedad        gravedad en ese momento (puede ser mayor que la inicial)
 * @param gravedadInicial gravedad con la que se reportó la llamada
 * @param recurso         ambulancia, hospital u operador involucrado ("" si no aplica)
 * @param valorSimMs      medida asociada (espera, respuesta o duración); -1 si no aplica
 */
public record Evento(long simMs, TipoEvento tipo, int emergenciaId, Gravedad gravedad,
                     Gravedad gravedadInicial, String recurso, String detalle, long valorSimMs) {

    public static Evento de(long simMs, TipoEvento tipo, Emergencia e, String recurso,
                            String detalle, long valorSimMs) {
        return new Evento(simMs, tipo, e.id(), e.gravedad(), e.gravedadInicial(), recurso,
                detalle, valorSimMs);
    }

    public static Evento sistema(long simMs, TipoEvento tipo, String recurso, String detalle) {
        return new Evento(simMs, tipo, -1, null, null, recurso, detalle, -1);
    }

    /** Línea de texto para el registro de la interfaz y el log. */
    public String texto() {
        long segundos = Math.max(0, simMs) / 1000;
        String hora = String.format("%02d:%02d", segundos / 60, segundos % 60);
        String id = emergenciaId > 0 ? "#" + emergenciaId + " " : "";
        return String.format("%s  %-10s %s%s", hora, tipo.etiqueta(), id, detalle);
    }
}
