package co.edu.iud.emergencias.comunicacion;

/** Tipos de mensaje que circulan por el bus de eventos entre los subsistemas. */
public enum TipoEvento {
    LLAMADA_RECIBIDA("Llamada"),
    EN_COLA("En cola"),
    PRIORIDAD_MODIFICADA("Prioridad"),
    DESPACHADA("Despacho"),
    ADELANTO("Adelanto"),
    EN_ESCENA("En escena"),
    EN_TRASLADO("Traslado"),
    HOSPITAL_SATURADO("Hospital"),
    FINALIZADA("Finalizada"),
    FALLO("Fallo"),
    REASIGNADA("Reasignada"),
    RECUPERADO("Recuperado");

    private final String etiqueta;

    TipoEvento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
