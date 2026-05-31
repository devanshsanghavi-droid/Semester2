// fake building with no location on the board
// only exists so we can call player.addSettlement() and get the victoryPoints
// field to tick up by 1 when a VP dev card is drawn
// kinda hacky but it works and keeps the vp logic in one place
public class VPBuilding extends Building {

    // null vertex = not on board, thats intentional
    public VPBuilding(Player owner) {
        super(owner, null);
    }

    // 1 vp per card, same as a settlement
    public int getVP() { return 1; }
}
