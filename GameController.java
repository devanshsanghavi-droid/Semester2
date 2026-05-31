import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.ArrayList;

// main game class, handles turn flow dice all placements dev cards trading bot win check
// basically everything thats not just drawing goes here
// has main() so run this one
public class GameController {

    // board holds tiles vertices roads dev deck
    private GameBoard board;

    // both players, index 0 = human index 1 = bot
    private ArrayList<Player> players;

    // the drawing panel in center
    private GameView view;

    // index into players[], whos turn it is
    private int currentPlayerIndex;

    // true after roll, reset at end of turn
    private boolean hasRolled;

    // true when somebody hits 10vp, locks everything
    private boolean gameOver;

    // snake order 4 2-player setup: P1 P2 P2 P1
    private static final int[] SETUP_ORDER = {0, 1, 1, 0};
    private boolean inSetupPhase;
    private int setupTurnIndex;
    private boolean setupPlacingRoad; // true after settlment placed waiting 4 road click

    // two-click road placement: store first click wait 4 second
    private boolean placingRoad;
    private Vertex roadFirstClick;

    // true while player is clicking which settlment 2 upgrd
    private boolean buildingCity;

    // true while player needs 2 click tile 4 robber (after 7 or knight)
    private boolean movingRobber;

    // dev card play state
    private boolean hasPlayedDevCard;   // only 1 card per turn
    private boolean roadBuildingActive; // true while placing 2 free roads from road bldg card
    private int roadBuildingRoadsLeft;  // counts down from 2 to 0

    // who holds bonus cards rn, -1 = nobody has it yet
    private int largestArmyHolder;
    private int longestRoadHolder;

    // store indiviual die values so we can show "3 + 4 = 7" insted of just 7
    private int lastDie1;
    private int lastDie2;

    // player 2 is always bot, flip this false 4 2 humans
    private boolean botEnabled;
    private int botStep; // which step bot state machine is on

    // buttons stored as fields so we can enable/disable from anywhere
    // rule: only Roll Dice active before roll, all others active after roll
    private JButton rollButton;
    private JButton endTurnButton;
    private JButton buildRoadButton;
    private JButton buildCityButton;
    private JButton buyDevCardButton;
    private JButton playDevCardButton;
    private JButton offerTradeButton;
    private JButton tradeButton;

    // status bar label at botom + transition screen label
    private JLabel statusLabel;
    private JLabel transitionLabel;

    // frame + cardlayout 4 intro -> game -> transition panels
    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel cardPanel;

    public static void main(String[] args) {
        GameController gc = new GameController();
        gc.showIntro();
    }

    // set up everything at 0/false, bot is player 2
    public GameController() {
        board = new GameBoard();
        players = new ArrayList<Player>();
        players.add(new Player("Player 1"));
        players.add(new Player("Bot"));
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
        hasPlayedDevCard = false;
        roadBuildingActive = false;
        roadBuildingRoadsLeft = 0;
        largestArmyHolder = -1;
        longestRoadHolder = -1;
        lastDie1 = 0;
        lastDie2 = 0;
        botEnabled = true;
        botStep = 0;
        view = new GameView(board, players);
    }

    // show star wars style intro crawl first then game
    public void showIntro() {
        frame = new JFrame("Settlers of Catan");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        IntroCrawlPanel intro = new IntroCrawlPanel(this);
        cardPanel.add(intro, "intro");

        // build game panel ahead of time so theres no delay when intro ends
        JPanel gamePanel = buildGamePanel();
        cardPanel.add(gamePanel, "game");

        // black transition panel shown between players (pass-the-device moment)
        JPanel transPanel = buildTransitionPanel();
        cardPanel.add(transPanel, "transition");

        frame.add(cardPanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        cardLayout.show(cardPanel, "intro");
        intro.startCrawl();
    }

    // called by IntroCrawlPanel when crawl finishes or player skips
    public void showGame() {
        cardLayout.show(cardPanel, "game");
        inSetupPhase = true;
        setupTurnIndex = 0;
        setupPlacingRoad = false;
        currentPlayerIndex = SETUP_ORDER[0]; // always starts with player 1
        rollButton.setEnabled(false);
        endTurnButton.setEnabled(false);
        setPostRollButtons(false); // nothing available until you roll
        updateStatus(players.get(currentPlayerIndex).getName() + ": place your first settlement.");
        view.setCurrentPlayerIndex(currentPlayerIndex);
        view.updateSidebar();
        view.repaint();
    }

    // assemble main game panel: log on left board in center sidebar on right btns on botom
    private JPanel buildGamePanel() {
        JPanel gamePanel = new JPanel(new BorderLayout());

        JPanel logPanel = view.createLogPanel();
        JPanel sidebar  = view.createSidebar();

        gamePanel.add(view,     BorderLayout.CENTER);
        gamePanel.add(logPanel, BorderLayout.WEST);
        gamePanel.add(sidebar,  BorderLayout.EAST);
        gamePanel.add(buildBottomPanel(), BorderLayout.SOUTH);

        // mouse clicks on board panel only (not sidebar or log)
        // allow clicks during setup nd during movingRobber even before hasRolled
        view.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (!gameOver && (hasRolled || inSetupPhase || movingRobber)) {
                    handleBoardClick(e.getX(), e.getY());
                }
            }
        });

        return gamePanel;
    }

    // black panel w big centered text, shows between turns so other player cant see cards
    private JPanel buildTransitionPanel() {
        JPanel panel = new JPanel(new GridBagLayout()); // gridbaglayout centers stuff automaticly
        panel.setBackground(Color.BLACK);
        panel.setPreferredSize(new Dimension(1100, 660));

        transitionLabel = new JLabel("...");
        transitionLabel.setForeground(Color.WHITE);
        transitionLabel.setFont(new Font("Arial", Font.BOLD, 32));
        panel.add(transitionLabel);

        return panel;
    }

    // show black screen 4 3 seconds then come back 2 game for next player
    // if bot is next we skip transition nd just go straight
    private void showTransition(final int nextIndex) {
        // update state now so when timer fires evrything is already set up
        currentPlayerIndex = nextIndex;
        hasRolled = false;
        rollButton.setEnabled(true);
        endTurnButton.setEnabled(false);
        setPostRollButtons(false);
        players.get(currentPlayerIndex).clearBoughtThisTurn();
        hasPlayedDevCard = false;

        String name = players.get(nextIndex).getName();
        transitionLabel.setText(name + " - don't look!");
        cardLayout.show(cardPanel, "transition");

        // single-fire timer, fires once after 3 seconds then stops itself
        Timer t = new Timer(3000, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((Timer) e.getSource()).stop();
                cardLayout.show(cardPanel, "game");
                view.setCurrentPlayerIndex(currentPlayerIndex);
                view.updateSidebar();
                updateStatus(players.get(currentPlayerIndex).getName() + "'s turn, roll the dice!");
                view.repaint();
            }
        });
        t.setRepeats(false);
        t.start();
    }

    // build botom button bar
    // all build/trade/dev buttons disabled until player rolls
    // roll dice disabled after rolling, re-enabled at start of next turn
    private JPanel buildBottomPanel() {
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        bottom.setBackground(new Color(40, 40, 60));

        rollButton      = new JButton("Roll Dice");
        buildRoadButton = new JButton("Build Road");
        JButton buildSettlementButton = new JButton("Build Settlement");
        buildCityButton   = new JButton("Build City");
        buyDevCardButton  = new JButton("Buy Dev Card");
        playDevCardButton = new JButton("Play Dev Card");
        offerTradeButton  = new JButton("Offer Trade");
        tradeButton       = new JButton("Bank Trade");
        endTurnButton     = new JButton("End Turn");
        JButton infoButton = new JButton("?"); // info popup

        endTurnButton.setEnabled(false);

        statusLabel = new JLabel("Welcome!");
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 13));

        // no lambdas per assignment rules so all anon classes
        rollButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!hasRolled && !gameOver && !inSetupPhase) takeTurn();
            }
        });

        endTurnButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!gameOver) endTurn();
            }
        });

        buildRoadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!gameOver) {
                    placingRoad = true;
                    roadFirstClick = null;
                    updateStatus("Click first vertex of road.");
                }
            }
        });

        // clicking Build Settlement just prompts user 2 click board
        // actual placement happens in handleBoardClick when no mode is active
        buildSettlementButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!gameOver) updateStatus("Click a vertex to place your settlement.");
            }
        });

        buildCityButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!gameOver) {
                    buildingCity = true;
                    updateStatus("Click a settlement to upgrade.");
                }
            }
        });

        buyDevCardButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!gameOver) buyDevCard();
            }
        });

        playDevCardButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // cant play more than one card per turn
                if (!gameOver && !hasPlayedDevCard) openPlayDevCardDialog();
            }
        });

        offerTradeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!gameOver) openOfferTradeDialog();
            }
        });

        tradeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!gameOver) openBankTradeDialog();
            }
        });

        infoButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showInfoDialog();
            }
        });

        bottom.add(rollButton);
        bottom.add(buildRoadButton);
        bottom.add(buildSettlementButton);
        bottom.add(buildCityButton);
        bottom.add(buyDevCardButton);
        bottom.add(playDevCardButton);
        bottom.add(offerTradeButton);
        bottom.add(tradeButton);
        bottom.add(endTurnButton);
        bottom.add(infoButton);
        bottom.add(statusLabel);

        return bottom;
    }

    // flip all action buttons on or off at once
    // called true after roll, false at turn start nd after game over
    private void setPostRollButtons(boolean enabled) {
        buildRoadButton.setEnabled(enabled);
        buildCityButton.setEnabled(enabled);
        buyDevCardButton.setEnabled(enabled);
        playDevCardButton.setEnabled(enabled);
        offerTradeButton.setEnabled(enabled);
        tradeButton.setEnabled(enabled);
    }

    // ---- TURN FLOW ----

    // roll both dice, handle 7 (robber) vs normal (distribute resorces)
    // logs indiviual die values like "3 + 4 = 7"
    public void takeTurn() {
        lastDie1 = (int)(Math.random() * 6) + 1;
        lastDie2 = (int)(Math.random() * 6) + 1;
        int roll = lastDie1 + lastDie2;

        hasRolled = true;
        rollButton.setEnabled(false);
        endTurnButton.setEnabled(true);
        setPostRollButtons(true);

        String rollMsg = players.get(currentPlayerIndex).getName()
                       + " rolled " + lastDie1 + " + " + lastDie2 + " = " + roll;
        addLog(rollMsg);

        if (roll == 7) {
            // anyone w more than 7 cards has 2 discard half (rounded down)
            for (Player p : players) {
                int total = p.getWood() + p.getBrick() + p.getWool() + p.getWheat() + p.getOre();
                if (total > 7) showDiscardDialog(p, total / 2);
            }
            movingRobber = true;
            updateStatus(rollMsg + ", move the robber!");
        } else {
            // snapshot before so we can log exactly what each player gained
            int[] before = resourceSnapshot();
            distributeResources(roll);
            logResourceGains(before);
            updateStatus(rollMsg + ". Build something!");
        }

        view.setCurrentPlayerIndex(currentPlayerIndex);
        view.updateSidebar();
        view.repaint();
    }

    // end current players turn, check longest road, then either transition or hand 2 bot
    private void endTurn() {
        checkLongestRoad(); // recalc now in case roads changed this turn
        int nextIndex = (currentPlayerIndex + 1) % players.size();

        addLog(players.get(currentPlayerIndex).getName() + " ended their turn.");

        if (botEnabled && nextIndex == 1) {
            // bot skips transition screen, just log and start bot timer chain
            currentPlayerIndex = nextIndex;
            hasRolled = false;
            rollButton.setEnabled(false);
            endTurnButton.setEnabled(false);
            setPostRollButtons(false);
            players.get(currentPlayerIndex).clearBoughtThisTurn();
            hasPlayedDevCard = false;
            view.setCurrentPlayerIndex(currentPlayerIndex);
            view.updateSidebar();
            updateStatus("Bot is thinking...");
            addLog("--- Bot's turn ---");
            view.repaint();
            botStep = 0;
            scheduleBotStep(800); // kick off state machine
        } else {
            showTransition(nextIndex); // human turn: show black screen first
        }
    }

    // ---- BOARD CLICK HANDLER ----

    // single entry point 4 all board clicks
    // checks modes in order: robber > setup > road > city > normal settlment
    private void handleBoardClick(int mouseX, int mouseY) {

        // robber always takes priority over everything else
        if (movingRobber) {
            // find hex closest 2 where they clicked
            Tile closest = null;
            int minDist = Integer.MAX_VALUE;
            for (Tile t : board.getTiles()) {
                int[] xp = t.getXPoints(), yp = t.getYPoints();
                int cx = 0, cy = 0;
                for (int i = 0; i < 6; i++) { cx += xp[i]; cy += yp[i]; }
                cx /= 6; cy /= 6; // avg corners 2 get center
                int dist = (cx - mouseX) * (cx - mouseX) + (cy - mouseY) * (cy - mouseY);
                if (dist < minDist) { minDist = dist; closest = t; }
            }
            // cant put robber back where it already is
            if (closest.hasRobber()) {
                updateStatus("Robber is already there. Pick a different tile.");
                return; // dont reset movingRobber, let them try again
            }
            // move robber: clear old set new
            for (Tile t : board.getTiles()) { if (t.hasRobber()) t.setHasRobber(false); }
            closest.setHasRobber(true);

            // steal random resorce from first opponent found on that tile
            Player current = players.get(currentPlayerIndex);
            Player victim = findVictim(closest, current);
            if (victim != null) {
                String stolen = stealRandom(victim, current);
                if (stolen != null) {
                    addLog(current.getName() + " stole 1 " + stolen + " from " + victim.getName());
                }
            }
            movingRobber = false;
            addLog(current.getName() + " moved the robber.");
            updateStatus("Robber moved.");
            view.setCurrentPlayerIndex(currentPlayerIndex);
            view.updateSidebar();
            view.repaint();
            return;
        }

        // setup phase: free settlments nd roads in snake order
        if (inSetupPhase) {
            if (!setupPlacingRoad) {
                handleSetupSettlement(mouseX, mouseY);
            } else {
                // two-click road placement during setup (free, just edge check)
                Vertex nearest = findNearestVertex(mouseX, mouseY);
                if (nearest == null) return;
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

        // road placement mode: active after clicking Build Road or playing road bldg card
        if (placingRoad) {
            Vertex nearest = findNearestVertex(mouseX, mouseY);
            if (nearest == null) return;
            if (roadFirstClick == null) {
                roadFirstClick = nearest;
                updateStatus("Click second vertex of road.");
            } else {
                // road bldg card = free road, normal = paid road
                if (roadBuildingActive) {
                    placeRoadFree(players.get(currentPlayerIndex), roadFirstClick, nearest);
                } else {
                    placeRoad(players.get(currentPlayerIndex), roadFirstClick, nearest);
                }
                placingRoad = false;
                roadFirstClick = null;
            }
            return;
        }

        // city upgrd mode: active after clicking Build City
        if (buildingCity) {
            Vertex nearest = findNearestVertex(mouseX, mouseY);
            buildingCity = false; // always reset even if click fails
            if (nearest == null) return;
            Player current = players.get(currentPlayerIndex);
            if (nearest.isEmpty()) {
                updateStatus("No building there.");
            } else if (!(nearest.getBuilding() instanceof Settlement)) {
                updateStatus("Already a city.");
            } else if (nearest.getBuilding().getOwner() != current) {
                updateStatus("Not your settlement.");
            } else if (!current.canAfford(0, 0, 0, 2, 3)) {
                updateStatus("Need 2 Wheat + 3 Ore.");
            } else {
                current.deductResources(0, 0, 0, 2, 3);
                ((Settlement) nearest.getBuilding()).upgrade(); // settlment.upgrade() handles the swap
                addLog(current.getName() + " upgraded to a city.");
                updateStatus("City built!");
                checkWinAndHandle(); // city gives +1 vp might win
                view.setCurrentPlayerIndex(currentPlayerIndex);
                view.updateSidebar();
                view.repaint();
            }
            return;
        }

        // default: try place settlment wherever they clicked
        // no dedicated mode needed, any unhandled click attempts a settlment
        if (!hasRolled) return;
        Vertex nearest = findNearestVertex(mouseX, mouseY);
        if (nearest == null) return;
        Player current = players.get(currentPlayerIndex);
        if (placeSettlement(current, nearest)) {
            addLog(current.getName() + " built a settlement.");
            updateStatus(current.getName() + " built a settlement!");
            checkLongestRoad(); // new settlment might break opponents road
            checkWinAndHandle();
        } else {
            // give specific error so player knows why it failed
            if (!nearest.isEmpty()) {
                updateStatus("That spot is taken.");
            } else if (!isValidPlacement(nearest)) {
                updateStatus("Too close to another settlement.");
            } else if (!isConnectedToRoad(current, nearest)) {
                updateStatus("Must be at the end of your road.");
            } else {
                updateStatus("Need: 1 Wood, 1 Brick, 1 Wool, 1 Wheat.");
            }
        }
        view.setCurrentPlayerIndex(currentPlayerIndex);
        view.updateSidebar();
        view.repaint();
    }

    // find vertex closest 2 click within 20px (20^2 = 400 squared dist)
    // returns null if nothing close enuf
    private Vertex findNearestVertex(int mouseX, int mouseY) {
        Vertex best = null;
        int minDist = Integer.MAX_VALUE;
        for (Vertex v : board.getVertices()) {
            int dx = v.getX() - mouseX, dy = v.getY() - mouseY;
            int dist = dx * dx + dy * dy;
            if (dist < minDist) { minDist = dist; best = v; }
        }
        return (best == null || minDist > 400) ? null : best;
    }

    // ---- PLACEMENT HELPERS ----

    // setup settlment: free, no connectivity check, just distance rule
    private void handleSetupSettlement(int mouseX, int mouseY) {
        Vertex nearest = findNearestVertex(mouseX, mouseY);
        if (nearest == null) return;
        Player current = players.get(currentPlayerIndex);
        if (!nearest.isEmpty()) {
            updateStatus("That spot is taken."); view.repaint(); return;
        }
        if (!isValidPlacement(nearest)) {
            updateStatus("Too close to another settlement."); view.repaint(); return;
        }
        Settlement s = new Settlement(current, nearest);
        nearest.placeBuilding(s);
        current.addSettlement(s);
        // only give starting resorces 4 2nd settlment (setupTurnIndex >= 2 for 2 players)
        if (setupTurnIndex >= players.size()) distributeSetupResources(nearest);
        setupPlacingRoad = true;
        String turn = (setupTurnIndex < players.size()) ? "first" : "second";
        addLog(current.getName() + " placed " + turn + " settlement.");
        updateStatus(current.getName() + ": place your " + turn + " road.");
        view.setCurrentPlayerIndex(currentPlayerIndex);
        view.updateSidebar();
        view.repaint();
    }

    // give current player 1 of each resorce from tiles touching vertex v
    // only called during setup 4 second settlment placement
    private void distributeSetupResources(Vertex v) {
        Player current = players.get(currentPlayerIndex);
        for (Tile t : v.getAdjacentTiles()) {
            if (!t.getResourceType().equals("DESERT")) {
                current.addResource(t.getResourceType());
            }
        }
    }

    // setup road: just checks shared edge, no cost no connectivity req
    private void placeSetupRoad(Player p, Vertex v1, Vertex v2) {
        if (!sharesEdge(v1, v2)) {
            updateStatus("Not a valid road edge."); return;
        }
        Road road = new Road(p, v1, v2);
        board.getRoads().add(road);
        p.getRoads().add(road);
        addLog(p.getName() + " placed a road.");
        advanceSetupAfterRoad();
        view.repaint();
    }

    // move to next step in setup order after road placed
    // once all 4 setup turns done, unlock roll btn nd start normal play
    private void advanceSetupAfterRoad() {
        setupPlacingRoad = false;
        setupTurnIndex++;
        if (setupTurnIndex >= SETUP_ORDER.length) {
            // setup done! player 1 always goes first
            inSetupPhase = false;
            currentPlayerIndex = 0;
            rollButton.setEnabled(true);
            updateStatus(players.get(0).getName() + "'s turn, roll the dice!");
        } else {
            // next player in snake order
            currentPlayerIndex = SETUP_ORDER[setupTurnIndex];
            String turn = (setupTurnIndex < players.size()) ? "first" : "second";
            updateStatus(players.get(currentPlayerIndex).getName()
                       + ": place your " + turn + " settlement.");
        }
        view.setCurrentPlayerIndex(currentPlayerIndex);
        view.updateSidebar();
        view.repaint();
    }

    // paid settlment placement: distance rule + connectivity + cost
    // returns false if any check fails (caller handles error msg)
    public boolean placeSettlement(Player p, Vertex v) {
        if (!v.isEmpty()) return false;
        if (!isValidPlacement(v)) return false;
        if (!isConnectedToRoad(p, v)) return false; // must be at end of own road
        if (!p.deductResources(1, 1, 1, 1, 0)) return false;
        Settlement s = new Settlement(p, v);
        v.placeBuilding(s);
        p.addSettlement(s);
        return true;
    }

    // paid road placement: shared edge + no duplicate + connected 2 network + cost
    private void placeRoad(Player p, Vertex v1, Vertex v2) {
        if (!sharesEdge(v1, v2)) {
            updateStatus("Not a valid road edge."); return;
        }
        if (isDuplicateRoad(v1, v2)) {
            updateStatus("Road already exists there."); return;
        }
        if (!isConnectedRoad(p, v1, v2)) {
            updateStatus("Road must connect to your network."); return;
        }
        if (!p.canAfford(1, 1, 0, 0, 0)) {
            updateStatus("Need 1 Wood + 1 Brick."); return;
        }
        p.deductResources(1, 1, 0, 0, 0);
        Road road = new Road(p, v1, v2);
        board.getRoads().add(road);
        p.getRoads().add(road);
        addLog(p.getName() + " built a road.");
        updateStatus("Road built.");
        checkLongestRoad();
        view.setCurrentPlayerIndex(currentPlayerIndex);
        view.updateSidebar();
        view.repaint();

        // if road bldg card is active, prompt 4 next road
        if (roadBuildingActive) {
            roadBuildingRoadsLeft--;
            if (roadBuildingRoadsLeft > 0) {
                placingRoad = true;
                updateStatus("Road Building: place road " + (3 - roadBuildingRoadsLeft) + " of 2.");
            } else {
                roadBuildingActive = false;
            }
        }
    }

    // free road from road bldg card: same checks as paid but no cost
    private void placeRoadFree(Player p, Vertex v1, Vertex v2) {
        if (!sharesEdge(v1, v2)) {
            updateStatus("Not a valid road edge."); return;
        }
        if (isDuplicateRoad(v1, v2)) {
            updateStatus("Road already exists there."); return;
        }
        if (!isConnectedRoad(p, v1, v2)) {
            updateStatus("Road must connect to your network."); return;
        }
        Road road = new Road(p, v1, v2);
        board.getRoads().add(road);
        p.getRoads().add(road);
        addLog(p.getName() + " built a free road.");
        checkLongestRoad();
        view.setCurrentPlayerIndex(currentPlayerIndex);
        view.updateSidebar();
        view.repaint();

        roadBuildingRoadsLeft--;
        if (roadBuildingRoadsLeft > 0) {
            placingRoad = true;
            updateStatus("Road Building: place road 2 of 2.");
        } else {
            roadBuildingActive = false;
            updateStatus("Road Building complete.");
        }
    }

    // ---- VALIDATION ----

    // distance rule: none of adjacent corners on any shared tile can have bldg
    private boolean isValidPlacement(Vertex v) {
        for (Tile t : v.getAdjacentTiles()) {
            Vertex[] corners = t.getVertices();
            for (int i = 0; i < 6; i++) {
                if (corners[i] == v) {
                    // check 2 vertices directly next 2 this one on hex
                    if (!corners[(i + 5) % 6].isEmpty() || !corners[(i + 1) % 6].isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // true if v1 and v2 r adjacent (diff 1 or 5 in index) on any shared tile
    // diff of 5 handles wrap-around case (index 0 and 5 r neighbors)
    private boolean sharesEdge(Vertex v1, Vertex v2) {
        for (Tile t : board.getTiles()) {
            Vertex[] corners = t.getVertices();
            int i1 = -1, i2 = -1;
            for (int i = 0; i < 6; i++) {
                if (corners[i] == v1) i1 = i;
                if (corners[i] == v2) i2 = i;
            }
            if (i1 != -1 && i2 != -1) {
                int diff = Math.abs(i1 - i2);
                if (diff == 1 || diff == 5) return true;
            }
        }
        return false;
    }

    // true if road already connects v1 and v2 in either direction
    private boolean isDuplicateRoad(Vertex v1, Vertex v2) {
        for (Road r : board.getRoads()) {
            if ((r.getV1() == v1 && r.getV2() == v2) || (r.getV1() == v2 && r.getV2() == v1)) {
                return true;
            }
        }
        return false;
    }

    // true if at least one of v1/v2 touches one of p's existing roads or bldgs
    // this is connectivity rule that prevents roads floating in middle of nowhere
    private boolean isConnectedRoad(Player p, Vertex v1, Vertex v2) {
        for (Road r : p.getRoads()) {
            if (r.getV1() == v1 || r.getV2() == v1 || r.getV1() == v2 || r.getV2() == v2) {
                return true;
            }
        }
        for (Building b : p.getSettlements()) {
            if (b.getLocation() == v1 || b.getLocation() == v2) return true;
        }
        return false;
    }

    // true if vertex v is endpoint of any of p's roads
    // used 4 settlment placement: must build at end of own road
    private boolean isConnectedToRoad(Player p, Vertex v) {
        for (Road r : p.getRoads()) {
            if (r.getV1() == v || r.getV2() == v) return true;
        }
        return false;
    }

    // ---- RESOURCE DISTRIBUTION ----

    // give each player resorces 4 tiles matching this roll num
    public void distributeResources(int roll) {
        for (Tile t : board.getTilesByRoll(roll)) {
            t.distribute(players); // tile.distribute handles robber nd city double-count
        }
    }

    // take snapshot of all resorce counts before distribution
    // used with logResourceGains 2 log what changed
    private int[] resourceSnapshot() {
        int[] snap = new int[players.size() * 5];
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            snap[i * 5 + 0] = p.getWood();
            snap[i * 5 + 1] = p.getBrick();
            snap[i * 5 + 2] = p.getWool();
            snap[i * 5 + 3] = p.getWheat();
            snap[i * 5 + 4] = p.getOre();
        }
        return snap;
    }

    // compare current counts 2 snapshot nd log anything that went up
    private void logResourceGains(int[] before) {
        String[] names = {"Wood", "Brick", "Wool", "Wheat", "Ore"};
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            int[] cur = {p.getWood(), p.getBrick(), p.getWool(), p.getWheat(), p.getOre()};
            for (int j = 0; j < 5; j++) {
                int gain = cur[j] - before[i * 5 + j];
                if (gain > 0) addLog(p.getName() + " received " + gain + " " + names[j]);
            }
        }
    }

    // ---- ROBBER HELPERS ----

    // find first opponent player w bldg on any corner of tile t
    // returns null if no opponents r on that tile (no stealing happens)
    private Player findVictim(Tile t, Player attacker) {
        Vertex[] verts = t.getVertices();
        for (Player p : players) {
            if (p == attacker) continue;
            for (Building b : p.getSettlements()) {
                for (Vertex tv : verts) {
                    if (tv == b.getLocation()) return p;
                }
            }
        }
        return null;
    }

    // steal one randum resorce from victim nd give 2 thief
    // tries up 2 10 randum picks in case first few hit empty types
    // returns type stolen, or null if victim had nothing
    private String stealRandom(Player victim, Player thief) {
        String[] types = {ResourceType.WOOD, ResourceType.BRICK,
                          ResourceType.WOOL, ResourceType.WHEAT, ResourceType.ORE};
        for (int attempt = 0; attempt < 10; attempt++) {
            String type = types[(int)(Math.random() * 5)];
            if (getCount(victim, type) > 0) {
                victim.removeResource(type);
                thief.addResource(type);
                return type;
            }
        }
        return null; // victim was broke, nothing 2 steal
    }

    // ---- DEV CARDS ----

    // buy one dev card: costs 1 wool + 1 wheat + 1 ore
    // vp cards reveal immediately, all others go 2 hand nd cant be played this turn
    private void buyDevCard() {
        Player current = players.get(currentPlayerIndex);
        if (!current.canAfford(0, 0, 1, 1, 1)) {
            updateStatus("Need 1 Wool + 1 Wheat + 1 Ore."); return;
        }
        String card = board.drawDevCard();
        if (card == null) {
            updateStatus("No dev cards left."); return;
        }
        current.deductResources(0, 0, 1, 1, 1);
        if (card.equals(DevCard.VICTORY_POINT)) {
            // vp cards revealed right away, add 1 to victoryPoints via VPBuilding
            // use addSettlement(new VPBuilding) so vp field gets bumped same way as settlment
            current.addDevCard(card);
            current.addSettlement(new VPBuilding(current));
            addLog(current.getName() + " drew a Victory Point card! (+1 VP)");
            updateStatus("Victory Point card! +1 VP");
            checkWinAndHandle(); // vp card might push to 10
        } else {
            current.addDevCard(card);
            current.addBoughtThisTurn(card); // mark it so cant be played same turn
            addLog(current.getName() + " bought a dev card.");
            updateStatus("Dev card added to hand.");
        }
        view.setCurrentPlayerIndex(currentPlayerIndex);
        view.updateSidebar();
        view.repaint();
    }

    // open dialog listing playable cards (not bought this turn, not vp cards)
    // one btn per distinct card type showing how many r playable
    private void openPlayDevCardDialog() {
        Player current = players.get(currentPlayerIndex);
        String[] types = {DevCard.KNIGHT, DevCard.ROAD_BUILDING,
                          DevCard.YEAR_OF_PLENTY, DevCard.MONOPOLY};

        final JDialog dialog = new JDialog(frame, "Play Dev Card", true);
        dialog.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));

        boolean any = false;
        for (int i = 0; i < types.length; i++) {
            final String type = types[i];
            int count = current.playableCount(type);
            if (count > 0) {
                any = true;
                JButton btn = new JButton(type + " (" + count + ")");
                btn.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        dialog.dispose();
                        playCard(type); // dispatch 2 right handler
                    }
                });
                dialog.add(btn);
            }
        }
        if (!any) {
            dialog.add(new JLabel("No playable cards."));
        }
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    // route to right play method based on card type string
    private void playCard(String type) {
        if      (type.equals(DevCard.KNIGHT))          playKnight();
        else if (type.equals(DevCard.ROAD_BUILDING))   playRoadBuilding();
        else if (type.equals(DevCard.YEAR_OF_PLENTY))  playYearOfPlenty();
        else if (type.equals(DevCard.MONOPOLY))        playMonopoly();
    }

    // knight: move robber + possibly gain Largest Army
    private void playKnight() {
        Player current = players.get(currentPlayerIndex);
        current.removeDevCard(DevCard.KNIGHT);
        current.incrementKnights();
        hasPlayedDevCard = true;
        movingRobber = true; // clicking tile will now move robber
        addLog(current.getName() + " played Knight.");
        updateStatus("Knight: move the robber.");
        checkLargestArmy(); // 3+ knights might give army card
        view.setCurrentPlayerIndex(currentPlayerIndex);
        view.updateSidebar();
        view.repaint();
    }

    // road building: place 2 free roads
    // sets roadBuildingActive so placeRoadFree knows 2 count down
    private void playRoadBuilding() {
        Player current = players.get(currentPlayerIndex);
        current.removeDevCard(DevCard.ROAD_BUILDING);
        hasPlayedDevCard = true;
        roadBuildingActive = true;
        roadBuildingRoadsLeft = 2;
        placingRoad = true;
        roadFirstClick = null;
        addLog(current.getName() + " played Road Building.");
        updateStatus("Road Building: place road 1 of 2.");
    }

    // year of plenty: pick any 2 resorces from bank (can be same type)
    private void playYearOfPlenty() {
        final Player current = players.get(currentPlayerIndex);
        current.removeDevCard(DevCard.YEAR_OF_PLENTY);
        hasPlayedDevCard = true;

        String[] opts = {ResourceType.WOOD, ResourceType.BRICK,
                         ResourceType.WOOL, ResourceType.WHEAT, ResourceType.ORE};
        final JComboBox<String> box1 = new JComboBox<String>(opts);
        final JComboBox<String> box2 = new JComboBox<String>(opts);

        JPanel panel = new JPanel(new GridLayout(4, 1, 4, 4));
        panel.add(new JLabel("First resource:"));
        panel.add(box1);
        panel.add(new JLabel("Second resource:"));
        panel.add(box2);

        final JDialog dialog = new JDialog(frame, "Year of Plenty", true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(panel, BorderLayout.CENTER);

        JButton confirm = new JButton("Take");
        confirm.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String r1 = (String) box1.getSelectedItem();
                String r2 = (String) box2.getSelectedItem();
                current.addResource(r1);
                current.addResource(r2);
                addLog(current.getName() + " took 1 " + r1 + " and 1 " + r2 + " (Year of Plenty).");
                dialog.dispose();
                view.setCurrentPlayerIndex(currentPlayerIndex);
                view.updateSidebar();
            }
        });
        dialog.add(confirm, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
        updateStatus("Year of Plenty played.");
    }

    // monopoly: pick resorce type, take every single one from all opponents
    private void playMonopoly() {
        final Player current = players.get(currentPlayerIndex);
        current.removeDevCard(DevCard.MONOPOLY);
        hasPlayedDevCard = true;

        String[] opts = {ResourceType.WOOD, ResourceType.BRICK,
                         ResourceType.WOOL, ResourceType.WHEAT, ResourceType.ORE};
        final JComboBox<String> box = new JComboBox<String>(opts);

        final JDialog dialog = new JDialog(frame, "Monopoly", true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(new JLabel("Choose resource to monopolize:"), BorderLayout.NORTH);
        dialog.add(box, BorderLayout.CENTER);

        JButton confirm = new JButton("Take All");
        confirm.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String type = (String) box.getSelectedItem();
                int total = 0;
                for (Player p : players) {
                    if (p == current) continue;
                    int count = getCount(p, type);
                    for (int i = 0; i < count; i++) {
                        p.removeResource(type);
                        current.addResource(type);
                    }
                    total += count;
                }
                addLog(current.getName() + " monopolized " + type + " (took " + total + ").");
                dialog.dispose();
                view.setCurrentPlayerIndex(currentPlayerIndex);
                view.updateSidebar();
            }
        });
        dialog.add(confirm, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
        updateStatus("Monopoly played.");
    }

    // ---- TRADING ----

    // 4:1 bank trade: give 4 of one resorce, get 1 of any other
    private void openBankTradeDialog() {
        String[] opts = {ResourceType.WOOD, ResourceType.BRICK,
                         ResourceType.WOOL, ResourceType.WHEAT, ResourceType.ORE};
        final JComboBox<String> giveBox = new JComboBox<String>(opts);
        final JComboBox<String> getBox  = new JComboBox<String>(opts);
        final JLabel errorLabel = new JLabel(" ");

        JPanel panel = new JPanel(new GridLayout(5, 1, 4, 4));
        panel.add(new JLabel("Give (4):"));
        panel.add(giveBox);
        panel.add(new JLabel("Get (1):"));
        panel.add(getBox);
        panel.add(errorLabel);

        final JDialog dialog = new JDialog(frame, "Bank Trade", true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(panel, BorderLayout.CENTER);

        JButton confirm = new JButton("Confirm");
        confirm.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String give = (String) giveBox.getSelectedItem();
                String get  = (String) getBox.getSelectedItem();
                if (give.equals(get)) { errorLabel.setText("Cannot trade same resource."); return; }
                Player current = players.get(currentPlayerIndex);
                if (getCount(current, give) < 4) { errorLabel.setText("Need 4 " + give + "."); return; }
                // deduct 4 of given resorce (if-else bc no maps allowed per assignment)
                if      (give.equals(ResourceType.WOOD))  current.deductResources(4, 0, 0, 0, 0);
                else if (give.equals(ResourceType.BRICK)) current.deductResources(0, 4, 0, 0, 0);
                else if (give.equals(ResourceType.WOOL))  current.deductResources(0, 0, 4, 0, 0);
                else if (give.equals(ResourceType.WHEAT)) current.deductResources(0, 0, 0, 4, 0);
                else                                       current.deductResources(0, 0, 0, 0, 4);
                current.addResource(get);
                addLog(current.getName() + " traded 4 " + give + " for 1 " + get + " (bank).");
                view.setCurrentPlayerIndex(currentPlayerIndex);
                view.updateSidebar();
                dialog.dispose();
                updateStatus("Trade complete.");
            }
        });
        dialog.add(confirm, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    // player trade step 1: current player sets wat they offer nd wat they want
    // spinners max out at wat each player actually has
    private void openOfferTradeDialog() {
        final Player current = players.get(currentPlayerIndex);
        final Player other   = players.get(1 - currentPlayerIndex); // only 2 players

        String[] names = {"Wood", "Brick", "Wool", "Wheat", "Ore"};
        final JSpinner[] giveSpinners = new JSpinner[5];
        final JSpinner[] getSpinners  = new JSpinner[5];

        JPanel panel = new JPanel(new GridLayout(6, 3, 4, 4));
        panel.add(new JLabel("Resource")); panel.add(new JLabel("You give")); panel.add(new JLabel("You want"));
        for (int i = 0; i < 5; i++) {
            int maxGive = getCountByIndex(current, i);
            giveSpinners[i] = new JSpinner(new SpinnerNumberModel(0, 0, maxGive, 1));
            getSpinners[i]  = new JSpinner(new SpinnerNumberModel(0, 0, 20, 1));
            panel.add(new JLabel(names[i]));
            panel.add(giveSpinners[i]);
            panel.add(getSpinners[i]);
        }

        final JDialog offerDialog = new JDialog(frame, "Offer Trade to " + other.getName(), true);
        offerDialog.setLayout(new BorderLayout(8, 8));
        offerDialog.add(panel, BorderLayout.CENTER);

        JButton send = new JButton("Send Offer");
        send.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                offerDialog.dispose();
                // hand it 2 other player 2 accept or decline
                showReceiveTradeDialog(current, other, giveSpinners, getSpinners, names);
            }
        });
        offerDialog.add(send, BorderLayout.SOUTH);
        offerDialog.pack();
        offerDialog.setLocationRelativeTo(frame);
        offerDialog.setVisible(true);
    }

    // player trade step 2: other player sees full offer nd can accept or decline
    // if accepted, transfer resorces both directions nd check affordability first
    private void showReceiveTradeDialog(final Player giver, final Player receiver,
                                        final JSpinner[] giveSpinners, final JSpinner[] getSpinners,
                                        final String[] names) {
        // build summary of whats being offered so receiver knows wat they r agreeing 2
        StringBuilder sb = new StringBuilder("<html>" + giver.getName() + " offers:<br>");
        for (int i = 0; i < 5; i++) {
            int g = (Integer) giveSpinners[i].getValue();
            if (g > 0) sb.append("  Give " + g + " " + names[i] + "<br>");
        }
        sb.append("In exchange for:<br>");
        for (int i = 0; i < 5; i++) {
            int w = (Integer) getSpinners[i].getValue();
            if (w > 0) sb.append("  Get " + w + " " + names[i] + "<br>");
        }
        sb.append("</html>");

        final JDialog recDialog = new JDialog(frame, receiver.getName() + ": trade offer", true);
        recDialog.setLayout(new BorderLayout(8, 8));
        recDialog.add(new JLabel(sb.toString()), BorderLayout.CENTER);

        // single-element boolean array trick so inner class can modify it
        final boolean[] accepted = {false};

        JPanel btns = new JPanel();
        JButton acceptBtn  = new JButton("Accept");
        JButton declineBtn = new JButton("Decline");
        acceptBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                accepted[0] = true;
                recDialog.dispose();
            }
        });
        declineBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                recDialog.dispose();
            }
        });
        btns.add(acceptBtn);
        btns.add(declineBtn);
        recDialog.add(btns, BorderLayout.SOUTH);
        recDialog.pack();
        recDialog.setLocationRelativeTo(frame);
        recDialog.setVisible(true); // blocks until accept or decline

        if (accepted[0]) {
            // double check receiver can actually pay wat they agreed 2
            for (int i = 0; i < 5; i++) {
                int need = (Integer) getSpinners[i].getValue();
                if (getCountByIndex(receiver, i) < need) {
                    updateStatus(receiver.getName() + " doesn't have enough resources.");
                    addLog("Trade failed: " + receiver.getName() + " lacks resources.");
                    return;
                }
            }
            // execute: move resorces both ways
            for (int i = 0; i < 5; i++) {
                int give = (Integer) giveSpinners[i].getValue();
                int get  = (Integer) getSpinners[i].getValue();
                for (int k = 0; k < give; k++) { giver.removeResource(indexToResource(i)); receiver.addResource(indexToResource(i)); }
                for (int k = 0; k < get;  k++) { receiver.removeResource(indexToResource(i)); giver.addResource(indexToResource(i)); }
            }
            addLog(giver.getName() + " and " + receiver.getName() + " completed a trade.");
            updateStatus("Trade accepted!");
            view.setCurrentPlayerIndex(currentPlayerIndex);
            view.updateSidebar();
        } else {
            addLog(receiver.getName() + " declined the trade.");
            updateStatus("Trade declined.");
        }
    }

    // ---- LARGEST ARMY ----

    // check if current player shud get Largest Army card
    // first 2 play 3 knights gets it, can be stolen by playing more than current holder
    private void checkLargestArmy() {
        Player current = players.get(currentPlayerIndex);
        // need 3 2 claim if nobody has it, otherwise need 1 more than current holder
        int needed = (largestArmyHolder == -1) ? 3
                     : players.get(largestArmyHolder).getKnightsPlayed() + 1;
        if (current.getKnightsPlayed() >= needed && largestArmyHolder != currentPlayerIndex) {
            if (largestArmyHolder != -1) players.get(largestArmyHolder).setHoldsLargestArmy(false);
            current.setHoldsLargestArmy(true);
            largestArmyHolder = currentPlayerIndex;
            addLog(current.getName() + " has Largest Army! (+2 VP)");
            view.setCurrentPlayerIndex(currentPlayerIndex);
            view.updateSidebar();
            checkWinAndHandle(); // 2 extra vp might win game
        }
    }

    // ---- LONGEST ROAD ----

    // recalculate longest road 4 all players nd transfer card if needed
    // called after every road placed nd every settlment placed (settlments can break roads)
    private void checkLongestRoad() {
        int[] lengths = new int[players.size()];
        for (int i = 0; i < players.size(); i++) {
            lengths[i] = calculateLongestRoad(players.get(i));
        }

        // find who has best road length >= 5
        int bestLen = 4; // minimum threshold is 5 so start at 4
        int bestIdx = -1;
        for (int i = 0; i < players.size(); i++) {
            if (lengths[i] > bestLen) { bestLen = lengths[i]; bestIdx = i; }
        }

        if (bestIdx == -1) return; // nobody qualifes yet

        // official catan rule: ties dont transfer card, current holder keeps it
        if (longestRoadHolder != -1 && lengths[longestRoadHolder] == bestLen) return;

        if (bestIdx != longestRoadHolder) {
            if (longestRoadHolder != -1) players.get(longestRoadHolder).setHoldsLongestRoad(false);
            players.get(bestIdx).setHoldsLongestRoad(true);
            longestRoadHolder = bestIdx;
            addLog(players.get(bestIdx).getName() + " has Longest Road! (" + bestLen + " segments, +2 VP)");
            view.setCurrentPlayerIndex(currentPlayerIndex);
            view.updateSidebar();
            checkWinAndHandle();
        }
    }

    // DFS entry point: try starting from both endpoints of every road
    // returns longest continuous road length 4 player p
    private int calculateLongestRoad(Player p) {
        int max = 0;
        ArrayList<Road> visited = new ArrayList<Road>();
        for (Road startRoad : p.getRoads()) {
            visited.clear();
            int len = dfsRoad(p, startRoad.getV1(), visited);
            if (len > max) max = len;
            visited.clear();
            len = dfsRoad(p, startRoad.getV2(), visited);
            if (len > max) max = len;
        }
        return max;
    }

    // recursive DFS: from vertex v follow all unvisited roads owned by p
    // road is "broken" if destination vertex has opponent bldg on it
    // visited list acts as path stack (added going in, removed backtracking)
    private int dfsRoad(Player p, Vertex v, ArrayList<Road> visited) {
        int best = 0;
        for (Road r : p.getRoads()) {
            if (visited.contains(r)) continue; // dont reuse road in same path

            // find which end of this road connects 2 v
            Vertex other = null;
            if (r.getV1() == v) other = r.getV2();
            else if (r.getV2() == v) other = r.getV1();
            else continue; // this road doesnt touch v

            // opponent settlment at other vertex breaks road (cant pass thru)
            if (!other.isEmpty() && other.getBuilding().getOwner() != p) continue;

            visited.add(r);
            int len = 1 + dfsRoad(p, other, visited);
            if (len > best) best = len;
            visited.remove(visited.size() - 1); // backtrack
        }
        return best;
    }

    // ---- WIN ----

    // true if any player has reached 10 vp
    public boolean checkWin() {
        for (Player p : players) {
            if (p.getVictoryPoints() >= 10) return true;
        }
        return false;
    }

    // check 4 win nd if triggered, lock game nd show win screen
    private void checkWinAndHandle() {
        if (!checkWin()) return;
        gameOver = true;
        rollButton.setEnabled(false);
        endTurnButton.setEnabled(false);
        setPostRollButtons(false);

        // find who won (first player at 10+ vp)
        Player winner = null;
        for (Player p : players) {
            if (p.getVictoryPoints() >= 10) { winner = p; break; }
        }

        addLog(winner.getName() + " wins with " + winner.getVictoryPoints() + " VP!");
        showWinDialog(winner);
    }

    // win screen: big dialog w winner name final vp nd a quit btn
    // not closable except by quitting (DO_NOTHING_ON_CLOSE)
    private void showWinDialog(Player winner) {
        JDialog win = new JDialog(frame, "Game Over", true);
        win.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        win.setLayout(new GridLayout(3, 1, 8, 8));

        JLabel nameLabel = new JLabel(winner.getName() + " Wins!", JLabel.CENTER);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 36));

        JLabel vpLabel = new JLabel("Final VP: " + winner.getVictoryPoints(), JLabel.CENTER);
        vpLabel.setFont(new Font("Arial", Font.PLAIN, 20));

        JButton quitBtn = new JButton("Quit");
        quitBtn.setFont(new Font("Arial", Font.BOLD, 16));
        quitBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { System.exit(0); }
        });

        win.add(nameLabel);
        win.add(vpLabel);
        win.add(quitBtn);
        win.setSize(400, 240);
        win.setLocationRelativeTo(frame);
        win.setVisible(true);
    }

    // ---- DISCARD DIALOG ----

    // force player p 2 discard n cards (called when someone has >7 on a 7 roll)
    // shows one btn per resorce type they own, auto-closes when enuf discarded
    private void showDiscardDialog(final Player p, final int n) {
        final JDialog dialog = new JDialog(frame, p.getName() + " must discard " + n + " cards", true);
        dialog.setLayout(new FlowLayout());

        final String[] types  = {ResourceType.WOOD, ResourceType.BRICK,
                                  ResourceType.WOOL, ResourceType.WHEAT, ResourceType.ORE};
        final String[] labels = {"Wood", "Brick", "Wool", "Wheat", "Ore"};
        final int[] discarded = {0}; // array so inner class can modify it
        final JButton[] buttons = new JButton[5];

        for (int i = 0; i < 5; i++) {
            if (getCount(p, types[i]) > 0) {
                final int idx = i;
                buttons[i] = new JButton(labels[i] + " (" + getCount(p, types[i]) + ")");
                buttons[i].addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        p.removeResource(types[idx]);
                        discarded[0]++;
                        int rem = getCount(p, types[idx]);
                        buttons[idx].setText(labels[idx] + " (" + rem + ")"); // update count on btn
                        if (rem == 0) buttons[idx].setEnabled(false); // grey out when depleted
                        if (discarded[0] >= n) dialog.dispose(); // done!
                    }
                });
                dialog.add(buttons[i]);
            }
        }

        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true); // blocks until n cards discarded
        addLog(p.getName() + " discarded " + n + " cards.");
    }

    // ---- INFO DIALOG ----

    // ? btn popup w all costs nd vp sources
    private void showInfoDialog() {
        String msg = "<html><b>Building Costs</b><br>"
            + "Settlement: 1 Wood, 1 Brick, 1 Wool, 1 Wheat (1 VP)<br>"
            + "City: 2 Wheat, 3 Ore (2 VP, replaces settlement)<br>"
            + "Road: 1 Wood, 1 Brick<br>"
            + "Dev Card: 1 Wool, 1 Wheat, 1 Ore<br><br>"
            + "<b>VP Sources</b><br>"
            + "Settlement = 1 VP<br>"
            + "City = 2 VP<br>"
            + "Longest Road (5+ connected) = 2 VP<br>"
            + "Largest Army (3+ knights played) = 2 VP<br>"
            + "Victory Point card = 1 VP<br><br>"
            + "<b>First to 10 VP wins!</b></html>";
        JOptionPane.showMessageDialog(frame, new JLabel(msg), "How to Play", JOptionPane.PLAIN_MESSAGE);
    }

    // ---- BOT ----

    // fire one-shot timer that calls runBotStep() after delayMs milliseconds
    // chaining these creates illusion bot is "thinking"
    private void scheduleBotStep(int delayMs) {
        Timer t = new Timer(delayMs, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((Timer) e.getSource()).stop();
                runBotStep();
            }
        });
        t.setRepeats(false);
        t.start();
    }

    // bot turn state machine, botStep increments each call
    // order: roll -> robber -> knight -> city -> settlment -> road -> buy card -> end turn
    private void runBotStep() {
        Player bot = players.get(currentPlayerIndex);
        botStep++;

        if (botStep == 1) {
            // always roll first
            takeTurn();
            if (movingRobber) { scheduleBotStep(800); return; } // rolled 7, handle robber next
            scheduleBotStep(800);
            return;
        }

        if (botStep == 2 && movingRobber) {
            // bot rolled 7 needs 2 move robber
            botMoveRobber();
            scheduleBotStep(800);
            return;
        }

        if (botStep == 3) {
            // play knight if available (helps build toward largest army)
            if (bot.playableCount(DevCard.KNIGHT) > 0 && !hasPlayedDevCard) {
                playKnight();
                if (movingRobber) { scheduleBotStep(800); return; } // knight also moves robber
            }
            scheduleBotStep(800);
            return;
        }

        if (botStep == 4 && movingRobber) {
            // robber from playing knight
            botMoveRobber();
            scheduleBotStep(800);
            return;
        }

        if (botStep == 5) {
            // upgrd 2 city if possible, most efficent use of resorces
            if (bot.canAfford(0, 0, 0, 2, 3)) {
                for (Building b : bot.getSettlements()) {
                    if (b instanceof Settlement) {
                        bot.deductResources(0, 0, 0, 2, 3);
                        ((Settlement) b).upgrade();
                        addLog("Bot upgraded to a city.");
                        updateStatus("Bot built a city.");
                        checkWinAndHandle();
                        view.setCurrentPlayerIndex(currentPlayerIndex);
                        view.updateSidebar();
                        view.repaint();
                        break; // only upgrd one per turn
                    }
                }
            }
            scheduleBotStep(800);
            return;
        }

        if (botStep == 6) {
            // build settlment at any valid connected vertex
            if (bot.canAfford(1, 1, 1, 1, 0)) {
                Vertex best = findBotSettlementSpot(bot);
                if (best != null && placeSettlement(bot, best)) {
                    addLog("Bot built a settlement.");
                    updateStatus("Bot built a settlement.");
                    checkLongestRoad();
                    checkWinAndHandle();
                    view.setCurrentPlayerIndex(currentPlayerIndex);
                    view.updateSidebar();
                    view.repaint();
                }
            }
            scheduleBotStep(800);
            return;
        }

        if (botStep == 7) {
            // build road 2 expand network
            if (bot.canAfford(1, 1, 0, 0, 0)) {
                boolean built = tryBotBuildRoad(bot);
                if (built) {
                    view.setCurrentPlayerIndex(currentPlayerIndex);
                    view.updateSidebar();
                    view.repaint();
                }
            }
            scheduleBotStep(800);
            return;
        }

        if (botStep == 8) {
            // buy dev card if affordable, knight spam solid strategy
            if (bot.canAfford(0, 0, 1, 1, 1)) {
                buyDevCard();
            }
            scheduleBotStep(600);
            return;
        }

        // step 9+: done, end turn
        endTurn();
    }

    // bot robber logic: target tile w most opponent bldgs on it
    // then steal randum resorce from whoever is there
    private void botMoveRobber() {
        Player bot = players.get(currentPlayerIndex);
        Tile best = null;
        int bestCount = -1;
        for (Tile t : board.getTiles()) {
            if (t.hasRobber()) continue; // cant stay in same spot
            int count = 0;
            for (Vertex v : t.getVertices()) {
                if (!v.isEmpty() && v.getBuilding().getOwner() != bot) count++;
            }
            if (count > bestCount) { bestCount = count; best = t; }
        }
        if (best == null) {
            // fallback: just pick any tile that doesnt have robber already
            for (Tile t : board.getTiles()) { if (!t.hasRobber()) { best = t; break; } }
        }
        for (Tile t : board.getTiles()) { if (t.hasRobber()) t.setHasRobber(false); }
        best.setHasRobber(true);
        Player victim = findVictim(best, bot);
        if (victim != null) {
            String stolen = stealRandom(victim, bot);
            if (stolen != null) addLog("Bot stole 1 " + stolen + " from " + victim.getName());
        }
        movingRobber = false;
        addLog("Bot moved the robber.");
        view.repaint();
    }

    // find vertex connected 2 bots road network that passes distance rule
    private Vertex findBotSettlementSpot(Player bot) {
        for (Road r : bot.getRoads()) {
            Vertex[] ends = {r.getV1(), r.getV2()};
            for (Vertex v : ends) {
                if (v.isEmpty() && isValidPlacement(v) && isConnectedToRoad(bot, v)) return v;
            }
        }
        return null; // no valid spot found
    }

    // try 2 build road connected 2 bots existing network on any valid edge
    private boolean tryBotBuildRoad(Player bot) {
        for (Tile t : board.getTiles()) {
            Vertex[] corners = t.getVertices();
            for (int i = 0; i < 6; i++) {
                Vertex v1 = corners[i];
                Vertex v2 = corners[(i + 1) % 6]; // adjacent corner (wraps around at index 5)
                if (!isDuplicateRoad(v1, v2) && isConnectedRoad(bot, v1, v2)) {
                    bot.deductResources(1, 1, 0, 0, 0);
                    Road road = new Road(bot, v1, v2);
                    board.getRoads().add(road);
                    bot.getRoads().add(road);
                    addLog("Bot built a road.");
                    checkLongestRoad();
                    return true;
                }
            }
        }
        return false; // no valid connected edge found
    }

    // ---- UTILITY ----

    // kept 4 backwards compat but takeTurn handles dice internaly now
    // still useful if something else needs a dice roll result
    public int rollDice() {
        lastDie1 = (int)(Math.random() * 6) + 1;
        lastDie2 = (int)(Math.random() * 6) + 1;
        return lastDie1 + lastDie2;
    }

    // get resorce count by type string (used in several dialogs)
    private int getCount(Player p, String type) {
        if (type.equals(ResourceType.WOOD))  return p.getWood();
        if (type.equals(ResourceType.BRICK)) return p.getBrick();
        if (type.equals(ResourceType.WOOL))  return p.getWool();
        if (type.equals(ResourceType.WHEAT)) return p.getWheat();
        if (type.equals(ResourceType.ORE))   return p.getOre();
        return 0;
    }

    // get resorce count by 0-4 index (wood=0 brick=1 wool=2 wheat=3 ore=4)
    // used in trade dialogs where we loop over all 5 resorces by index
    private int getCountByIndex(Player p, int idx) {
        if (idx == 0) return p.getWood();
        if (idx == 1) return p.getBrick();
        if (idx == 2) return p.getWool();
        if (idx == 3) return p.getWheat();
        return p.getOre();
    }

    // convert 0-4 index 2 ResourceType string constanst
    private String indexToResource(int idx) {
        if (idx == 0) return ResourceType.WOOD;
        if (idx == 1) return ResourceType.BRICK;
        if (idx == 2) return ResourceType.WOOL;
        if (idx == 3) return ResourceType.WHEAT;
        return ResourceType.ORE;
    }

    // apend 2 game log panel on left
    private void addLog(String msg) {
        view.addLog(msg);
    }

    // update both botom status label nd message drawn on board
    private void updateStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
        view.setMessage(msg);
    }

    public void startGame() { showIntro(); }
}
