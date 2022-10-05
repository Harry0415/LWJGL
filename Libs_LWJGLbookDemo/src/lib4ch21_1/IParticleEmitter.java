package lib4ch21_1;

import java.util.List;
import lib4ch21_1.GameItem;

public interface IParticleEmitter {

    void cleanup();
    
    Particle getBaseParticle();
    
    List<GameItem> getParticles();
}
