package co.edu.iud.emergencias.monitoreo;

import co.edu.iud.emergencias.comunicacion.Evento;
import co.edu.iud.emergencias.modelo.Gravedad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Estadísticas en tiempo real. Se suscribe al bus de eventos y acumula con contadores sin cerrojo
 * ({@link LongAdder}), de modo que el hilo del bus nunca espera y la interfaz puede leer cuando quiera.
 * Las medias se agrupan por la gravedad INICIAL de la llamada: es la forma justa de ver si un caso que
 * empezó leve y escaló fue bien atendido.
 */
public final class Estadisticas implements Consumer<Evento> {

    /** Suma, conteo y máximo de una medida (en ms simulados). */
    private static final class Acumulador {
        final LongAdder n = new LongAdder();
        final LongAdder suma = new LongAdder();
        final AtomicLong max = new AtomicLong();

        void agregar(long v) {
            n.increment();
            suma.add(v);
            max.accumulateAndGet(v, Math::max);
        }

        double mediaSeg() {
            long c = n.sum();
            return c == 0 ? 0 : suma.sum() / (double) c / 1000.0;
        }
    }

    public record Resumen(long llamadas, long despachos, long finalizadas, long fallos, long reasignadas,
                          long adelantos, long escaladas, long saturaciones,
                          double esperaMediaSeg, double esperaMaxSeg, double respuestaMediaSeg,
                          double respuestaP95Seg, double duracionMediaSeg,
                          Map<Gravedad, Double> esperaPorGravedadSeg,
                          Map<Gravedad, Double> respuestaPorGravedadSeg) {
    }

    private final LongAdder llamadas = new LongAdder();
    private final LongAdder despachos = new LongAdder();
    private final LongAdder finalizadas = new LongAdder();
    private final LongAdder fallos = new LongAdder();
    private final LongAdder reasignadas = new LongAdder();
    private final LongAdder adelantos = new LongAdder();
    private final LongAdder escaladas = new LongAdder();
    private final LongAdder saturaciones = new LongAdder();

    private final Acumulador espera = new Acumulador();
    private final Acumulador respuesta = new Acumulador();
    private final Acumulador duracion = new Acumulador();
    private final Map<Gravedad, Acumulador> esperaPorG = new EnumMap<>(Gravedad.class);
    private final Map<Gravedad, Acumulador> respuestaPorG = new EnumMap<>(Gravedad.class);
    private final ConcurrentLinkedQueue<Long> respuestas = new ConcurrentLinkedQueue<>();

    public Estadisticas() {
        for (Gravedad g : Gravedad.values()) {
            esperaPorG.put(g, new Acumulador());
            respuestaPorG.put(g, new Acumulador());
        }
    }

    @Override
    public void accept(Evento e) {
        switch (e.tipo()) {
            case LLAMADA_RECIBIDA -> llamadas.increment();
            case DESPACHADA -> {
                despachos.increment();
                if (e.valorSimMs() >= 0) {
                    espera.agregar(e.valorSimMs());
                    esperaPorG.get(e.gravedadInicial()).agregar(e.valorSimMs());
                }
            }
            case EN_ESCENA -> {
                if (e.valorSimMs() >= 0) {
                    respuesta.agregar(e.valorSimMs());
                    respuestaPorG.get(e.gravedadInicial()).agregar(e.valorSimMs());
                    respuestas.add(e.valorSimMs());
                }
            }
            case FINALIZADA -> {
                finalizadas.increment();
                if (e.valorSimMs() >= 0) {
                    duracion.agregar(e.valorSimMs());
                }
            }
            case FALLO -> fallos.increment();
            case REASIGNADA -> reasignadas.increment();
            case ADELANTO -> adelantos.increment();
            case PRIORIDAD_MODIFICADA -> escaladas.increment();
            case HOSPITAL_SATURADO -> saturaciones.increment();
            default -> {
            }
        }
    }

    public Resumen resumen() {
        List<Long> copia = new ArrayList<>(respuestas);
        Collections.sort(copia);
        double p95 = copia.isEmpty() ? 0 : copia.get((int) Math.min(copia.size() - 1, Math.ceil(copia.size() * 0.95) - 1)) / 1000.0;
        Map<Gravedad, Double> ep = new EnumMap<>(Gravedad.class);
        Map<Gravedad, Double> rp = new EnumMap<>(Gravedad.class);
        for (Gravedad g : Gravedad.values()) {
            ep.put(g, esperaPorG.get(g).mediaSeg());
            rp.put(g, respuestaPorG.get(g).mediaSeg());
        }
        return new Resumen(llamadas.sum(), despachos.sum(), finalizadas.sum(), fallos.sum(), reasignadas.sum(),
                adelantos.sum(), escaladas.sum(), saturaciones.sum(),
                espera.mediaSeg(), espera.max.get() / 1000.0, respuesta.mediaSeg(), p95,
                duracion.mediaSeg(), ep, rp);
    }
}
