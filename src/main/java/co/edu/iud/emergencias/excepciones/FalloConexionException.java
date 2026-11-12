package co.edu.iud.emergencias.excepciones;

/**
 * Se lanza cuando una ambulancia pierde el enlace de comunicación con la
 * central en medio de una misión.
 *
 * Es una excepción verificada a propósito: la caída de una conexión es una
 * situación esperada del entorno, no un error de programación, así que el
 * compilador obliga a decidir qué hacer (aquí: devolver la emergencia a la
 * cola para reasignarla).
 */
public class FalloConexionException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String recurso;

    public FalloConexionException(String recurso) {
        super("Se perdió el enlace con " + recurso);
        this.recurso = recurso;
    }

    public String recurso() {
        return recurso;
    }
}
