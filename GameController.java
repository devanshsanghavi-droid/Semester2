import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.ArrayList;

// main game class, handles turn flow dice all placements dev cards trading bot win check
// basically everything thats not just drawing goes here
// has main() so run this one
public class GameController {

    // board holds tiles vertices roads dev deck
    GameBoard board;

    // both players, index 0 = human index 1 = bot
    ArrayList<Player> players;

    // the drawing panel in center
    GameView view;

    // index into players[], whos turn it is
    int currentPlayerIndex;

    // true after roll, reset at end of turn
    private boolean hasRolled;

    // true when somebody hits 10vp, locks everything
    private boolean gameOver;

    // setup order built dynamically based on player count (snake draft)
    // 2p: 0 1 1 0   3p: 0 1 2 2 1 0   4p: 0 1 2 3 3 2 1 0
    int[] setupOrder;
    boolean inSetupPhase;
    int setupTurnIndex;
    boolean setupPlacingRoad; // true after settlment placed waiting 4 road click

    // two-click road placement: store first click wait 4 second
    private boolean placingRoad;
    Vertex roadFirstClick;

    // true while player is clicking which settlment 2 upgrd
    private boolean buildingCity;

    // true while player needs 2 click tile 4 robber (after 7 or knight)
    boolean movingRobber;

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

    // true if player 2 is a bot
    boolean botEnabled;
    // true while bot is acting, blocks human mouse clicks
    boolean botThinking;

    // buttons stored as fields so we can enable/disable from anywhere
    // rule: only Roll Dice active before roll, all others active after roll
    JButton rollButton;
    private JButton endTurnButton;
    private JButton buildRoadButton;
    private JButton buildCityButton;
    private JButton buyDevCardButton;
    private JButton playDevCardButton;
    private JButton tradeButton;

    // status bar label at botom + transition screen labels
    private JLabel statusLabel;
    private JLabel transitionLabel;
    private JLabel transitionSubLabel;

    // frame + cardlayout 4 intro -> game -> transition panels
    JFrame frame;
    private CardLayout cardLayout;
    private JPanel cardPanel;

    // helpers split out 2 keep this file shorter
    RobberHandler robber;
    SetupHandler setup;
    private BotController botAI;

    // set up everything at 0/false, vsBot resolved in showIntro
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
        hasPlayedDevCard = false;
        roadBuildingActive = false;
        roadBuildingRoadsLeft = 0;
        largestArmyHolder = -1;
        longestRoadHolder = -1;
        lastDie1 = 0;
        lastDie2 = 0;
        botEnabled = false;
        botThinking = false;
        setupOrder = new int[]{0, 1, 1, 0}; // placeholder, rebuilt in showIntro
        view = new GameView(board, players);
        robber = new RobberHandler(this);
        setup  = new SetupHandler(this);
        botAI  = new BotController(this);
    }

    // show star wars style intro crawl first then game
    // dialogs run BEFORE frame is built so sidebar gets correct player count
    public void showIntro() {
        // mode dialog first (null parent = screen center, fine before frame exists)
        Object[] modeOptions = {"vs Bot", "Player vs Player"};
        int modeChoice = JOptionPane.showOptionDialog(null,
            "Choose game mode:",
            "Settlers of Catan",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null, modeOptions, modeOptions[0]);

        if (modeChoice != 1) {
            // bot mode: 2 players
            botEnabled = true;
            players.get(1).setName("Bot");
        } else {
            // pvp: ask how many players (2 3 or 4)
            Object[] countOptions = {"2", "3", "4"};
            int countChoice = JOptionPane.showOptionDialog(null,
                "How many players?",
                "Settlers of Catan",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null, countOptions, countOptions[0]);
            int numPlayers = 2; // default if dialog closed
            if (countChoice == 1) numPlayers = 3;
            else if (countChoice == 2) numPlayers = 4;
            // add extra players if needed (constructor already made P1 and P2)
            while (players.size() < numPlayers) {
                players.add(new Player("Player " + (players.size() + 1)));
            }
        }

        // build setup order now that player count is locked in
        setupOrder = buildSetupOrder(players.size());

        // build frame after player count is known so sidebar has right number of labels
        frame = new JFrame("Settlers of Catan");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        IntroCrawlPanel intro = new IntroCrawlPanel(this);
        cardPanel.add(intro, "intro");

        // sidebar is built inside buildGamePanel, so players list must be final by here
        JPanel gamePanel = buildGamePanel();
        cardPanel.add(gamePanel, "game");

        JPanel transPanel = buildTransitionPanel();
        cardPanel.add(transPanel, "transition");

        frame.add(cardPanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        cardLayout.show(cardPanel, "intro");
        intro.startCrawl();
    }

    // build snake draft setup order for n players
    // forward pass 0..n-1 then reverse pass n-1..0
    private int[] buildSetupOrder(int numPlayers) {
        int[] order = new int[numPlayers * 2];
        for (int i = 0; i < numPlayers; i++) order[i] = i;
        for (int i = 0; i < numPlayers; i++) order[numPlayers + i] = numPlayers - 1 - i;
        return order;
    }

    // called by IntroCrawlPanel when crawl finishes or player skips
    public void showGame() {
        cardLayout.show(cardPanel, "game");
        inSetupPhase = true;
        setupTurnIndex = 0;
        setupPlacingRoad = false;
        currentPlayerIndex = setupOrder[0]; // always 0, player 1 goes first
        rollButton.setEnabled(false);
        endTurnButton.setEnabled(false);
        setPostRollButtons(false); // nothing available until you roll
        updateStatus(players.get(currentPlayerIndex).getName() + ": place your first settlement.");
        view.setCurrentPlayerIndex(currentPlayerIndex);
        view.updateSidebar();
        view.repaint();
    }

    // assemble main game panel: board in center sidebar on right btns on botom
    private JPanel buildGamePanel() {
        JPanel gamePanel = new JPanel(new BorderLayout());

        JPanel sidebar = view.createSidebar();

        gamePanel.add(view,    BorderLayout.CENTER);
        gamePanel.add(sidebar, BorderLayout.EAST);
        gamePanel.add(buildBottomPanel(), BorderLayout.SOUTH);

        // mouse clicks on board panel only (not sidebar or log)
        // allow clicks during setup nd during movingRobber even before hasRolled
        view.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (!gameOver && !botThinking && (hasRolled || inSetupPhase || movingRobber)) {
                    handleBoardClick(e.getX(), e.getY());
                }
            }
        });

        return gamePanel;
    }

    // black panel shown between turns so other player cant peek at cards
    private JPanel buildTransitionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.BLACK);
        panel.setPreferredSize(new Dimension(1100, 660));

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBackground(Color.BLACK);

        transitionLabel = new JLabel("...");
        transitionLabel.setForeground(Color.WHITE);
        transitionLabel.setFont(new Font("Arial", Font.BOLD, 48));
        transitionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        transitionSubLabel = new JLabel("...");
        transitionSubLabel.setForeground(new Color(180, 180, 180));
        transitionSubLabel.setFont(new Font("Arial", Font.PLAIN, 22));
        transitionSubLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        inner.add(transitionLabel);
        inner.add(Box.createVerticalStrut(18));
        inner.add(transitionSubLabel);
        panel.add(inner);

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
        // player who just finished = one step back from nextIndex
        String prev = players.get((nextIndex - 1 + players.size()) % players.size()).getName();
        transitionLabel.setText(prev + " LOOK AWAY!!!!");
        transitionSubLabel.setText("Give the computer to " + name + ". In 3 seconds.");
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

        if (roll == 7) {
            // anyone w more than 7 cards has 2 discard half (rounded down)
            for (Player p : players) {
                int total = p.getWood() + p.getBrick() + p.getWool() + p.getWheat() + p.getOre();
                if (total > 7) robber.showDiscardDialog(p, total / 2);
            }
            movingRobber = true;
            MusicPlayer.playSound("catan_sounds/catan_roll_seven.wav");
            updateStatus(rollMsg + ", move the robber!");
        } else {
            distributeResources(roll);
            MusicPlayer.playSound("catan_sounds/catan_dice_roll.wav");
            updateStatus(rollMsg + ". Build something!");
        }

        view.setCurrentPlayerIndex(currentPlayerIndex);
        view.updateSidebar();
        view.repaint();
    }

    // end current players turn, check longest road, then either transition or hand 2 bot
    void endTurn() {
        checkLongestRoad(); // recalc now in case roads changed this turn
        int nextIndex = (currentPlayerIndex + 1) % players.size();

        if (botEnabled && players.size() == 2 && nextIndex == 1) {
            // bot skips transition screen
            currentPlayerIndex = nextIndex;
            hasRolled = false;
            rollButton.setEnabled(false);
            endTurnButton.setEnabled(false);
            setPostRollButtons(false);
            players.get(currentPlayerIndex).clearBoughtThisTurn();
            hasPlayedDevCard = false;
            botThinking = true;
            view.setCurrentPlayerIndex(currentPlayerIndex);
            view.updateSidebar();
            updateStatus("Bot is thinking...");
            view.repaint();
            Timer t = new Timer(800, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    ((Timer) e.getSource()).stop();
                    botAI.botTakeTurn();
                }
            });
            t.setRepeats(false);
            t.start();
        } else {
            showTransition(nextIndex);
        }
    }

    // ---- BOARD CLICK HANDLER ----

    // single entry point 4 all board clicks
    // checks modes in order: robber > setup > road > city > normal settlment
    private void handleBoardClick(int rawX, int rawY) {
        // convert panel coords 2 board coords (board is translated when drawn)
        int mouseX = rawX - view.getBoardOffsetX();
        int mouseY = rawY - view.getBoardOffsetY();

        // robber always takes priority over everything else
        if (movingRobber) { robber.handleRobberClick(mouseX, mouseY); return; }

        // setup phase: free settlments nd roads in snake order
        if (inSetupPhase) { setup.handleSetupClick(mouseX, mouseY); return; }

        // road placement mode: active after clicking Build Road or playing road bldg card
        if (placingRoad) {
            Vertex nearest = findNearestVertex(mouseX, mouseY);
            if (nearest == null) return;
            if (roadFirstClick == null) {
                roadFirstClick = nearest;
                view.setHighlightedVertex(nearest);
                view.repaint();
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
                view.setHighlightedVertex(null);
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
                MusicPlayer.playSound("catan_sounds/catan_build_city.wav");
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
            MusicPlayer.playSound("catan_sounds/catan_build_settlement.wav");
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
    Vertex findNearestVertex(int mouseX, int mouseY) {
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
    // setup helpers moved 2 SetupHandler.java

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
        MusicPlayer.playSound("catan_sounds/catan_build_road.wav");
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
            MusicPlayer.playSound("catan_sounds/catan_build_road.wav");
            updateStatus("Road Building complete.");
        }
    }

    // ---- VALIDATION ----

    // distance rule: none of adjacent corners on any shared tile can have bldg
    boolean isValidPlacement(Vertex v) {
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
    boolean sharesEdge(Vertex v1, Vertex v2) {
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
    boolean isDuplicateRoad(Vertex v1, Vertex v2) {
        for (Road r : board.getRoads()) {
            if ((r.getV1() == v1 && r.getV2() == v2) || (r.getV1() == v2 && r.getV2() == v1)) {
                return true;
            }
        }
        return false;
    }

    // true if at least one of v1/v2 touches one of p's existing roads or bldgs
    // this is connectivity rule that prevents roads floating in middle of nowhere
    boolean isConnectedRoad(Player p, Vertex v1, Vertex v2) {
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
    boolean isConnectedToRoad(Player p, Vertex v) {
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

    // ---- ROBBER HELPERS ----
    // robber logic moved 2 RobberHandler.java

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
            MusicPlayer.playSound("catan_sounds/catan_dev_card.wav");
            updateStatus("Victory Point card! +1 VP");
            checkWinAndHandle(); // vp card might push to 10
        } else {
            current.addDevCard(card);
            current.addBoughtThisTurn(card); // mark it so cant be played same turn
            MusicPlayer.playSound("catan_sounds/catan_dev_card.wav");
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
        MusicPlayer.playSound("catan_sounds/catan_knight_card.wav");
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
                view.setCurrentPlayerIndex(currentPlayerIndex);
                view.updateSidebar();
                dialog.dispose();
                MusicPlayer.playSound("catan_sounds/catan_trade_accepted.wav");
                updateStatus("Trade complete.");
            }
        });
        dialog.add(confirm, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
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
            view.setCurrentPlayerIndex(currentPlayerIndex);
            view.updateSidebar();
            checkWinAndHandle(); // 2 extra vp might win game
        }
    }

    // ---- LONGEST ROAD ----

    // recalculate longest road 4 all players nd transfer card if needed
    // called after every road placed nd every settlment placed (settlments can break roads)
    void checkLongestRoad() {
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
    void checkWinAndHandle() {
        if (!checkWin()) return;
        gameOver = true;
        MusicPlayer.playSound("catan_sounds/catan_victory.wav");
        rollButton.setEnabled(false);
        endTurnButton.setEnabled(false);
        setPostRollButtons(false);

        // find who won (first player at 10+ vp)
        Player winner = null;
        for (Player p : players) {
            if (p.getVictoryPoints() >= 10) { winner = p; break; }
        }

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

//disc

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
    // bot logic moved 2 BotController.java
    // thin wrapper so SetupHandler can still call gc.botSetupSettlement()
    void botSetupSettlement() { botAI.botSetupSettlement(); }

//util 
    // kept 4 backwards compat but takeTurn handles dice internaly now
    // still useful if something else needs a dice roll result
    public int rollDice() {
        lastDie1 = (int)(Math.random() * 6) + 1;
        lastDie2 = (int)(Math.random() * 6) + 1;
        return lastDie1 + lastDie2;
    }

    // get resorce count by type string (used in several dialogs)
    int getCount(Player p, String type) {
        if (type.equals(ResourceType.WOOD))  
            return p.getWood();

        if (type.equals(ResourceType.BRICK)) 
            return p.getBrick();


        if (type.equals(ResourceType.WOOL))  
            return p.getWool();
        if (type.equals(ResourceType.WHEAT)) 
            return p.getWheat();
        if (type.equals(ResourceType.ORE))   
            return p.getOre();
        return 0;
    }





    // update both botom status label nd message drawn on board
    void updateStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
        view.setMessage(msg);
    }

    public void startGame() { showIntro(); }
}
