package co.edu.iud.emergencias.modelo;

/**
 * Nivel de gravedad de una emergencia. Define cuánto pesa en la prioridad,
 * qué recursos necesita además de la ambulancia y cuánto dura la atención en
 * la escena (en milisegundos simulados).
 *
 * El orden de las constantes va de la más grave a la más leve; por eso
 * {@link #siguienteNivel()} usa {@code ordinal() - 1}.
 */
public enum Gravedad {
    CRITICO("Crítico", 100, 1, 1, 6_000),
    GRAVE("Grave", 60, 1, 0, 5_000),
    MODERADO("Moderado", 30, 0, 0, 3_500),
    LEVE("Leve", 10, 0, 0, 2_500);

    private final String etiqueta;
    private final int peso;
    private final int medicos;
    private final int equipos;
    private final long atencionMs;

    Gravedad(String etiqueta, int peso, int medicos, int equipos, long atencionMs) {
        this.etiqueta = etiqueta;
        this.peso = peso;
        this.medicos = medicos;
        this.equipos = equipos;
        this.atencionMs = atencionMs;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public int peso() {
        return peso;
    }

    public long atencionMs() {
        return atencionMs;
    }

    /** Recursos que exige esta gravedad: siempre una ambulancia, más médico y equipo según el caso. */
    public Requisitos requisitos() {
        return new Requisitos(1, medicos, equipos);
    }

    /** Un nivel más grave (el paciente empeora mientras espera). Crítico no sube más. */
    public Gravedad siguienteNivel() {
        return this == CRITICO ? this : values()[ordinal() - 1];
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
