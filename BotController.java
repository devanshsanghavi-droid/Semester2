import java.awt.event.*;
import javax.swing.*;

// all bot AI logic: setup placement, normal turn, robber, building
// called from GameController when its the bots turn
class BotController {

    private GameController gc;

    BotController(GameController gc) {
        this.gc = gc;
    }

    // bot setup: pick vertex w most adjacent non-desert tiles, place free settlment
    void botSetupSettlement() {
        Player bot = gc.players.get(gc.currentPlayerIndex);
        Vertex best = null;
        int bestScore = -1;
        for (Vertex v : gc.board.getVertices()) {
            if (!v.isEmpty() || !gc.isValidPlacement(v)) continue;
            int score = 0;
            for (Tile t : v.getAdjacentTiles()) {
                if (!t.getResourceType().equals("DESERT")) score++;
            }
            if (score > bestScore) { bestScore = score; best = v; }
        }
        if (best == null) { gc.botThinking = false; return; }

        final Vertex placed = best;
        Settlement s = new Settlement(bot, placed);
        placed.placeBuilding(s);
        bot.addSettlement(s);
        if (gc.setupTurnIndex >= gc.players.size()) gc.setup.distributeSetupResources(placed);
        gc.setupPlacingRoad = true;
        gc.updateStatus("Bot placed a settlement.");
        gc.view.setCurrentPlayerIndex(gc.currentPlayerIndex);
        gc.view.updateSidebar();
        gc.view.repaint();

        // place road next after short delay
        Timer t = new Timer(600, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((Timer) e.getSource()).stop();
                botSetupRoad(placed);
            }
        });
        t.setRepeats(false);
        t.start();
    }

    // bot setup: place free road off the just-placed settlment vertex
    private void botSetupRoad(Vertex settlementVertex) {
        Player bot = gc.players.get(gc.currentPlayerIndex);
        for (Tile t : settlementVertex.getAdjacentTiles()) {
            Vertex[] corners = t.getVertices();
            for (int i = 0; i < 6; i++) {
                if (corners[i] == settlementVertex) {
                    Vertex other = corners[(i + 1) % 6];
                    if (!gc.isDuplicateRoad(settlementVertex, other)) {
                        gc.setup.placeSetupRoad(bot, settlementVertex, other);
                        gc.botThinking = false;
                        return;
                    }
                    other = corners[(i + 5) % 6];
                    if (!gc.isDuplicateRoad(settlementVertex, other)) {
                        gc.setup.placeSetupRoad(bot, settlementVertex, other);
                        gc.botThinking = false;
                        return;
                    }
                }
            }
        }
        // no road found, just advance anyway
        gc.setup.advanceSetupAfterRoad();
        gc.botThinking = false;
    }

    // bot normal turn: roll, handle robber if 7, then build, then end
    void botTakeTurn() {
        gc.takeTurn();
        if (gc.movingRobber) {
            // rolled 7, move robber then do actions
            Timer t = new Timer(800, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    ((Timer) e.getSource()).stop();
                    botMoveRobber();
                    botScheduleActions();
                }
            });
            t.setRepeats(false);
            t.start();
        } else {
            botScheduleActions();
        }
    }

    // wait 800ms then do all bot build actions and end turn
    private void botScheduleActions() {
        Timer t = new Timer(800, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((Timer) e.getSource()).stop();
                botDoActions();
            }
        });
        t.setRepeats(false);
        t.start();
    }

    // bot builds whatever it can afford in priority order, then ends turn
    private void botDoActions() {
        Player bot = gc.players.get(gc.currentPlayerIndex);

        // city upgrade first (best vp per resource)
        if (bot.canAfford(0, 0, 0, 2, 3)) {
            for (Building b : bot.getSettlements()) {
                if (b instanceof Settlement) {
                    bot.deductResources(0, 0, 0, 2, 3);
                    ((Settlement) b).upgrade();
                    gc.checkWinAndHandle();
                    gc.view.setCurrentPlayerIndex(gc.currentPlayerIndex);
                    gc.view.updateSidebar();
                    gc.view.repaint();
                    break;
                }
            }
        }


        // settlement if theres a valid connected spot
        if (bot.canAfford(1, 1, 1, 1, 0)) {
            Vertex spot = findBotSettlementSpot(bot);
            if (spot != null && gc.placeSettlement(bot, spot)) {
                gc.checkLongestRoad();
                gc.checkWinAndHandle();
                gc.view.setCurrentPlayerIndex(gc.currentPlayerIndex);
                gc.view.updateSidebar();
                gc.view.repaint();
            }
        }

        // road to expand network
        if (bot.canAfford(1, 1, 0, 0, 0)) {
            if (tryBotBuildRoad(bot)) {
                gc.view.setCurrentPlayerIndex(gc.currentPlayerIndex);
                gc.view.updateSidebar();
                gc.view.repaint();
            }
        }

        // end turn after short pause so human can see what happened
        Timer t = new Timer(600, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ((Timer) e.getSource()).stop();
                gc.botThinking = false;
                gc.endTurn();
            }
        });
        t.setRepeats(false);
        t.start();
    }

    // bot robber logic: target tile w most opponent bldgs on it
    // then steal randum resorce from whoever is there
    private void botMoveRobber() {
        Player bot = gc.players.get(gc.currentPlayerIndex);
        Tile best = null;
        int bestCount = -1;
        for (Tile t : gc.board.getTiles()) {
            if (t.hasRobber()) continue; // cant stay in same spot
            int count = 0;
            for (Vertex v : t.getVertices()) {
                if (!v.isEmpty() && v.getBuilding().getOwner() != bot) count++;
            }
            if (count > bestCount) { bestCount = count; best = t; }
        }
        if (best == null) {
            // fallbac just pick any tile that doesnt have robber already
            for (Tile t : gc.board.getTiles()) { if (!t.hasRobber()) { best = t; break; } }
        }
        for (Tile t : gc.board.getTiles()) { if (t.hasRobber()) t.setHasRobber(false); }
        best.setHasRobber(true);
        Player victim = gc.robber.findVictim(best, bot);
        if (victim != null) {
            gc.robber.stealRandom(victim, bot);
        }
        gc.movingRobber = false;
        gc.view.repaint();
    }

    // find vertex connected 2 bots road network that passes distance rule
    private Vertex findBotSettlementSpot(Player bot) {
        for (Road r : bot.getRoads()) {
            Vertex[] ends = {r.getV1(), r.getV2()};
            for (Vertex v : ends) {
                if (v.isEmpty() && gc.isValidPlacement(v) && gc.isConnectedToRoad(bot, v)) return v;
            }
        }

        return null; // no valid spot
    }

    // try 2 build road connected 2 bots existing netwo
    private boolean tryBotBuildRoad(Player bot) {
        for (Tile t : gc.board.getTiles())
        {
            Vertex[] corners = t.getVertices();
            for (int i = 0; i < 6; i++) {
                Vertex v1 = corners[i];
                Vertex v2 = corners[(i + 1) % 6]; // adj corner
                if (!gc.isDuplicateRoad(v1, v2) && gc.isConnectedRoad(bot, v1, v2)) {
                    bot.deductResources(1, 1, 0, 0, 0);
                    Road road = new Road(bot, v1, v2);
                    gc.board.getRoads().add(road);
                    bot.getRoads().add(road);
                    gc.checkLongestRoad();
                    return true;
                }
            }
        }
        return false; // no valid connected edge
        //
    }
}
