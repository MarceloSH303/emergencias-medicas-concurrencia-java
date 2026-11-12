# Sistema de Gestión de Emergencias Médicas con Concurrencia

EA2 · Desarrollo de Software Seguro · Institución Universitaria Digital de Antioquia
Autor: Marcelo Suárez Higuita (único integrante, contribución 100 %)

Simulación en Java (Swing) de una central de emergencias: llamadas concurrentes, recursos compartidos
limitados (ambulancias, médicos, equipos, camas), priorización dinámica, monitoreo en tiempo real y
tolerancia a fallos de comunicación.

## Requisitos
- JDK 21 o superior (probado con 21)
- Maven 3.9+ (solo para compilar y ejecutar las pruebas; el código no usa librerías externas)

## Ejecutar
```bash
mvn package
java -jar target/emergencias-1.0.jar          # abre el monitor gráfico
```
En IntelliJ: abrir la carpeta como proyecto Maven y ejecutar `co.edu.iud.emergencias.Main`.

En el monitor: **Iniciar**, ajustar las llamadas por minuto con el control deslizante, crear una emergencia
crítica o leve a mano, y **Fallar ambulancia** para provocar una pérdida de enlace y ver cómo la
emergencia vuelve a la cola y se reasigna. La velocidad (x1, x2, x5, x10) se elige antes de iniciar.

Otras herramientas:
```bash
mvn test                                                                  # pruebas JUnit 5
java -cp target/classes co.edu.iud.emergencias.demo.DeadlockDemo          # deadlock ingenuo vs. solución
java -cp target/classes co.edu.iud.emergencias.benchmark.Benchmark 2      # análisis de rendimiento (~7 min)
```

## Estructura
| Paquete | Responsabilidad |
|---|---|
| `modelo` | Emergencia (ciclo de vida validado), gravedad, ubicación, ciudad |
| `config` | `Configuracion` inmutable con *Builder* |
| `recursos` | `GestorRecursos` (reserva atómica todo-o-nada), ambulancias, hospitales (semáforo de camas) |
| `prioridad` | Algoritmo de priorización (gravedad + espera + distancia + reasignación) |
| `despacho` | `PlanificadorDespacho`: cola, escalamiento, backfill |
| `hilos` | Generador, operadores, despachadores, hilo por ambulancia, inyector de fallos |
| `comunicacion` | Bus de eventos publicador/suscriptor |
| `monitoreo` | Estadísticas sin cerrojos e instantáneas inmutables para la interfaz |
| `vista` | Swing: mapa, tablas, estadísticas y registro |
| `sistema` | `SistemaEmergencias`: fachada que cablea todo |
| `demo`, `benchmark` | Deadlock ingenuo vs. solución; análisis de carga |

## Decisiones de concurrencia (resumen)
- **Sin condiciones de carrera**: el estado compartido tiene un único dueño y un cerrojo (`GestorRecursos`, `PlanificadorDespacho`); los demás datos son inmutables o atómicos.
- **Sin deadlock**: la reserva es de todo-o-nada (nunca se retiene un recurso mientras se espera otro) y los cerrojos siguen la jerarquía planificador → recursos.
- **Sin inanición**: el puntaje de prioridad crece con la espera y la gravedad escala cada 45 s simulados.
- **Tolerancia a fallos**: `FalloConexionException` cancela la misión, devuelve los recursos en `finally` y reencola la emergencia con bono de prioridad.
- **Interfaz**: los hilos de trabajo nunca tocan Swing; un `javax.swing.Timer` lee instantáneas inmutables.

Los tiempos son simulados (`Reloj`): con escala 1,0 corre en tiempo real y con 0,02 va 50 veces más rápido (benchmark y pruebas).
