package co.edu.iud.emergencias.modelo;

/**
 * Ciclo de vida de una emergencia:
 * PENDIENTE → DESPACHADA → EN_ATENCION → EN_TRASLADO → FINALIZADA.
 * Si falla el enlace de la ambulancia, una emergencia en curso vuelve a PENDIENTE.
 */
public enum EstadoEmergencia {
    PENDIENTE("Pendiente"),
    DESPACHADA("En camino"),
    EN_ATENCION("En atención"),
    EN_TRASLADO("En traslado"),
    FINALIZADA("Finalizada");

    private final String etiqueta;

    EstadoEmergencia(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Transiciones permitidas; cualquier otra es un error de programación. */
    public boolean puedePasarA(EstadoEmergencia nuevo) {
        return switch (this) {
            case PENDIENTE -> nuevo == DESPACHADA;
            case DESPACHADA -> nuevo == EN_ATENCION || nuevo == PENDIENTE;
            case EN_ATENCION -> nuevo == EN_TRASLADO || nuevo == PENDIENTE;
            case EN_TRASLADO -> nuevo == FINALIZADA || nuevo == PENDIENTE;
            case FINALIZADA -> false;
        };
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
