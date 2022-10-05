package lib4ch27_2;

import java.util.List;
import lib4ch27_2.GameItem;

public interface IParticleEmitter {

    void cleanup();
    
    Particle getBaseParticle();
    
    List<GameItem> getParticles();
}
