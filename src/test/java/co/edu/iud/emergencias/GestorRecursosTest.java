package co.edu.iud.emergencias;

import co.edu.iud.emergencias.modelo.Ciudad;
import co.edu.iud.emergencias.modelo.Gravedad;
import co.edu.iud.emergencias.modelo.Requisitos;
import co.edu.iud.emergencias.modelo.Ubicacion;
import co.edu.iud.emergencias.recursos.Ambulancia;
import co.edu.iud.emergencias.recursos.EstadoRecurso;
import co.edu.iud.emergencias.recursos.GestorRecursos;
import co.edu.iud.emergencias.recursos.Hospital;
import co.edu.iud.emergencias.recursos.Reserva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GestorRecursosTest {

    static GestorRecursos gestor(int ambulancias, int medicos, int equipos) {
        List<Ambulancia> l = new ArrayList<>();
        for (int i = 0; i < ambulancias; i++) {
            l.add(new Ambulancia("A" + (i + 1), Ciudad.BASES.get(i % Ciudad.BASES.size()).ubicacion()));
        }
        List<Hospital> h = List.of(new Hospital("H", new Ubicacion(10, 10), 3));
        return new GestorRecursos(l, h, medicos, equipos);
    }

    private static final Ubicacion AQUI = new Ubicacion(5, 5);

    @Test
    @DisplayName("Todo o nada: si falta un equipo no se toma ni la ambulancia ni el médico")
    void todoONada() {
        GestorRecursos g = gestor(3, 2, 0);
        Optional<Reserva> r = g.intentarReservar(Gravedad.CRITICO.requisitos(), AQUI, null);
        assertTrue(r.isEmpty());
        assertEquals(2, g.medicosLibres());
        for (Ambulancia a : g.ambulancias()) {
            assertEquals(EstadoRecurso.DISPONIBLE, a.estado());
        }
    }

    @Test
    @DisplayName("Se asigna la ambulancia libre más cercana")
    void masCercana() {
        GestorRecursos g = gestor(6, 4, 2);
        Reserva r = g.intentarReservar(Gravedad.LEVE.requisitos(), new Ubicacion(16, 5), null).orElseThrow();
        assertEquals("A2", r.ambulancia().id()); // base (16,4)
        assertEquals(EstadoRecurso.EN_CAMINO, r.ambulancia().estado());
    }

    @Test
    @DisplayName("Liberar dos veces la misma reserva no crea recursos de la nada")
    void liberacionIdempotente() {
        GestorRecursos g = gestor(2, 1, 1);
        Reserva r = g.intentarReservar(Gravedad.CRITICO.requisitos(), AQUI, null).orElseThrow();
        assertEquals(0, g.medicosLibres());
        g.liberarPersonal(r);
        g.liberarPersonal(r);
        g.liberarAmbulancia(r);
        g.liberarAmbulancia(r);
        assertEquals(1, g.medicosLibres());
        assertEquals(1, g.equiposLibres());
    }

    @Test
    @DisplayName("Adelanto: no se concede un recurso que la emergencia más urgente necesita")
    void adelantoRespetaALaCabeza() {
        // Cabeza = crítico (ambulancia + médico + equipo), pero no hay equipos.
        Requisitos cabeza = Gravedad.CRITICO.requisitos();
        GestorRecursos dos = gestor(2, 1, 0);
        // Con 2 ambulancias, una leve puede pasar y aún queda una para la cabeza
        assertTrue(dos.intentarReservar(Gravedad.LEVE.requisitos(), AQUI, cabeza).isPresent());
        // Con una sola ambulancia, atender a la leve retrasaría al crítico
        GestorRecursos una = gestor(1, 1, 0);
        assertTrue(una.intentarReservar(Gravedad.LEVE.requisitos(), AQUI, cabeza).isEmpty());
    }

    @Test
    @DisplayName("Una ambulancia que pierde el enlace estando libre sale de servicio y no se asigna")
    void enlaceCaido() {
        GestorRecursos g = gestor(1, 1, 1);
        Ambulancia a = g.ambulancias().get(0);
        assertTrue(g.caerEnlace(a));
        assertFalse(g.caerEnlace(a));
        assertEquals(EstadoRecurso.FUERA_DE_SERVICIO, a.estado());
        assertTrue(g.intentarReservar(Gravedad.LEVE.requisitos(), AQUI, null).isEmpty());
        g.restaurarEnlace(a);
        assertEquals(EstadoRecurso.DISPONIBLE, a.estado());
        assertTrue(g.intentarReservar(Gravedad.LEVE.requisitos(), AQUI, null).isPresent());
    }

    @Test
    @DisplayName("Estrés: 16 hilos reservan y liberan sin duplicar recursos, sin deadlock y sin fugas")
    void estresConcurrente() {
        GestorRecursos g = gestor(6, 4, 2);
        AtomicBoolean[] enUso = new AtomicBoolean[6];
        for (int i = 0; i < enUso.length; i++) {
            enUso[i] = new AtomicBoolean();
        }
        AtomicInteger medicosEnUso = new AtomicInteger();
        AtomicInteger equiposEnUso = new AtomicInteger();
        AtomicInteger errores = new AtomicInteger();
        AtomicInteger concedidas = new AtomicInteger();

        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            ExecutorService pool = Executors.newFixedThreadPool(16);
            List<Future<?>> fs = new ArrayList<>();
            for (int t = 0; t < 16; t++) {
                fs.add(pool.submit(() -> {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    for (int i = 0; i < 5_000; i++) {
                        Gravedad gr = Gravedad.values()[r.nextInt(4)];
                        Optional<Reserva> res = g.intentarReservar(gr.requisitos(), AQUI, null);
                        if (res.isEmpty()) {
                            Thread.onSpinWait();
                            continue;
                        }
                        Reserva rv = res.get();
                        concedidas.incrementAndGet();
                        int idx = Integer.parseInt(rv.ambulancia().id().substring(1)) - 1;
                        // Si dos hilos recibieran la misma ambulancia, este compareAndSet fallaría
                        if (!enUso[idx].compareAndSet(false, true)) {
                            errores.incrementAndGet();
                        }
                        if (medicosEnUso.addAndGet(rv.medicos()) > 4) {
                            errores.incrementAndGet();
                        }
                        if (equiposEnUso.addAndGet(rv.equipos()) > 2) {
                            errores.incrementAndGet();
                        }
                        Thread.onSpinWait();
                        medicosEnUso.addAndGet(-rv.medicos());
                        equiposEnUso.addAndGet(-rv.equipos());
                        g.liberarPersonal(rv);
                        enUso[idx].set(false);
                        g.liberarAmbulancia(rv);
                    }
                }));
            }
            for (Future<?> f : fs) {
                f.get();
            }
            pool.shutdown();
        });

        assertEquals(0, errores.get());
        assertTrue(concedidas.get() > 1_000);
        assertEquals(4, g.medicosLibres());
        assertEquals(2, g.equiposLibres());
        for (Ambulancia a : g.ambulancias()) {
            assertEquals(EstadoRecurso.DISPONIBLE, a.estado());
        }
    }
}
