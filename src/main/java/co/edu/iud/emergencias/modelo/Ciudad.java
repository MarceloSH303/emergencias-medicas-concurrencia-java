package co.edu.iud.emergencias.modelo;

import java.util.List;

/** Mapa fijo de la ciudad simulada: una cuadrícula de 20 × 20 km con bases y hospitales. */
public final class Ciudad {

    public static final double LADO_KM = 20.0;

    public record PuntoNombrado(String nombre, Ubicacion ubicacion) {
    }

    /** Bases donde esperan las ambulancias. */
    public static final List<PuntoNombrado> BASES = List.of(
            new PuntoNombrado("A1", new Ubicacion(4, 4)),
            new PuntoNombrado("A2", new Ubicacion(16, 4)),
            new PuntoNombrado("A3", new Ubicacion(10, 8)),
            new PuntoNombrado("A4", new Ubicacion(4, 16)),
            new PuntoNombrado("A5", new Ubicacion(16, 16)),
            new PuntoNombrado("A6", new Ubicacion(10, 12)));

    public static final List<PuntoNombrado> HOSPITALES = List.of(
            new PuntoNombrado("Hospital Sur", new Ubicacion(10, 3)),
            new PuntoNombrado("Hospital Central", new Ubicacion(10, 10)),
            new PuntoNombrado("Hospital Norte", new Ubicacion(10, 17)));

    private Ciudad() {
        // Clase de constantes: no se instancia.
    }

    /** Distancia desde un punto hasta la base de ambulancias más cercana. */
    public static double distanciaABaseMasCercana(Ubicacion u) {
        double minima = Double.MAX_VALUE;
        for (PuntoNombrado base : BASES) {
            minima = Math.min(minima, u.distanciaA(base.ubicacion()));
        }
        return minima;
    }
}
