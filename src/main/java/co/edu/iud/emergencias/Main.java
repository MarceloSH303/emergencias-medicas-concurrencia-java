package co.edu.iud.emergencias;

import co.edu.iud.emergencias.vista.VentanaMonitor;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Si el aspecto del sistema no está disponible se usa el de Swing por defecto.
        }
        SwingUtilities.invokeLater(() -> new VentanaMonitor().setVisible(true));
    }
}
