package co.edu.iud.emergencias.benchmark;

import co.edu.iud.emergencias.config.Configuracion;
import co.edu.iud.emergencias.modelo.Gravedad;
import co.edu.iud.emergencias.monitoreo.Estadisticas;
import co.edu.iud.emergencias.sistema.SistemaEmergencias;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Análisis de rendimiento bajo distintas cargas. Cada experimento corre la simulación a 50x (escala
 * 0,02) durante 600 s simulados y luego deja drenar la cola, de modo que se miden TODAS las llamadas.
 * Resultado: tabla en consola y en {@code docs/benchmark_resultados.md}.
 *
 * Uso: {@code java -cp target/classes co.edu.iud.emergencias.benchmark.Benchmark [repeticiones]}
 */
public final class Benchmark {

    private static final double ESCALA = 0.02;
    private static final long VENTANA_SIM_MS = 600_000;

    private record Medida(long llamadas, long finalizadas, double espera, double respuesta, double p95,
                          double critico, double grave, double moderado, double leve, double utilizacion,
                          long fallos, long reasignadas) {
    }

    private Benchmark() {
    }

    public static void main(String[] args) throws Exception {
        int reps = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        StringBuilder md = new StringBuilder();
        md.append("# Resultados del benchmark\n\n");
        md.append("Simulación a 50x, ventana de 600 s simulados + drenado de la cola. Promedio de ")
                .append(reps).append(" repeticiones. Tiempos en segundos SIMULADOS. Recursos por defecto: ")
                .append("6 ambulancias, 4 médicos, 2 equipos, 3 hospitales de 3 camas, 2 operadores, 2 despachadores.\n\n");

        md.append("## A. Carga creciente: prioridad por gravedad vs. FIFO\n\n");
        encabezado(md, "Política / carga");
        for (int carga : new int[]{10, 20, 30, 45, 60}) {
            fila(md, "Prioridad, " + carga + "/min", repetir(reps, b -> b.llamadasPorMin(carga).fallosAutomaticos(false)));
            fila(md, "FIFO, " + carga + "/min", repetir(reps, b -> b.llamadasPorMin(carga).fallosAutomaticos(false).politicaFifo()));
        }

        md.append("\n## B. Cuello de botella en el centro de llamadas (45 llamadas/min, sin fallos)\n\n");
        encabezado(md, "Operadores");
        for (int ops : new int[]{1, 2, 4}) {
            fila(md, ops + " operador(es)", repetir(reps, b -> b.llamadasPorMin(45).fallosAutomaticos(false).operadores(ops)));
        }

        md.append("\n## C. Tolerancia a fallos (30 llamadas/min)\n\n");
        encabezado(md, "Escenario");
        fila(md, "Sin fallos", repetir(reps, b -> b.llamadasPorMin(30).fallosAutomaticos(false)));
        fila(md, "Con fallos (cada ~15 s, 8 s de caída)", repetir(reps,
                b -> b.llamadasPorMin(30).fallosAutomaticos(true).intervaloFallosMs(15_000)));

        System.out.println(md);
        Path salida = Path.of("docs", "benchmark_resultados.md");
        try {
            Files.createDirectories(salida.getParent());
            Files.writeString(salida, md.toString(), StandardCharsets.UTF_8);
            System.out.println("Guardado en " + salida.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("No se pudo guardar el archivo: " + e.getMessage());
        }
    }

    private static void encabezado(StringBuilder md, String primera) {
        md.append("| ").append(primera).append(" | Llamadas | Atendidas | Espera media | Respuesta media | p95 | Crítico | Grave | Moderado | Leve | Uso amb. | Fallos |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
    }

    private static void fila(StringBuilder md, String nombre, Medida m) {
        md.append(String.format(Locale.ROOT, "| %s | %d | %d | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %.0f %% | %d |%n",
                nombre, m.llamadas(), m.finalizadas(), m.espera(), m.respuesta(), m.p95(), m.critico(), m.grave(),
                m.moderado(), m.leve(), m.utilizacion() * 100, m.fallos()));
        System.out.println("  listo: " + nombre);
    }

    private static Medida repetir(int reps, UnaryOperator<Configuracion.Builder> ajuste) throws InterruptedException {
        List<Medida> medidas = new ArrayList<>();
        for (int i = 0; i < reps; i++) {
            medidas.add(ejecutar(ajuste.apply(Configuracion.porDefecto().modificar().escalaTiempo(ESCALA)).construir()));
        }
        return promedio(medidas);
    }

    private static Medida ejecutar(Configuracion cfg) throws InterruptedException {
        try (SistemaEmergencias s = new SistemaEmergencias(cfg)) {
            s.iniciar();
            Thread.sleep(Math.round(VENTANA_SIM_MS * ESCALA));
            s.setGeneracion(false);
            double utilizacion = s.utilizacion();
            s.esperarReposo(120_000);
            Estadisticas.Resumen r = s.estadisticas().resumen();
            return new Medida(r.llamadas(), r.finalizadas(), r.esperaMediaSeg(), r.respuestaMediaSeg(),
                    r.respuestaP95Seg(), r.respuestaPorGravedadSeg().get(Gravedad.CRITICO),
                    r.respuestaPorGravedadSeg().get(Gravedad.GRAVE), r.respuestaPorGravedadSeg().get(Gravedad.MODERADO),
                    r.respuestaPorGravedadSeg().get(Gravedad.LEVE), utilizacion, r.fallos(), r.reasignadas());
        }
    }

    private static Medida promedio(List<Medida> l) {
        int n = l.size();
        return new Medida(
                Math.round(l.stream().mapToLong(Medida::llamadas).average().orElse(0)),
                Math.round(l.stream().mapToLong(Medida::finalizadas).average().orElse(0)),
                l.stream().mapToDouble(Medida::espera).sum() / n,
                l.stream().mapToDouble(Medida::respuesta).sum() / n,
                l.stream().mapToDouble(Medida::p95).sum() / n,
                l.stream().mapToDouble(Medida::critico).sum() / n,
                l.stream().mapToDouble(Medida::grave).sum() / n,
                l.stream().mapToDouble(Medida::moderado).sum() / n,
                l.stream().mapToDouble(Medida::leve).sum() / n,
                l.stream().mapToDouble(Medida::utilizacion).sum() / n,
                Math.round(l.stream().mapToLong(Medida::fallos).average().orElse(0)),
                Math.round(l.stream().mapToLong(Medida::reasignadas).average().orElse(0)));
    }
}
