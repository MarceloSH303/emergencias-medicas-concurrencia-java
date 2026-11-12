package co.edu.iud.emergencias.modelo;

/** Punto de la ciudad, en kilómetros sobre una cuadrícula. */
public record Ubicacion(double x, double y) {

    public double distanciaA(Ubicacion otra) {
        return Math.hypot(x - otra.x, y - otra.y);
    }

    /** Punto intermedio hacia {@code destino}; {@code t} va de 0 (aquí) a 1 (destino). */
    public Ubicacion interpolar(Ubicacion destino, double t) {
        return new Ubicacion(x + (destino.x - x) * t, y + (destino.y - y) * t);
    }
}
