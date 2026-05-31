import java.util.ArrayList;

// player class, tracks resources buildings roads dev cards vp
// getVictoryPoints() adds victoryPoints + bonusVP so sidebar always right
public class Player {

    // name shown everywhere
    private String name;

    // resorce counts, all start 0
    private int wood;
    private int brick;
    private int wool;
    private int wheat;
    private int ore;

    // vp from bldgs + vp dev cards (1 per settlment, 2 per city, 1 per vp card)
    // changed by addSettlement / removeSettlement
    private int victoryPoints;

    // extra vp from bonus cards, +2 largest army +2 longest road
    // only touched by setHoldsLargestArmy / setHoldsLongestRoad
    private int bonusVP;

    // all bldgs placed, both settlments and cities go here
    // city upgrd removes settlment adds city so vp stays corect
    private ArrayList<Building> settlements;

    // all roads placed
    private ArrayList<Road> roads;

    // dev cards in hand, other player shudnt see contents just count
    private ArrayList<String> devCards;

    // cards bought this turn so we can blok playing them same turn
    private ArrayList<String> boughtThisTurn;

    // how many knight cards this player actually played (not just owned)
    // needed 4 largest army check
    private int knightsPlayed;

    // true if player holds Largest Army card rn
    private boolean holdsLargestArmy;

    // true if player holds Longest Road card rn
    private boolean holdsLongestRoad;

    // brand new player, nothing owned yet
    public Player(String name) {
        this.name = name;
        this.wood = 0;
        this.brick = 0;
        this.wool = 0;
        this.wheat = 0;
        this.ore = 0;
        this.victoryPoints = 0;
        this.bonusVP = 0;
        this.settlements = new ArrayList<Building>();
        this.roads = new ArrayList<Road>();
        this.devCards = new ArrayList<String>();
        this.boughtThisTurn = new ArrayList<String>();
        this.knightsPlayed = 0;
        this.holdsLargestArmy = false;
        this.holdsLongestRoad = false;
    }

    // true if they have enuf of every resorce to pay (w, br, wl, wh, o)
    public boolean canAfford(int w, int br, int wl, int wh, int o) {
        return wood >= w && brick >= br && wool >= wl && wheat >= wh && ore >= o;
    }

    // subtract resorces, returns false if they cant affort it
    public boolean deductResources(int w, int br, int wl, int wh, int o) {
        if (wood < w || brick < br || wool < wl || wheat < wh || ore < o) return false;
        wood  -= w;
        brick -= br;
        wool  -= wl;
        wheat -= wh;
        ore   -= o;
        return true;
    }

    // take away 1 resorce, wont go below 0
    // used 4 robber steals and discard
    public void removeResource(String type) {
        if      (type.equals(ResourceType.WOOD))  { if (wood  > 0) wood--;  }
        else if (type.equals(ResourceType.BRICK)) { if (brick > 0) brick--; }
        else if (type.equals(ResourceType.WOOL))  { if (wool  > 0) wool--;  }
        else if (type.equals(ResourceType.WHEAT)) { if (wheat > 0) wheat--; }
        else if (type.equals(ResourceType.ORE))   { if (ore   > 0) ore--;   }
    }

    // give player 1 resorce
    public void addResource(String type) {
        if      (type.equals(ResourceType.WOOD))  wood++;
        else if (type.equals(ResourceType.BRICK)) brick++;
        else if (type.equals(ResourceType.WOOL))  wool++;
        else if (type.equals(ResourceType.WHEAT)) wheat++;
        else if (type.equals(ResourceType.ORE))   ore++;
    }

    // add bldg and tick up victoryPoints by its vp value
    // works 4 settlments (1vp) cities (2vp) and vp cards (1vp via VPBuilding)
    public void addSettlement(Building b) {
        settlements.add(b);
        victoryPoints += b.getVP();
    }

    // remove bldg and drop vp, only used when upgrading settlment to city
    public void removeSettlement(Building b) {
        if (settlements.remove(b)) {
            victoryPoints -= b.getVP();
        }
    }

    // put dev card in hand
    public void addDevCard(String type) {
        devCards.add(type);
    }

    // remove one copy of card from hand, returns false if not found
    public boolean removeDevCard(String type) {
        for (int i = 0; i < devCards.size(); i++) {
            if (devCards.get(i).equals(type)) {
                devCards.remove(i);
                return true;
            }
        }
        return false;
    }

    // remember card was bought this turn so it cant be played yet
    public void addBoughtThisTurn(String type) {
        boughtThisTurn.add(type);
    }

    // wipe bought-this-turn list at end of each turn
    public void clearBoughtThisTurn() {
        boughtThisTurn.clear();
    }

    // true if this type was bought this turn (so its unplayble)
    public boolean boughtThisTurn(String type) {
        for (int i = 0; i < boughtThisTurn.size(); i++) {
            if (boughtThisTurn.get(i).equals(type)) return true;
        }
        return false;
    }

    // how many of this card type can be played rn
    // = total in hand minus any bought this turn (those locked)
    public int playableCount(String type) {
        int total = 0;
        for (int i = 0; i < devCards.size(); i++) {
            if (devCards.get(i).equals(type)) total++;
        }
        for (int i = 0; i < boughtThisTurn.size(); i++) {
            if (boughtThisTurn.get(i).equals(type)) total--;
        }
        return total < 0 ? 0 : total;
    }

    // played a knight, bump counter
    // checked in GameController.checkLargestArmy()
    public void incrementKnights() {
        knightsPlayed++;
    }

    // give or take Largest Army bonus, adjusts bonusVP by +/-2
    // called from checkLargestArmy() when card transfers
    public void setHoldsLargestArmy(boolean val) {
        if (val && !holdsLargestArmy) {
            bonusVP += 2;
        } else if (!val && holdsLargestArmy) {
            bonusVP -= 2;
        }
        holdsLargestArmy = val;
    }

    // give or take Longest Road bonus, adjusts bonusVP by +/-2
    // called from checkLongestRoad()
    public void setHoldsLongestRoad(boolean val) {
        if (val && !holdsLongestRoad) {
            bonusVP += 2;
        } else if (!val && holdsLongestRoad) {
            bonusVP -= 2;
        }
        holdsLongestRoad = val;
    }

    // total vp = bldgs/vp-cards + bonus cards, this is wat sidebar shows
    public int getVictoryPoints() {
        return victoryPoints + bonusVP;
    }

    // how many dev cards in hand (shown 2 all players, contents hidden)
    public int getDevCardCount() {
        return devCards.size();
    }

    // count of just settlments (not cities) 4 sidebar display
    public int getSettlementCount() {
        int count = 0;
        for (int i = 0; i < settlements.size(); i++) {
            if (settlements.get(i) instanceof Settlement) count++;
        }
        return count;
    }

    // count of just cities 4 sidebar display
    public int getCityCount() {
        int count = 0;
        for (int i = 0; i < settlements.size(); i++) {
            if (settlements.get(i) instanceof City) count++;
        }
        return count;
    }

    // getters (no setters, all mutation goes thru methods above)
    public String getName()               { return name; }
    public int getWood()                  { return wood; }
    public int getBrick()                 { return brick; }
    public int getWool()                  { return wool; }
    public int getWheat()                 { return wheat; }
    public int getOre()                   { return ore; }
    public int getKnightsPlayed()         { return knightsPlayed; }
    public boolean isHoldsLargestArmy()   { return holdsLargestArmy; }
    public boolean isHoldsLongestRoad()   { return holdsLongestRoad; }
    public ArrayList<Building> getSettlements() { return settlements; }
    public ArrayList<Road> getRoads()     { return roads; }
    public ArrayList<String> getDevCards() { return devCards; }
    public ArrayList<String> getBoughtThisTurn() { return boughtThisTurn; }
}
