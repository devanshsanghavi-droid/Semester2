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

    // offset applied 2 board drawing so it stays centered no matter panel size
    // recalculated every paint, read by GameController 2 adjust mouse coords
    private int boardOffsetX = 0;
    private int boardOffsetY = 0;

    // player colors: bright so they pop against any tile color
    private static final Color[] PLAYER_COLORS = {
        new Color(30, 144, 255),   // dodger blue
        new Color(220, 20,  60),   // crimson red
        new Color(255, 140,  0),   // dark orange
        new Color(148,  0, 211)    // violet
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
    // board coords r hardcoded around (350,350) so we translate g2d 2 keep it centered
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // board hardcoded center is (350,350), shift so it lands at panel center
        boardOffsetX = getWidth()  / 2 - 350;
        boardOffsetY = getHeight() / 2 - 350;

        Graphics2D g2 = (Graphics2D) g;
        g2.translate(boardOffsetX, boardOffsetY);

        ArrayList<Tile> tiles = board.getTiles();
        for (int i = 0; i < tiles.size(); i++) {
            drawTile(g, tiles.get(i));
        }

        ArrayList<Vertex> verts = board.getVertices();
        for (int i = 0; i < verts.size(); i++) {
            drawVertex(g, verts.get(i));
        }

        // roads drawn aftr vertices
        // white outline drawn first so road pops against any tile color
        ArrayList<Road> roads = board.getRoads();
        for (int i = 0; i < roads.size(); i++) {
            Road r = roads.get(i);
            int x1 = r.getV1().getX(), y1 = r.getV1().getY();
            int x2 = r.getV2().getX(), y2 = r.getV2().getY();
            g2.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Color.WHITE);
            g2.drawLine(x1, y1, x2, y2);
            g2.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(getPlayerColor(r.getOwner()));
            g2.drawLine(x1, y1, x2, y2);
        }
        g2.setStroke(new BasicStroke(1)); // reset or everything else gets thik

        // undo translation before drawing status (panel-relative coords)
        g2.translate(-boardOffsetX, -boardOffsetY);

        // status bar: big centered text w dark pill bg so its readable over any tile color
        g2.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics sfm = g2.getFontMetrics();
        int sw   = sfm.stringWidth(message);
        int sx   = (getWidth() - sw) / 2;
        int sy   = getHeight() - 16;
        int padX = 18, padY = 8;

        // semi-transparent dark rounded rect behind text
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(sx - padX, sy - sfm.getAscent() - padY,
                         sw + padX * 2, sfm.getHeight() + padY * 2, 16, 16);

        g2.setColor(Color.WHITE);
        g2.drawString(message, sx, sy);
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

    // empty vertex = faint dot; settlment = colored circle w white halo; city = colored square w white halo
    // white outline drawn 1st so pieces contrast against any tile color
    private void drawVertex(Graphics g, Vertex v) {
        if (v.isEmpty()) {
            g.setColor(new Color(255, 255, 255, 60));
            g.fillOval(v.getX() - 5, v.getY() - 5, 10, 10);
            g.setColor(new Color(255, 255, 255, 120));
            g.drawOval(v.getX() - 5, v.getY() - 5, 10, 10);
        } else {
            Building b = v.getBuilding();
            Color c = getPlayerColor(b.getOwner());
            if (b instanceof City) {
                // white halo first
                g.setColor(Color.WHITE);
                g.fillRect(v.getX() - 12, v.getY() - 12, 24, 24);
                // colored fill
                g.setColor(c);
                g.fillRect(v.getX() - 10, v.getY() - 10, 20, 20);
                // dark outline
                g.setColor(new Color(30, 30, 30));
                g.drawRect(v.getX() - 10, v.getY() - 10, 20, 20);
            } else {
                // white halo first
                g.setColor(Color.WHITE);
                g.fillOval(v.getX() - 11, v.getY() - 11, 22, 22);
                // colored fill
                g.setColor(c);
                g.fillOval(v.getX() - 9, v.getY() - 9, 18, 18);
                // dark outline
                g.setColor(new Color(30, 30, 30));
                g.drawOval(v.getX() - 9, v.getY() - 9, 18, 18);
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
        sidebar.setPreferredSize(new Dimension(250, 620));
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
            playerLabels[i].setFont(new Font("Monospaced", Font.PLAIN, 14));
            playerLabels[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            sidebar.add(playerLabels[i]);
            sidebar.add(Box.createVerticalStrut(30));
        }

        // resorce color key at botom of sidebar
        JLabel legend = new JLabel(buildLegend());
        legend.setFont(new Font("Monospaced", Font.PLAIN, 13));
        legend.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(legend);

        return sidebar;
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

    // GameController reads these 2 convert raw mouse coords 2 board coords
    public int getBoardOffsetX() { return boardOffsetX; }
    public int getBoardOffsetY() { return boardOffsetY; }
}
