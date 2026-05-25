import java.util.ArrayList;

// player - has resources + buildings + vp
public class Player {

    // name
    private String name;

    // resource counts
    private int wood;
    private int brick;
    private int wool;
    private int wheat;
    private int ore;

    // total vp
    private int victoryPoints;

    // all buildings they placed (both settlement + city go here)
    private ArrayList<Building> settlements;

    // all roads they placed
    private ArrayList<Road> roads;

    // new player, starts w 0 everything
    public Player(String name) {
        this.name = name;
        this.wood = 0;
        this.brick = 0;
        this.wool = 0;
        this.wheat = 0;
        this.ore = 0;
        this.victoryPoints = 0;
        this.settlements = new ArrayList<Building>();
        this.roads = new ArrayList<Road>();
    }

    // true if they can pay the cost
    public boolean canAfford(int w, int br, int wl, int wh, int o) {
        return wood >= w && brick >= br && wool >= wl && wheat >= wh && ore >= o;
    }

    // subtracts the given amts from inventory; returns false if insufficient
    public boolean deductResources(int w, int br, int wl, int wh, int o) {
        if (wood < w || brick < br || wool < wl || wheat < wh || ore < o) return false;
        wood -= w;
        brick -= br;
        wool -= wl;
        wheat -= wh;
        ore -= o;
        return true;
    }

    // +1 of given resource
    public void addResource(String type) {
        if (type.equals(ResourceType.WOOD)) {
            wood++;
        } else if (type.equals(ResourceType.BRICK)) {
            brick++;
        } else if (type.equals(ResourceType.WOOL)) {
            wool++;
        } else if (type.equals(ResourceType.WHEAT)) {
            wheat++;
        } else if (type.equals(ResourceType.ORE)) {
            ore++;
        }
    }

    // add building, also bumps vp
    public void addSettlement(Building b) {
        settlements.add(b);
        victoryPoints += b.getVP();
    }

    // remove building, also drops vp (para upgrade)
    public void removeSettlement(Building b) {
        if (settlements.remove(b)) {
            victoryPoints -= b.getVP();
        }
    }

    // getters not setter wow
    public String getName() { return name; }
    public int getWood() { return wood; }
    public int getBrick() { return brick; }
    public int getWool() { return wool; }
    public int getWheat() { return wheat; }
    public int getOre() { return ore; }
    public int getVictoryPoints() { return victoryPoints; }
    public ArrayList<Building> getSettlements() { return settlements; }
    public ArrayList<Road> getRoads() { return roads; }
}
