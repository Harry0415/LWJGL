package lib4ch20_2;

import org.joml.Vector2f;
import org.joml.Vector3f;

public class AnimVertex {

    public Vector3f position;

    public Vector2f textCoords;

    public Vector3f normal;

    public float[] weights;

    public int[] jointIndices;

    public AnimVertex() {
        super();
        normal = new Vector3f();
    }
}
