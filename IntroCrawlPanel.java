import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class IntroCrawlPanel extends JPanel {

    private static final int LINE_SPACING = 36;
    private static final String[] LINES = {
        "SETTLERS OF CATAN",
        "By Devansh Sanghavi, APCS A, 2026 ",
        "",
        "HOW TO PLAY",
        "",
        "PLACING SETTLEMENTS",
        "Click any vertex on the board to place a settlement.",
        "Each settlement costs 1 Wood, 1 Brick, 1 Wool, and 1 Wheat.",
        "Settlements cannot be placed next to an existing one.",
        "",
        "DICE ROLLS & RESOURCES",
        "At the start of your turn, roll the dice.",
        "Every tile has a number. When that number is rolled,",
        "all players with settlements on that tile earn its resource.",
        "Rolling a 7 gives no resources to anyone.",
        "",
        "BUILDING",
        "Use resources to build more settlements and grow your empire.",
        "Each new settlement earns you 1 Victory Point.",
        "",
        "WINNING",
        "The first player to reach 10 Victory Points wins the game.",
        "",
        "Click the question mark on the bottom row for help during game or read the readme",
        "",
        "Press any key to skip..."
    };

    private int yOffset;
    private final Timer scrollTimer;
    private final GameController controller;

    public IntroCrawlPanel(GameController gc) {
        this.controller = gc;
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(800, 600));
        setFocusable(true);

        // start text just below the bottom edge
        yOffset = 620;

        scrollTimer = new Timer(30, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                yOffset--;
                repaint();
                // all text is above the top edge when this is true
                if (yOffset + LINES.length * LINE_SPACING < 0) {
                    scrollTimer.stop();
                    controller.showGame();
                }
            }
        });

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                scrollTimer.stop();
                controller.showGame();
            }
        });
    }

    public void startCrawl() {
        scrollTimer.start();
        requestFocusInWindow();
    }

    // overide here idk?
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setFont(new Font("Monospaced", Font.BOLD, 18));

        for (int i = 0; i < LINES.length; i++)
        {
            String line = LINES[i];
            if (line.isEmpty()) continue;

            int y = yOffset + i * LINE_SPACING;
            // only draw if on screen
            if (y < -LINE_SPACING || y > getHeight() + LINE_SPACING) continue;

            // title line gets larger gold text
            if (i == 0) {
                g.setFont(new Font("Monospaced", Font.BOLD, 28));
                g.setColor(new Color(255, 215, 0));
            } else if (line.equals(line.toUpperCase()) && !line.isEmpty()) {
                g.setFont(new Font("Monospaced", Font.BOLD, 18));
                g.setColor(new Color(255, 215, 0));
            } else {
            g.setFont(new Font("Monospaced", Font.PLAIN, 16));
            g.setColor(new Color(230, 200, 100));
            }

            FontMetrics fm = g.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(line)) / 2;
            g.drawString(line, x, y);
        }
    }
}
