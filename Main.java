// entry point, just kicks off the game
public class Main {
    public static void main(String[] args) {
        MusicPlayer.startMusic("CatanMusic.wav"); //nujabes
        GameController gc = new GameController();
        gc.startGame();
    }
}
