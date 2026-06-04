import java.awt.event.*;
import javax.swing.*;

// handles the setup phase: placing starting settlements and roads in snake order
// called from GameController during inSetupPhase
class SetupHandler {

    private GameController gc;

    SetupHandler(GameController gc) {
        this.gc = gc;
    }

    // handles a board click during the setup phase routes 2 settlment placement or road placement depending on setupPlacingRoad flag
    void handleSetupClick(int mouseX, int mouseY) {
        if (!gc.setupPlacingRoad) {
            handleSetupSettlement(mouseX, mouseY);
        } else {
            // two-click road placement during setup (free, just edge check)
            Vertex nearest = gc.findNearestVertex(mouseX, mouseY);
            if (nearest == null) return;
            if (gc.roadFirstClick == null) {
                gc.roadFirstClick = nearest;
                gc.view.setHighlightedVertex(nearest);
                gc.view.repaint();
                gc.updateStatus("Click second vertex of road.");
            } else {
                placeSetupRoad(gc.players.get(gc.currentPlayerIndex), gc.roadFirstClick, nearest);
                gc.roadFirstClick = null;
                gc.view.setHighlightedVertex(null);
            }
        }
    }

    // setup settlment: free, no connectivity check, just distance rule
    private void handleSetupSettlement(int mouseX, int mouseY) {
        Vertex nearest = gc.findNearestVertex(mouseX, mouseY);
        if (nearest == null) return;
        Player current = gc.players.get(gc.currentPlayerIndex);
        if (!nearest.isEmpty()) {
            gc.updateStatus("That spot is taken."); gc.view.repaint(); return;
        }
        if (!gc.isValidPlacement(nearest)) {
            gc.updateStatus("Too close to another settlement."); gc.view.repaint(); return;
        }
        Settlement s = new Settlement(current, nearest);
        nearest.placeBuilding(s);
        current.addSettlement(s);
        // only give starting resorces 4 2nd settlment (setupTurnIndex >= 2 for 2 players)
        if (gc.setupTurnIndex >= gc.players.size()) distributeSetupResources(nearest);
        gc.setupPlacingRoad = true;
        MusicPlayer.playSound("catan_sounds/catan_build_settlement.wav");
        String turn = (gc.setupTurnIndex < gc.players.size()) ? "first" : "second";
        gc.updateStatus(current.getName() + ": place your " + turn + " road.");
        gc.view.setCurrentPlayerIndex(gc.currentPlayerIndex);
        gc.view.updateSidebar();
        gc.view.repaint();
    }

    // give current player 1 of each resorce from tiles touching vertex v
    // only called during setup 4 second settlment placement
    void distributeSetupResources(Vertex v) {
        Player current = gc.players.get(gc.currentPlayerIndex);
        for (Tile t : v.getAdjacentTiles())
        {
            if (!t.getResourceType().equals("DESERT")) {
                current.addResource(t.getResourceType());
            }
        }
    }

    // setup roa just checks shared edge, no cost no connectivity req
    void placeSetupRoad(Player p, Vertex v1, Vertex v2) {
        if (!gc.sharesEdge(v1, v2)) {
            gc.updateStatus("Not a valid road edge."); return;
        }
        Road road = new Road(p, v1, v2);
        gc.board.getRoads().add(road);
        p.getRoads().add(road);
        MusicPlayer.playSound("catan_sounds/catan_build_road.wav");
        advanceSetupAfterRoad();
        gc.view.repaint();
    }

    //  to next step in setup order ter road placed
    // once all setup turns done, unlock roll btn nd start normal play
    void advanceSetupAfterRoad() {
        gc.setupPlacingRoad = false;
        gc.setupTurnIndex++;
        if (gc.setupTurnIndex >= gc.setupOrder.length) {
            // setup doneplayer 1 always goes first
            gc.inSetupPhase = false;
            gc.currentPlayerIndex = 0;
            gc.rollButton.setEnabled(true);
            gc.updateStatus(gc.players.get(0).getName() + "'s turn, roll the dice!");
        } else {
            // next player in snake 
            gc.currentPlayerIndex = gc.setupOrder[gc.setupTurnIndex];
            String turn = (gc.setupTurnIndex < gc.players.size()) ? "first" : "second";
            gc.updateStatus(gc.players.get(gc.currentPlayerIndex).getName()
                       + ": place your " + turn + " settlement.");
        }
        gc.view.setCurrentPlayerIndex(gc.currentPlayerIndex);
        gc.view.updateSidebar();
        gc.view.repaint();

    // if its bots setup turn, auto lace after short delay
        if (gc.botEnabled && gc.inSetupPhase && gc.currentPlayerIndex == 1) {
            gc.botThinking = true;
            Timer t = new Timer(700, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    ((Timer) e.getSource()).stop();
                    gc.botSetupSettlement();
                }
            });
            t.setRepeats(false);
            t.start();
        }
    }
}
