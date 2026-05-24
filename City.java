// city = 2 vp, cant upgrade further
public class City extends Building {

    public City(Player owner, Vertex location) {
        super(owner, location);
    }

    // 2 pts
    public int getVP() {
        return 2;
    }
}
