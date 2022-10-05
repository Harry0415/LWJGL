package ch2_opgl_pipeline;
import javax.swing.*;
import static com.jogamp.opengl.GL4.*;
import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;

public class supplemental_queryingOpenGL extends JFrame implements GLEventListener
{	private GLCanvas myCanvas;

	public supplemental_queryingOpenGL()
	{	
		setTitle("Chapter 2 - supplemental notes");
		setSize(100,100);
		setLocation(300,300);
		myCanvas = new GLCanvas();
		myCanvas.addGLEventListener(this);
		this.add(myCanvas);
		this.setVisible(true);
	}

	public void display(GLAutoDrawable drawable) {}

	public void init(GLAutoDrawable drawable)
	{	GL4 gl = (GL4) GLContext.getCurrentGL();

		int[] size = new int[2];
		//float[] size = new float[2];

		gl.glGetIntegerv(GL_MAX_TEXTURE_IMAGE_UNITS, size, 0);
		//gl.glGetFloatv(GL_POINT_SIZE_RANGE, size, 0);

		System.out.println(size[0]);
		System.out.println(size[1]);
	}
	
	public static void main(String[] args) { new supplemental_queryingOpenGL(); }
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {}
	public void dispose(GLAutoDrawable drawable) {}
}