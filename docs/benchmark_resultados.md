# Resultados del benchmark

Simulación a 50x, ventana de 600 s simulados + drenado de la cola. Promedio de 2 repeticiones. Tiempos en segundos SIMULADOS. Recursos por defecto: 6 ambulancias, 4 médicos, 2 equipos, 3 hospitales de 3 camas, 2 operadores, 2 despachadores.

## A. Carga creciente: prioridad por gravedad vs. FIFO

| Política / carga | Llamadas | Atendidas | Espera media | Respuesta media | p95 | Crítico | Grave | Moderado | Leve | Uso amb. | Fallos |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Prioridad, 10/min | 111 | 111 | 2.0 | 3.0 | 4.7 | 3.2 | 2.9 | 2.9 | 3.2 | 23 % | 0 |
| FIFO, 10/min | 89 | 89 | 2.1 | 3.0 | 4.2 | 3.1 | 2.9 | 3.0 | 3.1 | 19 % | 0 |
| Prioridad, 20/min | 185 | 185 | 2.2 | 3.5 | 6.0 | 3.5 | 3.6 | 3.4 | 3.6 | 41 % | 0 |
| FIFO, 20/min | 201 | 201 | 2.2 | 3.5 | 5.7 | 3.2 | 3.4 | 3.6 | 3.5 | 44 % | 0 |
| Prioridad, 30/min | 285 | 285 | 3.4 | 5.2 | 11.0 | 4.6 | 4.9 | 5.1 | 5.8 | 70 % | 0 |
| FIFO, 30/min | 283 | 283 | 3.2 | 5.0 | 10.7 | 5.2 | 4.8 | 5.1 | 5.0 | 67 % | 0 |
| Prioridad, 45/min | 454 | 454 | 107.4 | 109.3 | 347.3 | 139.8 | 156.1 | 109.8 | 62.0 | 93 % | 0 |
| FIFO, 45/min | 442 | 442 | 66.0 | 68.4 | 125.9 | 78.3 | 70.2 | 64.7 | 67.7 | 98 % | 0 |
| Prioridad, 60/min | 568 | 568 | 303.0 | 304.8 | 852.8 | 440.2 | 410.8 | 355.6 | 93.1 | 89 % | 0 |
| FIFO, 60/min | 608 | 608 | 188.7 | 191.0 | 371.1 | 188.9 | 189.7 | 195.1 | 188.4 | 98 % | 0 |

## B. Cuello de botella en el centro de llamadas (45 llamadas/min, sin fallos)

| Operadores | Llamadas | Atendidas | Espera media | Respuesta media | p95 | Crítico | Grave | Moderado | Leve | Uso amb. | Fallos |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 operador(es) | 433 | 433 | 143.3 | 145.2 | 266.7 | 141.5 | 150.0 | 142.7 | 145.6 | 77 % | 0 |
| 2 operador(es) | 452 | 452 | 126.3 | 128.2 | 370.8 | 162.9 | 167.6 | 147.9 | 70.4 | 91 % | 0 |
| 4 operador(es) | 419 | 419 | 95.7 | 97.7 | 279.4 | 115.6 | 113.9 | 111.2 | 63.9 | 90 % | 0 |

## C. Tolerancia a fallos (30 llamadas/min)

| Escenario | Llamadas | Atendidas | Espera media | Respuesta media | p95 | Crítico | Grave | Moderado | Leve | Uso amb. | Fallos |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Sin fallos | 296 | 296 | 4.3 | 6.2 | 17.6 | 4.8 | 4.9 | 6.2 | 7.7 | 73 % | 0 |
| Con fallos (cada ~15 s, 8 s de caída) | 311 | 311 | 8.1 | 10.2 | 26.6 | 6.2 | 6.2 | 10.0 | 16.1 | 83 % | 60 |
