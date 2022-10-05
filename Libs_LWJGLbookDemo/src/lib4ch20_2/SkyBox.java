package lib4ch20_2;

import lib4ch20_2.GameItem;
import lib4ch20_2.Material;
import lib4ch20_2.Mesh;
import lib4ch20_2.OBJLoader;
import lib4ch20_2.Texture;

public class SkyBox extends GameItem {

    public SkyBox(String objModel, String textureFile) throws Exception {
        super();
        Mesh skyBoxMesh = OBJLoader.loadMesh(objModel);
        Texture skyBoxtexture = new Texture(textureFile);
        skyBoxMesh.setMaterial(new Material(skyBoxtexture, 0.0f));
        setMesh(skyBoxMesh);
        setPosition(0, 0, 0);
    }
}
