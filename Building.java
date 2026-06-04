// parent class for settlement + city, cant use directly
public abstract class Building {

    // ho owns it
    private Player owner;

    // which corner its on
    private Vertex location;

    // sets owner + location
    public Building(Player owner, Vertex location) {
        this.owner = owner;

        this.location = location;
    }

    // 1 for settlement, 2 for citysssssssss overridden by subclass
    public abstract int getVP();
//
    public Player getOwner() {
        return owner;
    }
    public Vertex getLocation() { return location; }
}
