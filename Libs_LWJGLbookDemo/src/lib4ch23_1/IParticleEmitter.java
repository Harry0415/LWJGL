package lib4ch23_1;

import java.util.List;
import lib4ch23_1.GameItem;

public interface IParticleEmitter {

    void cleanup();
    
    Particle getBaseParticle();
    
    List<GameItem> getParticles();
}
