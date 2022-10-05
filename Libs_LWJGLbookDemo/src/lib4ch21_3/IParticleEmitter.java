package lib4ch21_3;

import java.util.List;
import lib4ch21_3.GameItem;

public interface IParticleEmitter {

    void cleanup();
    
    Particle getBaseParticle();
    
    List<GameItem> getParticles();
}
