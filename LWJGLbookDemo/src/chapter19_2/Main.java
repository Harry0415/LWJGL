package chapter19_2;

//import org.lwjglb.engine.GameEngine;
//import org.lwjglb.engine.IGameLogic;
//import org.lwjglb.engine.Window;
import lib4ch19_2.*;

public class Main {
 
    public static void main(String[] args) {
        try {
            boolean vSync = true;
            IGameLogic gameLogic = new DummyGame();
            Window.WindowOptions opts = new Window.WindowOptions();
            GameEngine gameEng = new GameEngine("GAME", vSync, opts, gameLogic);
            gameEng.run();
        } catch (Exception excp) {
            excp.printStackTrace();
            System.exit(-1);
        }
    }
}