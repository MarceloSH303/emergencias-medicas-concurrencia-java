package co.edu.iud.emergencias.vista;

import co.edu.iud.emergencias.comunicacion.Evento;
import co.edu.iud.emergencias.config.Configuracion;
import co.edu.iud.emergencias.modelo.Gravedad;
import co.edu.iud.emergencias.monitoreo.Estadisticas;
import co.edu.iud.emergencias.monitoreo.Instantanea;
import co.edu.iud.emergencias.sistema.SistemaEmergencias;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Ventana principal. Regla de oro de Swing: los componentes solo se tocan desde el hilo de eventos.
 * Los hilos de trabajo jamás llaman a la interfaz; un {@link Timer} de Swing pide una instantánea
 * inmutable cada 400 ms y los eventos del bus llegan a una cola que ese mismo Timer vacía.
 */
@SuppressWarnings("serial")
public final class VentanaMonitor extends JFrame {

    private static final String[] VELOCIDADES = {"Tiempo real", "x2", "x5", "x10"};
    private static final double[] ESCALAS = {1.0, 0.5, 0.2, 0.1};

    private SistemaEmergencias sistema;
    private final ConcurrentLinkedQueue<String> lineasLog = new ConcurrentLinkedQueue<>();

    private final MapaPanel mapa = new MapaPanel();
    private final TablaEmergencias modeloEmergencias = new TablaEmergencias();
    private final TablaRecursos modeloRecursos = new TablaRecursos();
    private final JTextArea log = new JTextArea();
    private final JLabel cabecera = new JLabel(" ");
    private final JLabel[] estadisticas = new JLabel[8];

    private final JButton btnIniciar = new JButton("▶ Iniciar");
    private final JButton btnDetener = new JButton("■ Detener");
    private final JButton btnReiniciar = new JButton("↺ Reiniciar");
    private final JSlider sliderLlamadas = new JSlider(5, 120, 30);
    private final JLabel lblLlamadas = new JLabel("30 llamadas/min");
    private final JCheckBox chkFallos = new JCheckBox("Fallos auto", true);
    private final JComboBox<String> comboVelocidad = new JComboBox<>(VELOCIDADES);

    public VentanaMonitor() {
        super("Central de Emergencias Médicas — monitor en tiempo real");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        add(crearBarra(), BorderLayout.NORTH);

        JTable tablaE = new JTable(modeloEmergencias);
        tablaE.setRowHeight(22);
        tablaE.setDefaultRenderer(Object.class, new RenderizadorGravedad());
        JScrollPane spE = new JScrollPane(tablaE);
        spE.setBorder(BorderFactory.createTitledBorder("Emergencias activas (por prioridad)"));

        JTable tablaR = new JTable(modeloRecursos);
        tablaR.setRowHeight(22);
        tablaR.setDefaultRenderer(Object.class, new RenderizadorRecursos());
        JScrollPane spR = new JScrollPane(tablaR);
        spR.setBorder(BorderFactory.createTitledBorder("Ambulancias y hospitales"));

        JSplitPane derecha = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spE, spR);
        derecha.setResizeWeight(0.58);
        JSplitPane centro = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapa, derecha);
        centro.setResizeWeight(0.45);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane spLog = new JScrollPane(log);
        spLog.setBorder(BorderFactory.createTitledBorder("Registro de eventos (comunicación entre equipos)"));

        JPanel panelStats = new JPanel(new GridLayout(0, 2, 8, 2));
        panelStats.setBorder(BorderFactory.createTitledBorder("Estadísticas (tiempo simulado)"));
        String[] nombres = {"Llamadas / finalizadas", "Espera media (cola)", "Respuesta media", "Respuesta p95",
                "Espera máx.", "Fallos / reasignadas", "Adelantos / escaladas", "Utilización ambulancias"};
        for (int i = 0; i < estadisticas.length; i++) {
            panelStats.add(new JLabel(nombres[i] + ":"));
            estadisticas[i] = new JLabel("-");
            estadisticas[i].setFont(estadisticas[i].getFont().deriveFont(Font.BOLD));
            panelStats.add(estadisticas[i]);
        }
        JSplitPane sur = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelStats, spLog);
        sur.setResizeWeight(0.38);
        sur.setPreferredSize(new Dimension(900, 210));

        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centro, sur);
        vertical.setResizeWeight(0.75);
        add(vertical, BorderLayout.CENTER);

        cabecera.setFont(cabecera.getFont().deriveFont(Font.BOLD, 13f));
        cabecera.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(cabecera, BorderLayout.SOUTH);

        crearSistema();
        new Timer(400, e -> refrescar()).start();

        setSize(1280, 800);
        setLocationRelativeTo(null);
    }

    private JPanel crearBarra() {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        barra.add(btnIniciar);
        barra.add(btnDetener);
        barra.add(btnReiniciar);
        barra.add(Box.createHorizontalStrut(2));
        barra.add(new JLabel("Velocidad:"));
        barra.add(comboVelocidad);
        comboVelocidad.setSelectedIndex(1);
        barra.add(Box.createHorizontalStrut(2));
        sliderLlamadas.setPreferredSize(new Dimension(110, 24));
        barra.add(sliderLlamadas);
        lblLlamadas.setPreferredSize(new Dimension(105, 20));
        barra.add(lblLlamadas);
        barra.add(chkFallos);
        barra.add(Box.createHorizontalStrut(2));
        JButton btnCritica = new JButton("+ Crítica");
        JButton btnLeve = new JButton("+ Leve");
        JButton btnFallar = new JButton("⚡ Fallar ambulancia");
        barra.add(btnCritica);
        barra.add(btnLeve);
        barra.add(btnFallar);

        btnDetener.setEnabled(false);
        btnIniciar.addActionListener(e -> {
            sistema.iniciar();
            btnIniciar.setEnabled(false);
            btnDetener.setEnabled(true);
            comboVelocidad.setEnabled(false);
        });
        btnDetener.addActionListener(e -> {
            sistema.detener();
            btnDetener.setEnabled(false);
        });
        btnReiniciar.addActionListener(e -> crearSistema());
        sliderLlamadas.addChangeListener(e -> {
            lblLlamadas.setText(sliderLlamadas.getValue() + " llamadas/min");
            sistema.setLlamadasPorMin(sliderLlamadas.getValue());
        });
        chkFallos.addActionListener(e -> sistema.setFallosAutomaticos(chkFallos.isSelected()));
        btnCritica.addActionListener(e -> sistema.crearManual(Gravedad.CRITICO));
        btnLeve.addActionListener(e -> sistema.crearManual(Gravedad.LEVE));
        btnFallar.addActionListener(e -> sistema.fallarAmbulanciaAleatoria());
        return barra;
    }

    private void crearSistema() {
        if (sistema != null) {
            sistema.close();
        }
        double escala = ESCALAS[comboVelocidad.getSelectedIndex()];
        Configuracion cfg = Configuracion.porDefecto().modificar()
                .escalaTiempo(escala)
                .llamadasPorMin(sliderLlamadas.getValue())
                .fallosAutomaticos(chkFallos.isSelected())
                .construir();
        sistema = new SistemaEmergencias(cfg);
        sistema.bus().suscribir(this::encolarLinea);
        lineasLog.clear();
        log.setText("");
        btnIniciar.setEnabled(true);
        btnDetener.setEnabled(false);
        comboVelocidad.setEnabled(true);
        refrescar();
    }

    /** Se ejecuta en el hilo del bus: solo deja la línea en una cola concurrente, no toca Swing. */
    private void encolarLinea(Evento e) {
        lineasLog.add(e.texto());
    }

    private void refrescar() {
        Instantanea f = sistema.instantanea();
        mapa.mostrar(f);
        modeloEmergencias.setFilas(f.emergencias());
        modeloRecursos.setDatos(f.ambulancias(), f.hospitales());

        StringBuilder sb = new StringBuilder();
        String linea;
        int n = 0;
        while ((linea = lineasLog.poll()) != null && n++ < 200) {
            sb.append(linea).append('\n');
        }
        if (sb.length() > 0) {
            log.append(sb.toString());
            int exceso = log.getLineCount() - 600;
            if (exceso > 0) {
                try {
                    log.replaceRange("", 0, log.getLineEndOffset(exceso - 1));
                } catch (javax.swing.text.BadLocationException ignorada) {
                    // El texto cambió entre medias: se recorta en la siguiente actualización.
                }
            }
            log.setCaretPosition(log.getDocument().getLength());
        }

        long s = f.simMs() / 1000;
        cabecera.setText(String.format(Locale.ROOT,
                "Reloj simulado %02d:%02d   |   Pendientes: %d   |   Médicos libres: %d/%d   |   Equipos libres: %d/%d   |   %s",
                s / 60, s % 60, f.pendientes(), f.medicosLibres(), f.totalMedicos(), f.equiposLibres(),
                f.totalEquipos(), f.enMarcha() ? "EN MARCHA" : "DETENIDO"));

        Estadisticas.Resumen r = f.resumen();
        estadisticas[0].setText(r.llamadas() + " / " + r.finalizadas());
        estadisticas[1].setText(seg(r.esperaMediaSeg()));
        estadisticas[2].setText(seg(r.respuestaMediaSeg()));
        estadisticas[3].setText(seg(r.respuestaP95Seg()));
        estadisticas[4].setText(seg(r.esperaMaxSeg()));
        estadisticas[5].setText(r.fallos() + " / " + r.reasignadas());
        estadisticas[6].setText(r.adelantos() + " / " + r.escaladas());
        estadisticas[7].setText(String.format(Locale.ROOT, "%.0f %%", sistema.utilizacion() * 100));
    }

    private static String seg(double v) {
        return String.format(Locale.ROOT, "%.1f s", v);
    }

    // ------------------------------------------------------------------ modelos de tabla

    @SuppressWarnings("serial")
    private static final class TablaEmergencias extends AbstractTableModel {
        private static final String[] COLS = {"#", "Gravedad", "Estado", "Prioridad", "Espera (s)", "Ambulancia"};
        private List<Instantanea.FilaEmergencia> filas = List.of();

        void setFilas(List<Instantanea.FilaEmergencia> nuevas) {
            this.filas = nuevas;
            fireTableDataChanged();
        }

        Instantanea.FilaEmergencia fila(int i) {
            return filas.get(i);
        }

        @Override
        public int getRowCount() {
            return filas.size();
        }

        @Override
        public int getColumnCount() {
            return COLS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLS[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            Instantanea.FilaEmergencia f = filas.get(r);
            return switch (c) {
                case 0 -> "#" + f.id();
                case 1 -> f.gravedad() + (f.gravedad() != f.gravedadInicial() ? " ↑" : "");
                case 2 -> f.estado();
                case 3 -> String.format(Locale.ROOT, "%.1f", f.prioridad());
                case 4 -> String.format(Locale.ROOT, "%.0f", f.esperaSeg());
                default -> f.ambulancia();
            };
        }
    }

    @SuppressWarnings("serial")
    private static final class TablaRecursos extends AbstractTableModel {
        private static final String[] COLS = {"Recurso", "Estado", "Detalle"};
        private List<Instantanea.FilaAmbulancia> amb = List.of();
        private List<Instantanea.FilaHospital> hos = List.of();

        void setDatos(List<Instantanea.FilaAmbulancia> a, List<Instantanea.FilaHospital> h) {
            this.amb = a;
            this.hos = h;
            fireTableDataChanged();
        }

        Instantanea.FilaAmbulancia ambulancia(int i) {
            return i < amb.size() ? amb.get(i) : null;
        }

        @Override
        public int getRowCount() {
            return amb.size() + hos.size();
        }

        @Override
        public int getColumnCount() {
            return COLS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLS[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            if (r < amb.size()) {
                Instantanea.FilaAmbulancia a = amb.get(r);
                return switch (c) {
                    case 0 -> "Ambulancia " + a.id();
                    case 1 -> a.estado();
                    default -> String.format(Locale.ROOT, "%d misiones · uso %.0f %%%s", a.misiones(),
                            a.utilizacion() * 100, a.emergenciaId() > 0 ? " · caso #" + a.emergenciaId() : "");
                };
            }
            Instantanea.FilaHospital h = hos.get(r - amb.size());
            return switch (c) {
                case 0 -> h.nombre();
                case 1 -> h.enLinea() ? "En línea" : "Sin enlace";
                default -> h.camasLibres() + " de " + h.capacidad() + " camas libres";
            };
        }
    }

    // ------------------------------------------------------------------ renderizadores

    @SuppressWarnings("serial")
    private final class RenderizadorGravedad extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            setHorizontalAlignment(c >= 3 && c <= 4 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            if (r < modeloEmergencias.getRowCount() && c == 1) {
                setForeground(Colores.de(modeloEmergencias.fila(r).gravedad()));
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                setForeground(sel ? t.getSelectionForeground() : t.getForeground());
                setFont(t.getFont());
            }
            return this;
        }
    }

    @SuppressWarnings("serial")
    private final class RenderizadorRecursos extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            Instantanea.FilaAmbulancia a = modeloRecursos.ambulancia(r);
            if (c == 1 && a != null) {
                setForeground(Colores.de(a.estado()));
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (c == 1) {
                setForeground(String.valueOf(v).startsWith("Sin") ? Colores.SIN_ENLACE : new Color(0, 121, 107));
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                setForeground(sel ? t.getSelectionForeground() : t.getForeground());
                setFont(t.getFont());
            }
            return this;
        }
    }
}
