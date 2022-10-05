package lib4ch19_3;

import lib4ch19_3.GameItem;
import lib4ch19_3.Material;
import lib4ch19_3.Mesh;
import lib4ch19_3.OBJLoader;
import lib4ch19_3.Texture;

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
