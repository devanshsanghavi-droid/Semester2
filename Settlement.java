// settlement = 1 vp, upgradeable
public class Settlement extends Building {

    public Settlement(Player owner, Vertex location) {
        super(owner, location);
    }

    // 1 pt
    public int getVP() {
        return 1;
    }

    // turns this into a city on same spot
    // caller checks canAfford(0,0,0,2,3) first
    public void upgrade() {
        Player owner = getOwner(); Vertex loc = getLocation();
        // rm settlement first
        owner.removeSettlement(this);
        // slap a city there
        City city = new City(owner, loc);
        loc.placeBuilding(city);
        owner.addSettlement(city);
    }
}
