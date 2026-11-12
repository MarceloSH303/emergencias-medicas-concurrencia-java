package co.edu.iud.emergencias.vista;

import co.edu.iud.emergencias.modelo.Gravedad;
import co.edu.iud.emergencias.recursos.EstadoRecurso;

import java.awt.Color;

/** Paleta compartida por el mapa y las tablas. Cada color también va acompañado de texto, no solo de color. */
final class Colores {

    static final Color CRITICO = new Color(198, 40, 40);
    static final Color GRAVE = new Color(230, 108, 20);
    static final Color MODERADO = new Color(196, 154, 0);
    static final Color LEVE = new Color(46, 125, 50);

    static final Color DISPONIBLE = new Color(46, 125, 50);
    static final Color EN_CAMINO = new Color(21, 101, 192);
    static final Color OCUPADA = new Color(106, 27, 154);
    static final Color SIN_ENLACE = new Color(117, 117, 117);

    private Colores() {
    }

    static Color de(Gravedad g) {
        return switch (g) {
            case CRITICO -> CRITICO;
            case GRAVE -> GRAVE;
            case MODERADO -> MODERADO;
            case LEVE -> LEVE;
        };
    }

    static Color de(EstadoRecurso e) {
        return switch (e) {
            case DISPONIBLE -> DISPONIBLE;
            case EN_CAMINO -> EN_CAMINO;
            case OCUPADA -> OCUPADA;
            case FUERA_DE_SERVICIO -> SIN_ENLACE;
        };
    }
}
