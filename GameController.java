import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.ArrayList;

// runs the game - turns, dice, building, win check
// has main(), run this
public class GameController {

    // the board
    private GameBoard board;

    // all players
    private ArrayList<Player> players;

    // drawing panel
    private GameView view;

    // whos turn (index into players)
    private int currentPlayerIndex;

    // true aftr p rolls, reset when turn done
    private boolean hasRolled;

    // true when somone hits 10vp, locks everything
    private boolean gameOver;

    // snake order for 2-player setup: P1, P2, P2, P1
    private static final int[] SETUP_ORDER = {0, 1, 1, 0};

    // true while players are placing starting settlements/roads
    private boolean inSetupPhase;

    // which step in SETUP_ORDER we're on (0-3)
    private int setupTurnIndex;

    // true after a setup settlement is placed, waiting for road
    private boolean setupPlacingRoad;

    // buttons
    private JButton rollButton;
    private JButton endTurnButton;

    // true while player is clicking road endpoints
    private boolean placingRoad;
    private Vertex roadFirstClick;

    // true while player is clicking a settlement to upgrade
    private boolean buildingCity;

    // true while player must click a tile to place robber
    private boolean movingRobber;

    // shows roll result / errors etc
    private JLabel statusLabel;

    // frame and card layout kept for intro -> game swap
    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel cardPanel;

    public static void main(String[] args) {
        GameController gc = new GameController();
        gc.showIntro();
    }

    // init board + 2 players
    public GameController() {
        board = new GameBoard();
        players = new ArrayList<Player>();
        players.add(new Player("Player 1"));
        players.add(new Player("Player 2"));
        currentPlayerIndex = 0;
        hasRolled = false;
        gameOver = false;
        inSetupPhase = false;
        setupTurnIndex = 0;
        setupPlacingRoad = false;
        placingRoad = false;
        roadFirstClick = null;
        buildingCity = false;
        movingRobber = false;
        view = new GameView(board, players);
    }

    // show intro crawl screen first
    public void showIntro() {
        frame = new JFrame("Settlers of Catan");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        IntroCrawlPanel intro = new IntroCrawlPanel(this);
        cardPanel.add(intro, "intro");

        // build game panel now so it's ready when intro ends
        JPanel gamePanel = buildGamePanel();
        cardPanel.add(gamePanel, "game");

        frame.add(cardPanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        cardLayout.show(cardPanel, "intro");
        intro.startCrawl();
    }

    // called by IntroCrawlPanel when crawl ends or key is pressed
    public void showGame() {
        cardLayout.show(cardPanel, "game");
        inSetupPhase = true;
        setupTurnIndex = 0;
        setupPlacingRoad = false;
        currentPlayerIndex = SETUP_ORDER[0];
        rollButton.setEnabled(false);
        endTurnButton.setEnabled(false);
        updateStatus(players.get(currentPlayerIndex).getName() + ": place your first settlement.");
        view.updateSidebar();
        view.repaint();
    }

    // build the game panel (center board + sidebar + bottom)
    private JPanel buildGamePanel() {
        JPanel gamePanel = new JPanel(new BorderLayout());

        JPanel sidebar = view.createSidebar();
        gamePanel.add(view, BorderLayout.CENTER);
        gamePanel.add(sidebar, BorderLayout.EAST);
        gamePanel.add(buildBottomPanel(), BorderLayout.SOUTH);

        view.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (!gameOver && (hasRolled || inSetupPhase)) {
                    handleBoardClick(e.getX(), e.getY());
                }
            }
        });

        return gamePanel;
    }

    // give starting resources then show window
    public void startGame() {
        showIntro();
    }

    // bottom bar: roll btn, end turn btn, status label
    private JPanel buildBottomPanel() {
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        bottom.setBackground(new Color(40, 40, 60));

        rollButton = new JButton("Roll Dice");
        JButton buildRoadButton = new JButton("Build Road");
        endTurnButton = new JButton("End Turn");
        endTurnButton.setEnabled(false);

        statusLabel = new JLabel("Player 1's turn");
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 13));

        // no lambdas allowed so anon classes
        rollButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!hasRolled && !gameOver) {
                    takeTurn();
                }
            }
        });

        endTurnButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!gameOver) {
                    endTurn();
                }
            }
        });

        buildRoadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (hasRolled && !gameOver) {
                    placingRoad = true;
                    updateStatus("Click first vertex of road.");
                } else {
                    updateStatus("Roll dice first.");
                }
            }
        });

        JButton buildCityButton = new JButton("Build City");
        buildCityButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (hasRolled && !gameOver) {
                    buildingCity = true;
                    updateStatus("Click a settlement to upgrade.");
                } else {
                    updateStatus("Roll dice first.");
                }
            }
        });

        bottom.add(rollButton);
        bottom.add(buildRoadButton);
        bottom.add(buildCityButton);
        bottom.add(endTurnButton);
        bottom.add(statusLabel);
        return bottom;
    }

    // roll dice, give resources, let them build
    public void takeTurn() {
        int roll = rollDice();
        hasRolled = true;
        rollButton.setEnabled(false);
        endTurnButton.setEnabled(true);

        if (roll == 7) {
            for (Player p : players) {
                int total = p.getWood() + p.getBrick() + p.getWool() + p.getWheat() + p.getOre();
                if (total > 7) {
                    showDiscardDialog(p, total / 2);
                }
            }
            movingRobber = true;
            updateStatus("Click a tile to move the robber.");
        } else {
            distributeResources(roll);
            updateStatus(players.get(currentPlayerIndex).getName() + " rolled " + roll + ". Click a vertex to build.");
        }
        view.updateSidebar();
        view.repaint();
    }

    // next players turn
    private void endTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        hasRolled = false;
        rollButton.setEnabled(true);
        endTurnButton.setEnabled(false);
        Player next = players.get(currentPlayerIndex);
        updateStatus(next.getName() + "'s turn: roll the dice!");
        view.updateSidebar();
        view.repaint();
    }

    // find closest vertex to click, try to build there
    private void handleBoardClick(int mouseX, int mouseY) {
        if (movingRobber) {
            Tile closest = null;
            int minDist = Integer.MAX_VALUE;
            for (Tile t : board.getTiles()) {
                int[] xp = t.getXPoints();
                int[] yp = t.getYPoints();
                int cx = 0, cy = 0;
                for (int i = 0; i < 6; i++) { cx += xp[i]; cy += yp[i]; }
                cx /= 6; cy /= 6;
                int dx = cx - mouseX;
                int dy = cy - mouseY;
                int dist = dx * dx + dy * dy;
                if (dist < minDist) { minDist = dist; closest = t; }
            }
            for (Tile t : board.getTiles()) {
                if (t.hasRobber()) t.setHasRobber(false);
            }
            closest.setHasRobber(true);
            Player current = players.get(currentPlayerIndex);
            Player victim = null;
            Vertex[] tileVerts = closest.getVertices();
            outer:
            for (Player p : players) {
                if (p == current) continue;
                for (Building b : p.getSettlements()) {
                    for (Vertex tv : tileVerts) {
                        if (tv == b.getLocation()) { victim = p; break outer; }
                    }
                }
            }
            if (victim != null) {
                String[] types = {ResourceType.WOOD, ResourceType.BRICK,
                                  ResourceType.WOOL, ResourceType.WHEAT, ResourceType.ORE};
                for (int attempt = 0; attempt < 10; attempt++) {
                    String type = types[(int)(Math.random() * 5)];
                    if (getCount(victim, type) > 0) {
                        victim.removeResource(type);
                        current.addResource(type);
                        break;
                    }
                }
            }
            movingRobber = false;
            view.updateSidebar();
            view.repaint();
            updateStatus("Robber moved.");
            return;
        }

        if (inSetupPhase) {
            if (!setupPlacingRoad) {
                handleSetupSettlement(mouseX, mouseY);
            } else {
                Vertex nearest = null;
                int minDist = Integer.MAX_VALUE;
                for (Vertex v : board.getVertices()) {
                    int dx = v.getX() - mouseX;
                    int dy = v.getY() - mouseY;
                    int dist = dx * dx + dy * dy;
                    if (dist < minDist) { minDist = dist; nearest = v; }
                }
                if (nearest == null || minDist > 400) return;
                if (roadFirstClick == null) {
                    roadFirstClick = nearest;
                    updateStatus("Click second vertex of road.");
                } else {
                    placeSetupRoad(players.get(currentPlayerIndex), roadFirstClick, nearest);
                    roadFirstClick = null;
                }
            }
            return;
        }

        if (placingRoad) {
            Vertex nearest = null;
            int minDist = Integer.MAX_VALUE;
            for (Vertex v : board.getVertices()) {
                int dx = v.getX() - mouseX;
                int dy = v.getY() - mouseY;
                int dist = dx * dx + dy * dy;
                if (dist < minDist) { minDist = dist; nearest = v; }
            }
            if (nearest == null || minDist > 400) return;
            if (roadFirstClick == null) {
                roadFirstClick = nearest;
                updateStatus("Click second vertex of road.");
            } else {
                placeRoad(players.get(currentPlayerIndex), roadFirstClick, nearest);
                placingRoad = false;
                roadFirstClick = null;
            }
            return;
        }

        if (buildingCity) {
            Vertex nearest = null;
            int minDist = Integer.MAX_VALUE;
            for (Vertex v : board.getVertices()) {
                int dx = v.getX() - mouseX;
                int dy = v.getY() - mouseY;
                int dist = dx * dx + dy * dy;
                if (dist < minDist) { minDist = dist; nearest = v; }
            }
            buildingCity = false;
            if (nearest == null || minDist > 400) return;
            Player current = players.get(currentPlayerIndex);
            if (nearest.isEmpty()) {
                updateStatus("No building there.");
            } else if (!(nearest.getBuilding() instanceof Settlement)) {
                updateStatus("Already a city.");
            } else if (nearest.getBuilding().getOwner() != current) {
                updateStatus("Not your settlement.");
            } else if (!current.canAfford(0, 0, 0, 2, 3)) {
                updateStatus("Need 2 wheat and 3 ore.");
            } else {
                current.deductResources(0, 0, 0, 2, 3);
                ((Settlement) nearest.getBuilding()).upgrade();
                updateStatus("City built.");
                view.updateSidebar();
                view.repaint();
            }
            return;
        }

        Vertex nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (Vertex v : board.getVertices()) {
            int dx = v.getX() - mouseX;
            int dy = v.getY() - mouseY;
            int dist = dx * dx + dy * dy;
            if (dist < minDist) {
                minDist = dist;
                nearest = v;
            }
        }

        // only if within 20px (20^2=400)
        if (nearest == null || minDist > 400) return;

        Player current = players.get(currentPlayerIndex);
        boolean placed = placeSettlement(current, nearest);

        if (placed) {
            updateStatus(current.getName() + " built a settlement!");
            if (checkWin()) {
                updateStatus(current.getName() + " wins with "
                             + current.getVictoryPoints() + " VP!");
                gameOver = true;
                rollButton.setEnabled(false);
                endTurnButton.setEnabled(false);
            }
        } else {
            // tell them why it failed
            if (!nearest.isEmpty()) {
                updateStatus("That spot is already taken.");
            } else if (!isValidPlacement(nearest)) {
                updateStatus("Too close to another settlement.");
            } else {
                updateStatus("Not enough resources. Need: 1 Wood, 1 Brick, 1 Wool, 1 Wheat.");
            }
        }

        view.updateSidebar();
        view.repaint();
    }

    // place settlement at v for player p
    // checks: empty, distance rule, can afford
    public boolean placeSettlement(Player p, Vertex v) {
        if (!v.isEmpty()) return false;
        if (!isValidPlacement(v)) return false;
        if (!p.canAfford(1, 1, 1, 1, 0)) return false;

        if (!p.deductResources(1, 1, 1, 1, 0)) {
            updateStatus("Not enough resources.");
            return false;
        }
        Settlement s = new Settlement(p, v);
        v.placeBuilding(s);
        p.addSettlement(s);
        return true;
    }

    // place road for p between v1 and v2; checks shared edge, no duplicate, cost
    private void placeRoad(Player p, Vertex v1, Vertex v2) {
        if (!sharesEdge(v1, v2)) {
            updateStatus("Invalid road location.");
            return;
        }
        for (Road r : board.getRoads()) {
            if ((r.getV1() == v1 && r.getV2() == v2) || (r.getV1() == v2 && r.getV2() == v1)) {
                updateStatus("A road already exists there.");
                return;
            }
        }
        if (!p.canAfford(1, 1, 0, 0, 0)) {
            updateStatus("Not enough resources. Need: 1 Wood, 1 Brick.");
            return;
        }
        p.deductResources(1, 1, 0, 0, 0);
        Road road = new Road(p, v1, v2);
        board.getRoads().add(road);
        p.getRoads().add(road);
        updateStatus("Road built.");
        view.updateSidebar();
        view.repaint();
    }

    // free road placement during setup; only checks shared edge rule
    private void placeSetupRoad(Player p, Vertex v1, Vertex v2) {
        if (!sharesEdge(v1, v2)) {
            updateStatus("Invalid road location.");
            return;
        }
        Road road = new Road(p, v1, v2);
        board.getRoads().add(road);
        p.getRoads().add(road);
        advanceSetupAfterRoad();
        view.repaint();
    }

    // true if v1 and v2 are adjacent corners on the same tile
    private boolean sharesEdge(Vertex v1, Vertex v2) {
        for (Tile t : board.getTiles()) {
            Vertex[] corners = t.getVertices();
            int idx1 = -1, idx2 = -1;
            for (int i = 0; i < 6; i++) {
                if (corners[i] == v1) idx1 = i;
                if (corners[i] == v2) idx2 = i;
            }
            if (idx1 != -1 && idx2 != -1) {
                int diff = Math.abs(idx1 - idx2);
                if (diff == 1 || diff == 5) return true;
            }
        }
        return false;
    }

    // dist rule - no adj corner of v can have a building
    // adj = next to it on same tile
    private boolean isValidPlacement(Vertex v) {
        for (Tile t : v.getAdjacentTiles()) {
            Vertex[] corners = t.getVertices();
            for (int i = 0; i < 6; i++) {
                if (corners[i] == v) {
                    Vertex prev = corners[(i + 5) % 6];
                    Vertex next = corners[(i + 1) % 6];
                    if (!prev.isEmpty() || !next.isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // show modal dialog forcing player p to discard n cards
    private void showDiscardDialog(final Player p, final int n) {
        JDialog dialog = new JDialog(frame, p.getName() + " must discard " + n + " cards", true);
        dialog.setLayout(new FlowLayout());

        final String[] types  = {ResourceType.WOOD, ResourceType.BRICK,
                                  ResourceType.WOOL, ResourceType.WHEAT, ResourceType.ORE};
        final String[] labels = {"Wood", "Brick", "Wool", "Wheat", "Ore"};
        final int[] discarded = {0};
        final JButton[] buttons = new JButton[5];

        for (int i = 0; i < 5; i++) {
            if (getCount(p, types[i]) > 0) {
                final int idx = i;
                buttons[i] = new JButton(labels[i] + " (" + getCount(p, types[i]) + ")");
                buttons[i].addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        p.removeResource(types[idx]);
                        discarded[0]++;
                        int remaining = getCount(p, types[idx]);
                        buttons[idx].setText(labels[idx] + " (" + remaining + ")");
                        if (remaining == 0) buttons[idx].setEnabled(false);
                        if (discarded[0] >= n) dialog.dispose();
                    }
                });
                dialog.add(buttons[i]);
            }
        }

        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    // count of a named resource for player p
    private int getCount(Player p, String type) {
        if (type.equals(ResourceType.WOOD))  return p.getWood();
        if (type.equals(ResourceType.BRICK)) return p.getBrick();
        if (type.equals(ResourceType.WOOL))  return p.getWool();
        if (type.equals(ResourceType.WHEAT)) return p.getWheat();
        if (type.equals(ResourceType.ORE))   return p.getOre();
        return 0;
    }

    // 2 dice, return sum
    public int rollDice() {
        int d1 = (int) (Math.random() * 6) + 1;
        int d2 = (int) (Math.random() * 6) + 1;
        return d1 + d2;
    }

    // give resources for all tiles matching the roll
    public void distributeResources(int roll) {
        ArrayList<Tile> active = board.getTilesByRoll(roll);
        for (Tile t : active) {
            t.distribute(players);
        }
    }

    // anyone at 10+ vp?
    public boolean checkWin() {
        for (Player p : players) {
            if (p.getVictoryPoints() >= 10) {
                return true;
            }
        }
        return false;
    }

    // free settlement placement during setup — no cost, snake order
    private void handleSetupSettlement(int mouseX, int mouseY) {
        Vertex nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (Vertex v : board.getVertices()) {
            int dx = v.getX() - mouseX;
            int dy = v.getY() - mouseY;
            int dist = dx * dx + dy * dy;
            if (dist < minDist) {
                minDist = dist;
                nearest = v;
            }
        }
        if (nearest == null || minDist > 400) return;

        Player current = players.get(currentPlayerIndex);
        if (!nearest.isEmpty()) {
            updateStatus("That spot is already taken.");
            view.repaint();
            return;
        }
        if (!isValidPlacement(nearest)) {
            updateStatus("Too close to another settlement.");
            view.repaint();
            return;
        }

        Settlement s = new Settlement(current, nearest);
        nearest.placeBuilding(s);
        current.addSettlement(s);

        // second pass (index >= 2): give 1 of each adjacent non-desert resource
        if (setupTurnIndex >= players.size()) {
            distributeSetupResources(nearest);
        }

        setupPlacingRoad = true;
        String turn = (setupTurnIndex < players.size()) ? "first" : "second";
        updateStatus(current.getName() + ": place your " + turn + " road (or click 'Skip Road').");
        view.updateSidebar();
        view.repaint();
    }

    // give current player 1 of each resource from tiles touching vertex v
    private void distributeSetupResources(Vertex v) {
        Player current = players.get(currentPlayerIndex);
        for (Tile t : v.getAdjacentTiles()) {
            if (!t.getResourceType().equals("DESERT")) {
                current.addResource(t.getResourceType());
            }
        }
    }

    // advance setup after a road is placed (or skipped)
    private void advanceSetupAfterRoad() {
        setupPlacingRoad = false;
        setupTurnIndex++;
        if (setupTurnIndex >= SETUP_ORDER.length) {
            inSetupPhase = false;
            currentPlayerIndex = 0;
            rollButton.setEnabled(true);
            updateStatus(players.get(0).getName() + "'s turn: roll the dice!");
        } else {
            currentPlayerIndex = SETUP_ORDER[setupTurnIndex];
            String turn = (setupTurnIndex < players.size()) ? "first" : "second";
            updateStatus(players.get(currentPlayerIndex).getName()
                         + ": place your " + turn + " settlement.");
        }
        view.updateSidebar();
        view.repaint();
    }

    // update bottom label + board msg at same time
    private void updateStatus(String msg) {
        statusLabel.setText(msg);
        view.setMessage(msg);
    }
}
