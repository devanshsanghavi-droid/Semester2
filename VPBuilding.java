// fake building w no location on board
// only exists so addSettlement() bumps victoryPoints by 1 when vp card drawn
// kinda hacky but keeps vp logic in one place
public class VPBuilding extends Building {

    // null vertex = not on bord, thats intentional
    public VPBuilding(Player owner) {
        super(owner, null);
    }

    // 1 vp same as settlment
    public int getVP() {
        return 1;
    }
}
