// road segment between two vertices
public class Road {
    private Player owner;
    private Vertex v1, v2;

    public Road(Player owner, Vertex v1, Vertex v2) {
        this.owner = owner;
        this.v1 = v1;
        this.v2 = v2;
    }

    public Player getOwner() { return owner; }
    public Vertex getV1() {return v1;

    }
    public Vertex getV2()
    {
        return v2;
    }
}
