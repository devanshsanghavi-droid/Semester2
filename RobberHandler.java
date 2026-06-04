import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

// handles robber movement, stealing resources, and the discard dialog
// called from GameController when movingRobber is true or a 7 is rolled
class RobberHandler {

    private GameController gc;

    RobberHandler(GameController gc) {
        this.gc = gc;
    }

    // handles a board click when movingRobber is true
    // finds closest tile, moves robber there, steals a resource
    void handleRobberClick(int mouseX, int mouseY) {
        // find hex closest 2 where they clicked
        Tile closest = null;
        int minDist = Integer.MAX_VALUE;
        for (Tile t : gc.board.getTiles()) {
            int[] xp = t.getXPoints(), yp = t.getYPoints();
            int cx = 0, cy = 0;
            for (int i = 0; i < 6; i++) { cx += xp[i]; cy += yp[i]; }
            cx /= 6; cy /= 6; // avg corners 2 get center
            int dist = (cx - mouseX) * (cx - mouseX) + (cy - mouseY) * (cy - mouseY);
            if (dist < minDist) { minDist = dist; closest = t; }
        }
        // cant put robber back where it already is
        if (closest.hasRobber()) {
            gc.updateStatus("Robber is already there. Pick a different tile.");
            return; // dont reset movingRobber, let them try again
        }
        // move robber: clear old set new
        for (Tile t : gc.board.getTiles()) { if (t.hasRobber()) t.setHasRobber(false); }
        closest.setHasRobber(true);

        // steal random resorce from first opponent found on that tile
        Player current = gc.players.get(gc.currentPlayerIndex);
        Player victim = findVictim(closest, current);
        if (victim != null) {
            stealRandom(victim, current);
        }
        gc.movingRobber = false;
        gc.updateStatus("Robber moved.");
        gc.view.setCurrentPlayerIndex(gc.currentPlayerIndex);
        gc.view.updateSidebar();
        gc.view.repaint();
    }

    // find first opponent player w bldg on any corner of tile t
    // returns null if no opponents r on that tile (no stealing happens)
    Player findVictim(Tile t, Player attacker) {
        Vertex[] verts = t.getVertices();
        for (Player p : gc.players) {
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
    String stealRandom(Player victim, Player thief) {
        String[] types = {ResourceType.WOOD, ResourceType.BRICK,
                          ResourceType.WOOL, ResourceType.WHEAT, ResourceType.ORE};
        for (int attempt = 0; attempt < 10; attempt++) {
            String type = types[(int)(Math.random() * 5)];
            if (gc.getCount(victim, type) > 0) {
                victim.removeResource(type);
                thief.addResource(type);
                return type;
            }
        }
        return null; // victim was broke, nothing 2 steal
    }

    // force player p 2 discard n cards (called when someone has >7 on a 7 roll)
    // shows one btn per resorce type they own, auto-closes when enuf discarded
    void showDiscardDialog(final Player p, final int n) {
        final JDialog dialog = new JDialog(gc.frame, p.getName() + " must discard " + n + " cards", true);
        dialog.setLayout(new FlowLayout());

        final String[] types  = {ResourceType.WOOD, ResourceType.BRICK,
                                  ResourceType.WOOL, ResourceType.WHEAT, ResourceType.ORE};
        final String[] labels = {"Wood", "Brick", "Wool", "Wheat", "Ore"};
        final int[] discarded = {0}; // array so inner class can modify it
        final JButton[] buttons = new JButton[5];

        for (int i = 0; i < 5; i++) {
            if (gc.getCount(p, types[i]) > 0) {
                final int idx = i;
                buttons[i] = new JButton(labels[i] + " (" + gc.getCount(p, types[i]) + ")");
                buttons[i].addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        p.removeResource(types[idx]);
                        discarded[0]++;
                        int rem = gc.getCount(p, types[idx]);
                        buttons[idx].setText(labels[idx] + " (" + rem + ")"); // update count on btn
                        if (rem == 0) buttons[idx].setEnabled(false); // grey out when depleted
                        if (discarded[0] >= n) dialog.dispose(); // done!
                    }
                });
                dialog.add(buttons[i]);
            }
        }

        dialog.pack();
        dialog.setLocationRelativeTo(gc.frame);
        dialog.setVisible(true); // blocks until n cards discarded
    }
}
