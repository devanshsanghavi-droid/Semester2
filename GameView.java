import java.awt.*;
import javax.swing.*;
import java.util.ArrayList;

// draws board and all ui panels (log on left sidebar on right)
// owns the JTextArea game log so GameController can apend to it
public class GameView extends JPanel {

    // board data, tiles vertices roads
    private GameBoard board;

    // need player list 2 look up colors nd build sidebar labels
    private ArrayList<Player> players;

    // msg drawn at botom of hex board
    private String message;

    // index of whose turn it is, only that player sees their resorce counts
    private int currentPlayerIndex;

    // sidebar labels one per player, refreshed on updateSidebar()
    private JLabel[] playerLabels;

    // scrollble game log text area on left side
    private JTextArea logArea;

    // player colors: blue p1, red bot/p2, orange p3, purpel p4
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
        setBackground(new Color(65, 105, 225)); // ocean blue
    }

    // main draw method: tiles then vertices then roads then status msg
    // order matters, roads go on top of tiles
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

        // roads drawn aftr vertices so they apear on top of empty dots
        // settlmnts still draw over roads bc vertices come last... wait no
        // roads draw AFTER vertices here which means roads r on top
        // looks fine in practice tho since roads dont overlap settlments much
        Graphics2D g2 = (Graphics2D) g;
        ArrayList<Road> roads = board.getRoads();
        for (int i = 0; i < roads.size(); i++) {
            Road r = roads.get(i);
            g2.setStroke(new BasicStroke(4));
            g2.setColor(getPlayerColor(r.getOwner()));
            g2.drawLine(r.getV1().getX(), r.getV1().getY(), r.getV2().getX(), r.getV2().getY());
        }
        g2.setStroke(new BasicStroke(1)); // reset stroke or everything else gets thik

        // status msg at very botom of board area
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 13));
        g.drawString(message, 10, getHeight() - 8);
    }

    // draw one hex tile: bg color, black outline, dice num, robber circle
    private void drawTile(Graphics g, Tile t) {
        g.setColor(getTileColor(t.getResourceType()));
        g.fillPolygon(t.getXPoints(), t.getYPoints(), 6);

        g.setColor(Color.BLACK);
        g.drawPolygon(t.getXPoints(), t.getYPoints(), 6);

        // find center by averging the 6 corner coords
        int cx = 0, cy = 0;
        for (int x : t.getXPoints()) cx += x;
        for (int y : t.getYPoints()) cy += y;
        cx /= 6;
        cy /= 6;

        // draw dice num, red 4 6 and 8 cuz those r the hot numbres
        if (t.getDiceNumber() > 0) {
            g.setColor((t.getDiceNumber() == 6 || t.getDiceNumber() == 8) ? Color.RED : Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 18));
            FontMetrics fm = g.getFontMetrics();
            String num = "" + t.getDiceNumber();
            g.drawString(num, cx - fm.stringWidth(num) / 2, cy + fm.getAscent() / 2 - 2);
        }

        // robber = black filled circle w white outline so visible on dark tiles
        if (t.hasRobber()) {
            g.setColor(Color.BLACK);
            g.fillOval(cx - 10, cy + 12, 20, 20);
            g.setColor(Color.WHITE);
            g.drawOval(cx - 10, cy + 12, 20, 20);
        }
    }

    // empty vertex = faint white dot; settlment = colored circle; city = colored square
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
                // city = square, bigger
                g.fillRect(v.getX() - 9, v.getY() - 9, 18, 18);
                g.setColor(Color.BLACK);
                g.drawRect(v.getX() - 9, v.getY() - 9, 18, 18);
            } else {
                // settlment = circle
                g.fillOval(v.getX() - 8, v.getY() - 8, 16, 16);
                g.setColor(Color.BLACK);
                g.drawOval(v.getX() - 8, v.getY() - 8, 16, 16);
            }
        }
    }

    // map resorce type string 2 a color 4 hex background
    private Color getTileColor(String resourceType) {
        if (resourceType.equals(ResourceType.WOOD))  return new Color(34, 120, 34);
        if (resourceType.equals(ResourceType.BRICK)) return new Color(180, 70, 50);
        if (resourceType.equals(ResourceType.WOOL))  return new Color(130, 210, 100);
        if (resourceType.equals(ResourceType.WHEAT)) return new Color(218, 165, 32);
        if (resourceType.equals(ResourceType.ORE))   return new Color(130, 130, 140);
        return new Color(210, 185, 130); // desert tan
    }

    // look up player color by looping players list
    private Color getPlayerColor(Player p) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i) == p) return PLAYER_COLORS[i % PLAYER_COLORS.length];
        }
        return Color.GRAY; // shuldnt happen but just in case
    }

    // build right sidebar showing each players stats
    // playerLabels[] gets populated here nd updated in updateSidebar()
    public JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(245, 222, 179)); // sandy color
        sidebar.setPreferredSize(new Dimension(210, 620));
        sidebar.setBorder(BorderFactory.createEmptyBorder(12, 10, 10, 10));

        JLabel title = new JLabel("Players");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(title);
        sidebar.add(Box.createVerticalStrut(12));

        playerLabels = new JLabel[players.size()];
        for (int i = 0; i < players.size(); i++) {
            playerLabels[i] = new JLabel(buildPlayerText(i, i == currentPlayerIndex));
            playerLabels[i].setForeground(PLAYER_COLORS[i % PLAYER_COLORS.length]);
            playerLabels[i].setFont(new Font("Monospaced", Font.PLAIN, 11));
            playerLabels[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            sidebar.add(playerLabels[i]);
            sidebar.add(Box.createVerticalStrut(16));
        }

        // resorce color key at botom of sidebar
        JLabel legend = new JLabel(buildLegend());
        legend.setFont(new Font("Monospaced", Font.PLAIN, 11));
        legend.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(legend);

        return sidebar;
    }

    // builds dark game log panel goes on left side of board
    // creates JTextArea nd wraps it in scroll pane
    public JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(30, 30, 50));
        panel.setPreferredSize(new Dimension(175, 620));

        JLabel title = new JLabel(" Game Log");
        title.setForeground(Color.LIGHT_GRAY);
        title.setFont(new Font("Arial", Font.BOLD, 13));
        title.setBackground(new Color(20, 20, 40));
        title.setOpaque(true);
        panel.add(title, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBackground(new Color(30, 30, 50));
        logArea.setForeground(new Color(200, 200, 200));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(null);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // apend line 2 game log nd auto-scroll to botom
    // called from GameController.addLog()
    public void addLog(String msg) {
        if (logArea == null) return;
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength()); // scroll to botom
    }

    // tell view which player is active so sidebar privacy works
    public void setCurrentPlayerIndex(int i) {
        currentPlayerIndex = i;
    }

    // refresh all player labels, called after any state change afecting sidebar
    public void updateSidebar() {
        if (playerLabels == null) return;
        for (int i = 0; i < players.size(); i++) {
            playerLabels[i].setText(buildPlayerText(i, i == currentPlayerIndex));
        }
    }

    // build html string 4 one players sidebar entry
    // current player sees full resorce counts, others see "hidden"
    // also shows vp dev card count roads/settlments/cities knights bonus card flags
    private String buildPlayerText(int i, boolean isCurrentPlayer) {
        Player p = players.get(i);
        // show [Army] and [Road] tags if player holds those bonus cards
        String armyTag = p.isHoldsLargestArmy() ? " [Army]" : "";
        String roadTag = p.isHoldsLongestRoad()  ? " [Road]"  : "";

        if (!isCurrentPlayer) {
            // other player, hide resorces but show everything else
            return "<html><b>" + p.getName() + "</b>" + armyTag + roadTag + "<br>"
                 + "VP: " + p.getVictoryPoints() + "<br>"
                 + "Resources: hidden<br>"
                 + "Dev cards: " + p.getDevCardCount() + "<br>"
                 + "Roads: " + p.getRoads().size()
                 + "  S: " + p.getSettlementCount()
                 + "  C: " + p.getCityCount() + "<br>"
                 + "Knights: " + p.getKnightsPlayed()
                 + "</html>";
        }
        // current player, show everything
        return "<html><b>" + p.getName() + "</b>" + armyTag + roadTag + "<br>"
             + "VP: " + p.getVictoryPoints() + "<br>"
             + "Wood: "  + p.getWood()  + "  Brick: " + p.getBrick() + "<br>"
             + "Wool: "  + p.getWool()  + "  Wheat: " + p.getWheat() + "<br>"
             + "Ore: "   + p.getOre()   + "<br>"
             + "Dev cards: " + p.getDevCardCount() + "<br>"
             + "Roads: " + p.getRoads().size()
             + "  S: " + p.getSettlementCount()
             + "  C: " + p.getCityCount() + "<br>"
             + "Knights: " + p.getKnightsPlayed()
             + "</html>";
    }

    // resorce color key 4 botom of sidebar
    private String buildLegend() {
        return "<html><b>Resources</b><br>"
             + "<font color='#227822'>&#9632;</font> Wood &nbsp;"
             + "<font color='#b44632'>&#9632;</font> Brick<br>"
             + "<font color='#82d264'>&#9632;</font> Wool &nbsp;"
             + "<font color='#daa520'>&#9632;</font> Wheat<br>"
             + "<font color='#828288'>&#9632;</font> Ore &nbsp;&nbsp;"
             + "<font color='#d2b982'>&#9632;</font> Desert</html>";
    }

    public void setMessage(String s) { message = s; }
}
