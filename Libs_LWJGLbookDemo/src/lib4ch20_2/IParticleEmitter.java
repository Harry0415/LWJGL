package lib4ch20_2;

import java.util.List;
import lib4ch20_2.GameItem;

public interface IParticleEmitter {

    void cleanup();
    
    Particle getBaseParticle();
    
    List<GameItem> getParticles();
}
