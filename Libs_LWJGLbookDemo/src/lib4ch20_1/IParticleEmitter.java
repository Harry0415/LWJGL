package lib4ch20_1;

import java.util.List;
import lib4ch20_1.GameItem;

public interface IParticleEmitter {

    void cleanup();
    
    Particle getBaseParticle();
    
    List<GameItem> getParticles();
}
