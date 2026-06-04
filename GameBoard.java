import java.util.ArrayList;

// stores all 19 tiles + shared corners
public class GameBoard {

    // all the tiles
    private ArrayList<Tile> tiles;

    // all unique corners (shared btw neighbors)
    private ArrayList<Vertex> vertices;

    // all placed roads
    private ArrayList<Road> roads;

    // shuffled dev card deck, strings matching DevCard constansts
    private ArrayList<String> devDeck;

    public GameBoard() {
        tiles = new ArrayList<Tile>();
        vertices = new ArrayList<Vertex>();
        roads = new ArrayList<Road>();
        devDeck = new ArrayList<String>();
        initBoard();
        initDevDeck();
    }

    // builds official 25 card deck nd shuffles it
    // 14 knight 2 road bldg 2 yop 2 mono 5 vp
    private void initDevDeck() {
        for (int i = 0; i < 14; i++) devDeck.add(DevCard.KNIGHT);

        for (int i = 0; i < 2; i++) devDeck.add(DevCard.ROAD_BUILDING);
        for (int i = 0; i < 2; i++) devDeck.add(DevCard.YEAR_OF_PLENTY);
        for (int i = 0; i < 2; i++) devDeck.add(DevCard.MONOPOLY);
        for (int i = 0; i < 5; i++) devDeck.add(DevCard.VICTORY_POINT);
        // fisher-yates shuffle so order is randum every game
        for (int i = devDeck.size() - 1; i > 0; i--) {
            int j = (int)(Math.random() * (i + 1));
            String tmp = devDeck.get(i);
            devDeck.set(i, devDeck.get(j));
            devDeck.set(j, tmp);
        }
    }

    // pull top card off deck, null if empty
    public String drawDevCard() {
        if (devDeck.isEmpty()) return null;
        return devDeck.remove(devDeck.size() - 1);
    }

    // how many cards r left, useful 4 bot and buy btn
    public int getDevDeckSize() {
        return devDeck.size();
    }

    // builds the whole board layout
    // pointy-top hexs, radius 65, rows: 3-4-5-4-3
    private void initBoard() {
        // center px of each tile (col spacing 113, row spacing 98)
        int[][] centers = {
            // row 0
            {237, 154}, {350, 154}, {463, 154},
            // row 1
            {181, 252}, {294, 252}, {407, 252}, {520, 252},
            // row 2 (middle)
            {124, 350}, {237, 350}, {350, 350}, {463, 350}, {576, 350},
            // row 3
            {181, 448}, {294, 448}, {407, 448}, {520, 448},
            // row 4
            {237, 546}, {350, 546}, {463, 546}
        };

        // resource per tile
        String[] resources = {
            ResourceType.ORE,   ResourceType.WOOD,  ResourceType.WOOL,
            ResourceType.WHEAT, ResourceType.BRICK, ResourceType.WOOD,  ResourceType.BRICK,
            ResourceType.WOOL,  ResourceType.WHEAT, "DESERT",           ResourceType.WOOD,  ResourceType.ORE,
            ResourceType.BRICK, ResourceType.WHEAT, ResourceType.ORE,   ResourceType.WOOD,
            ResourceType.WOOL,  ResourceType.WHEAT, ResourceType.WOOL
        };

        // dice num per tile, 0 = desert
        int[] diceNums = {
            10,  2,  9,
            12,  6,  4, 10,
             9, 11,  0,  3,  8,
             8,  3,  5,  4,
             5,  6, 11
        };

        // offset from center to each corner (radius 65)
        // top, upper-r, lower-r, bottom, lower-l, upper-l
        // 56 = round(65 * sqrt(3)/2), 33 = round(65 / 2)
        int[][] offsets = {
            {  0, -65},
            { 56, -33},
            { 56,  33},
            {  0,  65},
            {-56,  33},
            {-56, -33}
        };

        for (int i = 0; i < 19; i++) {
            int cx = centers[i][0];
            int cy = centers[i][1];

            Vertex[] verts = new Vertex[6];
            int[] xp = new int[6];
            int[] yp = new int[6];

            for (int k = 0; k < 6; k++) {
                int vx = cx + offsets[k][0];
                int vy = cy + offsets[k][1];
                // reuse corner if already exists (shared w neighbor tile)
                verts[k] = findOrCreateVertex(vx, vy);
                xp[k] = verts[k].getX();
                yp[k] = verts[k].getY();
            }

            Tile tile = new Tile(resources[i], diceNums[i], verts, xp, yp);
            tiles.add(tile);

            // tell each corner abt this tile
            for (Vertex v : verts) {
                v.addAdjacentTile(tile);
            }

            // robber starts on desert
            if (resources[i].equals("DESERT")) {
                tile.setHasRobber(true);
            }
        }   
    }

    // find exist corner near x,y or make a new one
    // tolerance 1px just in case
    private Vertex findOrCreateVertex(int x, int y) {
        for (Vertex v : vertices) {
            if (Math.abs(v.getX() - x) <= 1 && Math.abs(v.getY() - y) <= 1) {
                return v;
                
            }
        }
        Vertex newV = new Vertex(x, y); // crazy thing right here, if there is no existing vertex, we create one.
        vertices.add(newV); //

        return newV;
    }

    // all tiles w that dice num
    public ArrayList<Tile> getTilesByRoll(int roll) {
        ArrayList<Tile> result = new ArrayList<Tile>();
        for (Tile t : tiles) {
            if (t.getDiceNumber() == roll) result.add(t);
        }

        return result;
    }

    public ArrayList<Tile> getTiles() { return tiles; }
    public ArrayList<Vertex> getVertices() { return vertices; }
    public ArrayList<Road> getRoads() { return roads; }
}
