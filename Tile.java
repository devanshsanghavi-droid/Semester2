import java.util.ArrayList;

// one hex on the board
public class Tile {

    // what resource it gives (or DESERT)
    private String resourceType;

    // dice roll that triggers this, 0 = desert
    private int diceNumber;

    // robber here = no resources
    private boolean hasRobber;

    // 6 corners
    private Vertex[] vertices;

    // x coords for drawing the hex polygon
    private int[] xPoints;

    // y coords for drawing the hex polygon
    private int[] yPoints;

    public Tile(String resourceType, int diceNumber, Vertex[] vertices,
                int[] xPoints, int[] yPoints) {
        this.resourceType = resourceType;
        this.diceNumber = diceNumber;
        this.hasRobber = false;
        this.vertices = vertices;
        this.xPoints = xPoints;
        this.yPoints = yPoints;
    }

    // hand out resources to ppl w buildings on this tile
    // skip if robber or desert
    public void distribute(ArrayList<Player> players) {
        if (hasRobber || resourceType.equals("DESERT"))
            return;
        for (Vertex v : vertices) {
            if (!v.isEmpty()) {
                Building b = v.getBuilding(); int amount = b.getVP();
                // getVP() = 1 for settlement, 2 for city, clever reuse
                for (int i = 0; i < amount; i++) {
                    b.getOwner().addResource(resourceType);
                }
            }
        }
    }

    public String getResourceType() { return resourceType; }
    public int getDiceNumber() { return diceNumber; }
    public boolean hasRobber() { return hasRobber; }
    public void setHasRobber(boolean r) { hasRobber = r; }
    public Vertex[] getVertices() { return vertices; }
    public int[] getXPoints() { return xPoints; }
    public int[] getYPoints() { return yPoints; }
}
