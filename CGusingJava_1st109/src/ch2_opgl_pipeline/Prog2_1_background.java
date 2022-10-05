package ch2_opgl_pipeline;
import javax.swing.*;
import static com.jogamp.opengl.GL4.*;
import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import opengl.lib.Utils;
public class Prog2_1_background extends JFrame implements GLEventListener
{	private GLCanvas myCanvas;

	public Prog2_1_background()
	{	setTitle("Chapter 2 - program 1");
		setSize(800,600);
		setLocation(200,200);
		myCanvas = new GLCanvas();
		myCanvas.addGLEventListener(this);
		this.add(myCanvas);
		this.setVisible(true);
	}

	public void display(GLAutoDrawable drawable)
	{	GL4 gl = (GL4) GLContext.getCurrentGL();
		gl.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
		gl.glClear(GL_COLOR_BUFFER_BIT);
	}

	public static void main(String[] args)
	{ new Prog2_1_background();
	}

	public void init(GLAutoDrawable drawable) {}
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {}
	public void dispose(GLAutoDrawable drawable) {}
}