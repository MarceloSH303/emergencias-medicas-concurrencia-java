package co.edu.iud.emergencias.prioridad;

import co.edu.iud.emergencias.config.Configuracion;
import co.edu.iud.emergencias.modelo.Ciudad;
import co.edu.iud.emergencias.modelo.Emergencia;

/**
 * Algoritmo de priorización. Combina tres factores en un solo puntaje (mayor = más urgente):
 *
 * <pre>
 * puntaje = pesoGravedad  × peso(gravedad)
 *         + pesoEspera    × segundos de espera
 *         − pesoDistancia × km hasta la base más cercana
 *         + bonusReasignacion × veces que se perdió una ambulancia
 * </pre>
 *
 * <ul>
 *   <li><b>Gravedad</b>: domina el puntaje (crítico 100, grave 60, moderado 30, leve 10).</li>
 *   <li><b>Tiempo de espera</b> ("envejecimiento"): suma puntos con cada segundo, de modo que un caso
 *       leve que espera mucho termina superando a uno grave recién llegado y no sufre inanición.</li>
 *   <li><b>Distancia</b>: resta un poco para los casos lejanos. Atender primero lo cercano baja el
 *       tiempo medio de respuesta (idea de "el trabajo más corto primero"), y el envejecimiento
 *       evita que lo lejano espere para siempre.</li>
 *   <li><b>Reasignación</b>: quien ya perdió una ambulancia por un fallo pasa al frente.</li>
 * </ul>
 *
 * Es una función pura y sin estado, por lo que es segura para usar desde cualquier hilo.
 */
public final class CalculadorPrioridad {

    private final double pesoGravedad;
    private final double pesoEspera;
    private final double pesoDistancia;
    private final double bonusReasignacion;

    public CalculadorPrioridad(Configuracion config) {
        this.pesoGravedad = config.pesoGravedad();
        this.pesoEspera = config.pesoEspera();
        this.pesoDistancia = config.pesoDistancia();
        this.bonusReasignacion = config.bonusReasignacion();
    }

    public double puntaje(Emergencia e, long ahoraSimMs) {
        double esperaSeg = Math.max(0L, ahoraSimMs - e.llegadaSimMs()) / 1000.0;
        double distanciaKm = Ciudad.distanciaABaseMasCercana(e.ubicacion());
        return pesoGravedad * e.gravedad().peso()
                + pesoEspera * esperaSeg
                - pesoDistancia * distanciaKm
                + bonusReasignacion * e.reasignaciones();
    }
}
