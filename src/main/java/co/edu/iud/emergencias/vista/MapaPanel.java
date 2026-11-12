package co.edu.iud.emergencias.vista;

import co.edu.iud.emergencias.modelo.Ciudad;
import co.edu.iud.emergencias.modelo.EstadoEmergencia;
import co.edu.iud.emergencias.monitoreo.Instantanea;
import co.edu.iud.emergencias.recursos.EstadoRecurso;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;

/** Mapa de la ciudad. Solo dibuja la última instantánea inmutable; se actualiza desde el hilo de Swing. */
@SuppressWarnings("serial")
final class MapaPanel extends JPanel {

    private static final int MARGEN = 18;
    private Instantanea foto;

    MapaPanel() {
        setPreferredSize(new Dimension(520, 520));
        setBackground(new Color(247, 248, 250));
    }

    void mostrar(Instantanea nueva) {
        this.foto = nueva;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int lado = Math.min(getWidth(), getHeight() - 28) - 2 * MARGEN;
        if (lado < 100) {
            g.dispose();
            return;
        }
        int ox = (getWidth() - lado) / 2;
        int oy = MARGEN;
        double k = lado / Ciudad.LADO_KM;

        g.setColor(Color.WHITE);
        g.fillRect(ox, oy, lado, lado);
        g.setColor(new Color(225, 228, 233));
        for (int i = 0; i <= 20; i += 2) {
            g.drawLine((int) (ox + i * k), oy, (int) (ox + i * k), oy + lado);
            g.drawLine(ox, (int) (oy + i * k), ox + lado, (int) (oy + i * k));
        }
        g.setColor(new Color(150, 155, 165));
        g.drawRect(ox, oy, lado, lado);

        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        FontMetrics fm = g.getFontMetrics();

        // Bases
        for (Ciudad.PuntoNombrado b : Ciudad.BASES) {
            int x = (int) (ox + b.ubicacion().x() * k);
            int y = (int) (oy + b.ubicacion().y() * k);
            g.setColor(new Color(190, 195, 205));
            g.drawRect(x - 7, y - 7, 14, 14);
        }

        if (foto != null) {
            // Hospitales
            for (Instantanea.FilaHospital h : foto.hospitales()) {
                int x = (int) (ox + h.x() * k);
                int y = (int) (oy + h.y() * k);
                g.setColor(h.enLinea() ? new Color(0, 121, 107) : Colores.SIN_ENLACE);
                g.fillRoundRect(x - 13, y - 13, 26, 26, 6, 6);
                g.setColor(Color.WHITE);
                g.drawString("H", x - fm.stringWidth("H") / 2, y + 4);
                g.setColor(Color.DARK_GRAY);
                String t = h.enLinea() ? h.camasLibres() + "/" + h.capacidad() + " camas" : "sin enlace";
                g.drawString(t, x - fm.stringWidth(t) / 2, y + 26);
            }

            // Líneas ambulancia → emergencia
            Stroke base = g.getStroke();
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
            for (Instantanea.FilaAmbulancia a : foto.ambulancias()) {
                if (a.emergenciaId() > 0) {
                    for (Instantanea.FilaEmergencia e : foto.emergencias()) {
                        if (e.id() == a.emergenciaId()) {
                            g.setColor(new Color(120, 130, 150));
                            g.drawLine((int) (ox + a.x() * k), (int) (oy + a.y() * k),
                                    (int) (ox + e.x() * k), (int) (oy + e.y() * k));
                        }
                    }
                }
            }
            g.setStroke(base);

            // Emergencias (rombos)
            for (Instantanea.FilaEmergencia e : foto.emergencias()) {
                int x = (int) (ox + e.x() * k);
                int y = (int) (oy + e.y() * k);
                Path2D rombo = new Path2D.Double();
                rombo.moveTo(x, y - 9);
                rombo.lineTo(x + 9, y);
                rombo.lineTo(x, y + 9);
                rombo.lineTo(x - 9, y);
                rombo.closePath();
                g.setColor(Colores.de(e.gravedad()));
                g.fill(rombo);
                if (e.estado() != EstadoEmergencia.PENDIENTE) {
                    g.setColor(Color.WHITE);
                    g.draw(rombo);
                }
                g.setColor(new Color(40, 40, 40));
                g.drawString("#" + e.id(), x + 10, y - 6);
            }

            // Ambulancias (círculos)
            for (Instantanea.FilaAmbulancia a : foto.ambulancias()) {
                int x = (int) (ox + a.x() * k);
                int y = (int) (oy + a.y() * k);
                g.setColor(Colores.de(a.estado()));
                g.fillOval(x - 10, y - 10, 20, 20);
                g.setColor(Color.WHITE);
                String id = a.id();
                g.drawString(id, x - fm.stringWidth(id) / 2, y + 4);
                if (a.estado() == EstadoRecurso.FUERA_DE_SERVICIO || !a.enlace()) {
                    g.setColor(Colores.CRITICO);
                    g.setStroke(new BasicStroke(2f));
                    g.drawOval(x - 12, y - 12, 24, 24);
                    g.setStroke(base);
                }
            }
        }

        // Leyenda
        int ly = oy + lado + 18;
        int lx = ox;
        lx = leyenda(g, fm, lx, ly, Colores.DISPONIBLE, "Disponible");
        lx = leyenda(g, fm, lx, ly, Colores.EN_CAMINO, "En camino");
        lx = leyenda(g, fm, lx, ly, Colores.OCUPADA, "Ocupada");
        lx = leyenda(g, fm, lx, ly, Colores.SIN_ENLACE, "Sin enlace");
        g.setColor(Color.DARK_GRAY);
        g.drawString("◆ emergencia", lx + 8, ly);
        g.dispose();
    }

    private static int leyenda(Graphics2D g, FontMetrics fm, int x, int y, Color c, String t) {
        g.setColor(c);
        g.fillOval(x, y - 9, 11, 11);
        g.setColor(Color.DARK_GRAY);
        g.drawString(t, x + 15, y);
        return x + 15 + fm.stringWidth(t) + 12;
    }
}
