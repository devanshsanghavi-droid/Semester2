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
    private JButton skipRoadButton;

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
        endTurnButton = new JButton("End Turn");
        endTurnButton.setEnabled(false);
        skipRoadButton = new JButton("Skip Road");
        skipRoadButton.setVisible(false);

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

        skipRoadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (inSetupPhase && setupPlacingRoad) {
                    advanceSetupAfterRoad();
                }
            }
        });

        bottom.add(rollButton);
        bottom.add(endTurnButton);
        bottom.add(skipRoadButton);
        bottom.add(statusLabel);
        return bottom;
    }

    // roll dice, give resources, let them build
    public void takeTurn() {
        int roll = rollDice();
        distributeResources(roll);
        hasRolled = true;
        rollButton.setEnabled(false);
        endTurnButton.setEnabled(true);

        Player current = players.get(currentPlayerIndex);
        String rollMsg = current.getName() + " rolled " + roll + ". Click a vertex to build.";
        if (roll == 7) {
            // 7 = no resources in catan
            rollMsg = current.getName() + " rolled 7, no resources. Click to build.";
        }
        updateStatus(rollMsg);
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
        if (inSetupPhase) {
            if (!setupPlacingRoad) {
                handleSetupSettlement(mouseX, mouseY);
            }
            // road placement handled by Feature 2; skip road button used until then
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

        p.deductResources(1, 1, 1, 1, 0);
        Settlement s = new Settlement(p, v);
        v.placeBuilding(s);
        p.addSettlement(s);
        return true;
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
        skipRoadButton.setVisible(true);
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
        skipRoadButton.setVisible(false);
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
