import java.util.ArrayList;

// stores all 19 tiles + shared corners
public class GameBoard {

    // all the tiles
    private ArrayList<Tile> tiles;

    // all unique corners (shared btw neighbors)
    private ArrayList<Vertex> vertices;

    public GameBoard() {
        tiles = new ArrayList<Tile>();
        vertices = new ArrayList<Vertex>();
        initBoard();
    }

    // builds the whole board layout
    // pointy-top hexs, radius 60, fits on 700x620
    // rows: 3-4-5-4-3
    private void initBoard() {
        // center px of each tile
        int[][] centers = {
            // row 0
            {246, 170}, {350, 170}, {454, 170},
            // row 1
            {194, 260}, {298, 260}, {402, 260}, {506, 260},
            // row 2 (middle)
            {142, 350}, {246, 350}, {350, 350}, {454, 350}, {558, 350},
            // row 3
            {194, 440}, {298, 440}, {402, 440}, {506, 440},
            // row 4
            {246, 530}, {350, 530}, {454, 530}
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

        // offset from center to each corner
        // top, upper-r, lower-r, bottom, lower-l, upper-l
        // 52 = round(60 * sqrt(3)/2)
        int[][] offsets = {
            {  0, -60},
            { 52, -30},
            { 52,  30},
            {  0,  60},
            {-52,  30},
            {-52, -30}
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

    // find existing corner near x,y or make a new one
    // tolerance 1px just in case
    private Vertex findOrCreateVertex(int x, int y) {
        for (Vertex v : vertices) {
            if (Math.abs(v.getX() - x) <= 1 && Math.abs(v.getY() - y) <= 1) {
                return v;
            }
        }
        Vertex newV = new Vertex(x, y);
        vertices.add(newV);
        return newV;
    }

    // all tiles w that dice num
    public ArrayList<Tile> getTilesByRoll(int roll) {
        ArrayList<Tile> result = new ArrayList<Tile>();
        for (Tile t : tiles) {
            if (t.getDiceNumber() == roll) {
                result.add(t);
            }
        }
        return result;
    }

    public ArrayList<Tile> getTiles() { return tiles; }
    public ArrayList<Vertex> getVertices() { return vertices; }
}
