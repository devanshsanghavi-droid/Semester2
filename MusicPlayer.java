import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;
 // i searched up how to do things like music and learned in java files so this one is more for my enjoyment.
public class MusicPlayer {
 
    private static Clip clip;
 
    public static void startMusic(String filename) {
        try {
            File soundFile = new File(filename);
            if (!soundFile.exists()) return;
 
            AudioInputStream audio = AudioSystem.getAudioInputStream(soundFile);
            clip = AudioSystem.getClip();
            clip.open(audio);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();
        } catch (Exception e) {
            // music is optional, dont crazh if we fail
        }
    }
 
    public static void stopMusic() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
            clip.close();
        }
    }
}