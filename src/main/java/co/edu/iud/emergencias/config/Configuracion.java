package co.edu.iud.emergencias.config;

/**
 * Parámetros de la simulación. Todos los tiempos están en milisegundos
 * SIMULADOS: el {@code Reloj} los convierte a tiempo real con la escala
 * ({@code escalaTiempo = 1.0} es tiempo real; {@code 0.1} corre 10 veces más
 * rápido, útil para el benchmark y las pruebas).
 *
 * Es un record inmutable: para cambiar un valor se usa {@link #modificar()}.
 */
public record Configuracion(
        double escalaTiempo,
        int medicos,
        int equipos,
        int operadores,
        int despachadores,
        double llamadasPorMin,
        double pesoGravedad,
        double pesoEspera,
        double pesoDistancia,
        double bonusReasignacion,
        long escalarGravedadCadaMs,
        long msPorKm,
        int camasPorHospital,
        long estadiaHospitalMs,
        boolean fallosAutomaticos,
        long intervaloFallosMs,
        long duracionFalloMs) {

    public Configuracion {
        if (escalaTiempo <= 0) {
            throw new IllegalArgumentException("La escala de tiempo debe ser positiva.");
        }
        if (medicos < 0 || equipos < 0) {
            throw new IllegalArgumentException("Médicos y equipos no pueden ser negativos.");
        }
        if (operadores < 1 || despachadores < 1) {
            throw new IllegalArgumentException("Se necesita al menos un operador y un despachador.");
        }
        if (llamadasPorMin <= 0) {
            throw new IllegalArgumentException("La intensidad de llamadas debe ser positiva.");
        }
        if (camasPorHospital < 1 || msPorKm < 0 || escalarGravedadCadaMs <= 0) {
            throw new IllegalArgumentException("Parámetros de hospital, velocidad o escalamiento inválidos.");
        }
    }

    public static Configuracion porDefecto() {
        return new Builder().construir();
    }

    public Builder modificar() {
        return new Builder(this);
    }

    /** Constructor fluido: parte de los valores por defecto o de otra configuración. */
    public static final class Builder {
        private double escalaTiempo = 1.0;
        private int medicos = 4;
        private int equipos = 2;
        private int operadores = 2;
        private int despachadores = 2;
        private double llamadasPorMin = 30;
        private double pesoGravedad = 1.0;
        private double pesoEspera = 2.0;
        private double pesoDistancia = 1.0;
        private double bonusReasignacion = 30.0;
        private long escalarGravedadCadaMs = 45_000;
        private long msPorKm = 250;
        private int camasPorHospital = 3;
        private long estadiaHospitalMs = 8_000;
        private boolean fallosAutomaticos = true;
        private long intervaloFallosMs = 45_000;
        private long duracionFalloMs = 8_000;

        public Builder() {
        }

        private Builder(Configuracion c) {
            this.escalaTiempo = c.escalaTiempo;
            this.medicos = c.medicos;
            this.equipos = c.equipos;
            this.operadores = c.operadores;
            this.despachadores = c.despachadores;
            this.llamadasPorMin = c.llamadasPorMin;
            this.pesoGravedad = c.pesoGravedad;
            this.pesoEspera = c.pesoEspera;
            this.pesoDistancia = c.pesoDistancia;
            this.bonusReasignacion = c.bonusReasignacion;
            this.escalarGravedadCadaMs = c.escalarGravedadCadaMs;
            this.msPorKm = c.msPorKm;
            this.camasPorHospital = c.camasPorHospital;
            this.estadiaHospitalMs = c.estadiaHospitalMs;
            this.fallosAutomaticos = c.fallosAutomaticos;
            this.intervaloFallosMs = c.intervaloFallosMs;
            this.duracionFalloMs = c.duracionFalloMs;
        }

        public Builder escalaTiempo(double v) {
            this.escalaTiempo = v;
            return this;
        }

        public Builder medicos(int v) {
            this.medicos = v;
            return this;
        }

        public Builder equipos(int v) {
            this.equipos = v;
            return this;
        }

        public Builder operadores(int v) {
            this.operadores = v;
            return this;
        }

        public Builder despachadores(int v) {
            this.despachadores = v;
            return this;
        }

        public Builder llamadasPorMin(double v) {
            this.llamadasPorMin = v;
            return this;
        }

        public Builder pesoGravedad(double v) {
            this.pesoGravedad = v;
            return this;
        }

        public Builder pesoEspera(double v) {
            this.pesoEspera = v;
            return this;
        }

        public Builder pesoDistancia(double v) {
            this.pesoDistancia = v;
            return this;
        }

        public Builder bonusReasignacion(double v) {
            this.bonusReasignacion = v;
            return this;
        }

        public Builder escalarGravedadCadaMs(long v) {
            this.escalarGravedadCadaMs = v;
            return this;
        }

        public Builder msPorKm(long v) {
            this.msPorKm = v;
            return this;
        }

        public Builder camasPorHospital(int v) {
            this.camasPorHospital = v;
            return this;
        }

        public Builder estadiaHospitalMs(long v) {
            this.estadiaHospitalMs = v;
            return this;
        }

        public Builder fallosAutomaticos(boolean v) {
            this.fallosAutomaticos = v;
            return this;
        }

        public Builder intervaloFallosMs(long v) {
            this.intervaloFallosMs = v;
            return this;
        }

        public Builder duracionFalloMs(long v) {
            this.duracionFalloMs = v;
            return this;
        }

        /** Política FIFO de referencia: ignora gravedad y distancia, solo cuenta la espera. */
        public Builder politicaFifo() {
            this.pesoGravedad = 0;
            this.pesoDistancia = 0;
            this.bonusReasignacion = 0;
            this.escalarGravedadCadaMs = Long.MAX_VALUE / 4;
            return this;
        }

        public Configuracion construir() {
            return new Configuracion(escalaTiempo, medicos, equipos, operadores, despachadores,
                    llamadasPorMin, pesoGravedad, pesoEspera, pesoDistancia, bonusReasignacion,
                    escalarGravedadCadaMs, msPorKm, camasPorHospital, estadiaHospitalMs,
                    fallosAutomaticos, intervaloFallosMs, duracionFalloMs);
        }
    }
}
