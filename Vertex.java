import java.util.ArrayList;

// corner of a hex, where  build stuff
public class Vertex {

    // pixel pos`
    private int x;
    private int y;

    // building here, null if empty
    private Building building;

    // tiles touching this corner (max 3)
    private ArrayList<Tile> adjacentTiles;

    // new vertex at x,y, nothing built yet
    public Vertex(int x, int y) {
        this.x = x;
        this.y = y;
        this.building = null;
        this.adjacentTiles = new ArrayList<Tile>();
    }

    // nothing here?
    public boolean isEmpty() {
        return building == null;
    }

    // put a building here
    public void placeBuilding(Building b) {
        building = b;
    }

    // get the building (or null)
    public Building getBuilding() {
        return building;
    }

    // link this corner to a tile during setup
    public void addAdjacentTile(Tile t) {
        adjacentTiles.add(t);
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public ArrayList<Tile> getAdjacentTiles() { return adjacentTiles; }
}
