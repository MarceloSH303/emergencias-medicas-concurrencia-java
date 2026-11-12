package co.edu.iud.emergencias.monitoreo;

import co.edu.iud.emergencias.modelo.EstadoEmergencia;
import co.edu.iud.emergencias.modelo.Gravedad;
import co.edu.iud.emergencias.recursos.EstadoRecurso;

import java.util.List;

/**
 * Foto inmutable del sistema en un instante. Los hilos de trabajo nunca tocan la interfaz: el monitor
 * (Swing Timer) pide una instantánea y dibuja a partir de ella, sin compartir estado mutable.
 */
public record Instantanea(long simMs, List<FilaEmergencia> emergencias, List<FilaAmbulancia> ambulancias,
                          List<FilaHospital> hospitales, int pendientes, int medicosLibres, int totalMedicos,
                          int equiposLibres, int totalEquipos, boolean enMarcha,
                          Estadisticas.Resumen resumen) {

    public record FilaEmergencia(int id, Gravedad gravedad, Gravedad gravedadInicial, EstadoEmergencia estado,
                                 double prioridad, double esperaSeg, String ambulancia, double x, double y) {
    }

    public record FilaAmbulancia(String id, EstadoRecurso estado, boolean enlace, double x, double y,
                                 int emergenciaId, int misiones, double utilizacion) {
    }

    public record FilaHospital(String nombre, int camasLibres, int capacidad, boolean enLinea,
                               double x, double y) {
    }
}
