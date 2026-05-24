import java.awt.*;
import javax.swing.*;
import java.util.ArrayList;

// draws the board, also makes the sidebar
public class GameView extends JPanel {

    // the board
    private GameBoard board;

    // need players to get colors for buildings
    private ArrayList<Player> players;

    // msg shown at bottom of board
    private String message;

    // sidebar labels, 1 per player
    private JLabel[] playerLabels;

    // colors by player index
    private static final Color[] PLAYER_COLORS = {
        new Color(30, 100, 210),   // blue
        new Color(200, 40, 40),    // red
        new Color(200, 120, 0),    // orange
        new Color(100, 0, 160)     // purple
    };

    public GameView(GameBoard board, ArrayList<Player> players) {
        this.board = board;
        this.players = players;
        this.message = "Welcome to Catan!";
        this.playerLabels = null;
        setPreferredSize(new Dimension(700, 620));
        setBackground(new Color(65, 105, 225)); // ocean
    }

    // draws tiles then vertex dots on top
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        ArrayList<Tile> tiles = board.getTiles();
        for (int i = 0; i < tiles.size(); i++) {
            drawTile(g, tiles.get(i));
        }

        ArrayList<Vertex> verts = board.getVertices();
        for (int i = 0; i < verts.size(); i++) {
            drawVertex(g, verts.get(i));
        }

        // status msg at bottom
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 13));
        g.drawString(message, 10, getHeight() - 8);
    }

    // draws one hex: color fill, outline, dice num in center
    private void drawTile(Graphics g, Tile t) {
        g.setColor(getTileColor(t.getResourceType()));
        g.fillPolygon(t.getXPoints(), t.getYPoints(), 6);

        g.setColor(Color.BLACK);
        g.drawPolygon(t.getXPoints(), t.getYPoints(), 6);

        // avg corners to find center
        int cx = 0;
        int cy = 0;
        for (int x : t.getXPoints()) cx += x;
        for (int y : t.getYPoints()) cy += y;
        cx /= 6;
        cy /= 6;

        // draw dice num, red if 6 or 8 (those r the good rolls)
        if (t.getDiceNumber() > 0) {
            if (t.getDiceNumber() == 6 || t.getDiceNumber() == 8) {
                g.setColor(Color.RED);
            } else {
                g.setColor(Color.BLACK);
            }
            g.setFont(new Font("Arial", Font.BOLD, 18));
            FontMetrics fm = g.getFontMetrics();
            String num = "" + t.getDiceNumber();
            g.drawString(num, cx - fm.stringWidth(num) / 2, cy + fm.getAscent() / 2 - 2);
        }

        // robber = black circle
        if (t.hasRobber()) {
            g.setColor(Color.BLACK);
            g.fillOval(cx - 10, cy + 12, 20, 20);
            g.setColor(Color.WHITE);
            g.drawOval(cx - 10, cy + 12, 20, 20);
        }
    }

    // empty vertex = faint dot, occupied = colored shape
    // settlement = circle, city = square
    private void drawVertex(Graphics g, Vertex v) {
        if (v.isEmpty()) {
            g.setColor(new Color(255, 255, 255, 80));
            g.fillOval(v.getX() - 5, v.getY() - 5, 10, 10);
            g.setColor(new Color(180, 180, 180));
            g.drawOval(v.getX() - 5, v.getY() - 5, 10, 10);
        } else {
            Building b = v.getBuilding();
            Color c = getPlayerColor(b.getOwner());
            g.setColor(c);
            if (b instanceof City) {
                g.fillRect(v.getX() - 9, v.getY() - 9, 18, 18);
                g.setColor(Color.BLACK);
                g.drawRect(v.getX() - 9, v.getY() - 9, 18, 18);
            } else {
                g.fillOval(v.getX() - 8, v.getY() - 8, 16, 16);
                g.setColor(Color.BLACK);
                g.drawOval(v.getX() - 8, v.getY() - 8, 16, 16);
            }
        }
    }

    // tile color by resource
    private Color getTileColor(String resourceType) {
        if (resourceType.equals(ResourceType.WOOD))  return new Color(34, 120, 34);
        if (resourceType.equals(ResourceType.BRICK)) return new Color(180, 70, 50);
        if (resourceType.equals(ResourceType.WOOL))  return new Color(130, 210, 100);
        if (resourceType.equals(ResourceType.WHEAT)) return new Color(218, 165, 32);
        if (resourceType.equals(ResourceType.ORE))   return new Color(130, 130, 140);
        return new Color(210, 185, 130); // desert
    }

    // look up player color by index
    private Color getPlayerColor(Player p) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i) == p) {
                return PLAYER_COLORS[i % PLAYER_COLORS.length];
            }
        }
        return Color.GRAY; // fallback, shouldnt happen
    }

    // builds sidebar panel + inits playerLabels[]
    public JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(245, 222, 179));
        sidebar.setPreferredSize(new Dimension(210, 620));
        sidebar.setBorder(BorderFactory.createEmptyBorder(12, 10, 10, 10));

        JLabel title = new JLabel("Players");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(title);
        sidebar.add(Box.createVerticalStrut(12));

        playerLabels = new JLabel[players.size()];
        for (int i = 0; i < players.size(); i++) {
            playerLabels[i] = new JLabel(buildPlayerText(i));
            playerLabels[i].setForeground(PLAYER_COLORS[i % PLAYER_COLORS.length]);
            playerLabels[i].setFont(new Font("Monospaced", Font.PLAIN, 12));
            playerLabels[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            sidebar.add(playerLabels[i]);
            sidebar.add(Box.createVerticalStrut(16));
        }

        // color legend at the bottom
        JLabel legend = new JLabel(buildLegend());
        legend.setFont(new Font("Monospaced", Font.PLAIN, 11));
        legend.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(legend);

        return sidebar;
    }

    // refresh player labels after any state change
    public void updateSidebar() {
        if (playerLabels == null) return;
        for (int i = 0; i < players.size(); i++) {
            playerLabels[i].setText(buildPlayerText(i));
        }
    }

    // html text for one player: name, resources, vp
    private String buildPlayerText(int i) {
        Player p = players.get(i);
        return "<html><b>" + p.getName() + "</b><br>"
             + "Wood:  " + p.getWood()  + "  Brick: " + p.getBrick() + "<br>"
             + "Wool:  " + p.getWool()  + "  Wheat: " + p.getWheat() + "<br>"
             + "Ore:   " + p.getOre()   + "<br>"
             + "VP: "    + p.getVictoryPoints() + "</html>";
    }

    // color key for sidebar
    private String buildLegend() {
        return "<html><b>Resources</b><br>"
             + "<font color='#227822'>&#9632;</font> Wood &nbsp;"
             + "<font color='#b44632'>&#9632;</font> Brick<br>"
             + "<font color='#82d264'>&#9632;</font> Wool &nbsp;"
             + "<font color='#daa520'>&#9632;</font> Wheat<br>"
             + "<font color='#828288'>&#9632;</font> Ore &nbsp;&nbsp;"
             + "<font color='#d2b982'>&#9632;</font> Desert</html>";
    }

    public void setMessage(String s) {
        message = s;
    }
}
