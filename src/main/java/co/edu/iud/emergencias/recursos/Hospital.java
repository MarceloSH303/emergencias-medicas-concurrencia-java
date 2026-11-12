package co.edu.iud.emergencias.recursos;

import co.edu.iud.emergencias.modelo.Ubicacion;

import java.util.concurrent.Semaphore;

/**
 * Hospital con un número limitado de camas. Las camas son un {@link Semaphore}:
 * es el mecanismo de Java pensado para "N permisos" y no requiere cerrojos
 * adicionales (admitir = {@code tryAcquire}, dar de alta = {@code release}).
 */
public final class Hospital {

    private final String nombre;
    private final Ubicacion ubicacion;
    private final int capacidad;
    private final Semaphore camas;
    private volatile boolean enLinea = true;

    public Hospital(String nombre, Ubicacion ubicacion, int capacidad) {
        this.nombre = nombre;
        this.ubicacion = ubicacion;
        this.capacidad = capacidad;
        this.camas = new Semaphore(capacidad);
    }

    public String nombre() {
        return nombre;
    }

    public Ubicacion ubicacion() {
        return ubicacion;
    }

    public int capacidad() {
        return capacidad;
    }

    public int camasLibres() {
        return camas.availablePermits();
    }

    public boolean enLinea() {
        return enLinea;
    }

    public void setEnLinea(boolean enLinea) {
        this.enLinea = enLinea;
    }

    /** Reserva una cama sin esperar. Falla si el hospital no responde o está lleno. */
    public boolean intentarAdmitir() {
        return enLinea && camas.tryAcquire();
    }

    public void darAlta() {
        camas.release();
    }

    @Override
    public String toString() {
        return nombre;
    }
}
