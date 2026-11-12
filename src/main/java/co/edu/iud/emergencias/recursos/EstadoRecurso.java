package co.edu.iud.emergencias.recursos;

/** Estado de una ambulancia: disponible, en camino a la escena, ocupada o sin enlace con la central. */
public enum EstadoRecurso {
    DISPONIBLE("Disponible"),
    EN_CAMINO("En camino"),
    OCUPADA("Ocupada"),
    FUERA_DE_SERVICIO("Sin enlace");

    private final String etiqueta;

    EstadoRecurso(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
