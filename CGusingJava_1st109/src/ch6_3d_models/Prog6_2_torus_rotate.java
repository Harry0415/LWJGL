package ch6_3d_models;

import java.io.*;
import java.nio.*;
import java.lang.Math;
import javax.swing.*;
import static com.jogamp.opengl.GL4.*;
import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.texture.*;

import opengl.lib.Algo2TranslateRotate;
import opengl.lib.Torus;
import opengl.lib.Utils;

import com.jogamp.common.nio.Buffers;
import org.joml.*;

public class Prog6_2_torus_rotate extends JFrame implements GLEventListener
{	private GLCanvas myCanvas;
	private int renderingProgram;
	private int vao[] = new int[1];
	private int vbo[] = new int[4];
	private float cameraX, cameraY, cameraZ;
	private float torLocX, torLocY, torLocZ;
	private float sphLocX, sphLocY, sphLocZ;
	private int brickTexture;
	
	private Torus myTorus;
	private int numTorusIndices, numTorusVertices;
	
	// allocate variables for display() function
	private FloatBuffer vals = Buffers.newDirectFloatBuffer(16);
	private Matrix4f pMat = new Matrix4f();  // perspective matrix
	private Matrix4f vMat = new Matrix4f();  // view matrix
	private Matrix4f mMat = new Matrix4f();  // model matrix
	private Matrix4f mvMat = new Matrix4f(); // model-view matrix
	private int mvLoc, projLoc;
	private float aspect;
	
	// Animation and Rotate
	private double tf;
	private double startTime;
	private double elapsedTime;
	private double divisionV = 1000.0;
	// Animation and Rotate
	
	//Rotate
	private int approach4trans_rotate = Algo2TranslateRotate.ROTATE_ALONG_X;
	private float rotateSpeedX = 1.75f, 
			rotateSpeedY = 1.75f, 
			rotateSpeedZ = 1.75f;
	//Rotate

	public Prog6_2_torus_rotate()
	{	setTitle("Chapter6 - program2");
		setSize(800, 800);
		myCanvas = new GLCanvas();
		myCanvas.addGLEventListener(this);
		this.add(myCanvas);
		this.setVisible(true);
		Animator animator = new Animator(myCanvas);
		animator.start();
	}

	public void display(GLAutoDrawable drawable)
	{	GL4 gl = (GL4) GLContext.getCurrentGL();
		gl.glClear(GL_COLOR_BUFFER_BIT);
		gl.glClearColor(0.0f, 0.0f, 0.25f, 1.0f);
		gl.glClear(GL_DEPTH_BUFFER_BIT);

		gl.glUseProgram(renderingProgram);

		mvLoc = gl.glGetUniformLocation(renderingProgram, "mv_matrix");
		projLoc = gl.glGetUniformLocation(renderingProgram, "proj_matrix");

		vMat.identity().setTranslation(-cameraX,-cameraY,-cameraZ);

		// Animation and Rotate
		elapsedTime = System.currentTimeMillis() - startTime;
		tf = elapsedTime/divisionV;
		// Animation and Rotate
		
		applyApproach4trans_n_rotation();
		
//		mMat.identity();
//		mMat.translate(torLocX, torLocY, torLocZ);
//		mMat.rotateX((float)Math.toRadians(30.0f));

		mvMat.identity();
		mvMat.mul(vMat);
		mvMat.mul(mMat);

		gl.glUniformMatrix4fv(mvLoc, 1, false, mvMat.get(vals));
		gl.glUniformMatrix4fv(projLoc, 1, false, pMat.get(vals));

		gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
		gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(0);

		gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[1]);
		gl.glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);
		gl.glEnableVertexAttribArray(1);

		gl.glActiveTexture(GL_TEXTURE0);
		gl.glBindTexture(GL_TEXTURE_2D, brickTexture);

		gl.glEnable(GL_CULL_FACE);
		gl.glFrontFace(GL_CCW);

		gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vbo[3]);
		gl.glDrawElements(GL_TRIANGLES, numTorusIndices, GL_UNSIGNED_INT, 0);
	}

	public void init(GLAutoDrawable drawable)
	{	GL4 gl = (GL4) GLContext.getCurrentGL();
		renderingProgram = Utils.createShaderProgram("code/vertShader4Prog6_2.glsl", "code/fragShader4Prog6_2.glsl");

		// Rotate
		startTime = System.currentTimeMillis();
		divisionV = 1000.0;
		// Rotate
		
		aspect = (float) myCanvas.getWidth() / (float) myCanvas.getHeight();
		pMat.identity().setPerspective((float) Math.toRadians(60.0f), aspect, 0.1f, 1000.0f);
		
		setupVertices();
		
		cameraX = 0.0f; cameraY = 0.0f; cameraZ = 2.5f;
		torLocX = 0.0f; torLocY = 0.0f; torLocZ = -0.5f;
		
		brickTexture = Utils.loadTexture("images/brick1.jpg");
	}
	
	// Rotate
	public void applyApproach4trans_n_rotation() {
		if (approach4trans_rotate == Algo2TranslateRotate.JUST_STATIC_IMAGE) {
			Algo2TranslateRotate.justStaticImage(mMat, sphLocX, sphLocY, sphLocZ, 10.0f, 40.0f, 50.0f);
		}else if (approach4trans_rotate == Algo2TranslateRotate.TRANS_ROTATE_WITH_CIRCLING){
			Algo2TranslateRotate.transRotateWithCircling(mMat, tf);
		}else if (approach4trans_rotate == Algo2TranslateRotate.TRANS_DIFF_IN_XYZ) {
			Algo2TranslateRotate.translateDiffInXYZ(mMat, tf, rotateSpeedX, rotateSpeedY, rotateSpeedZ);
		}else if (approach4trans_rotate == Algo2TranslateRotate.ROTATE_ALONG_X) {
			Algo2TranslateRotate.rotateAlongX(mMat, tf);
		}else if (approach4trans_rotate == Algo2TranslateRotate.ROTATE_ALONG_Y) {
			Algo2TranslateRotate.rotateAlongY(mMat, tf);
		}else {
			Algo2TranslateRotate.rotateAlongZ(mMat, tf);
		}
	}
	// Rotate

	private void setupVertices()
	{	GL4 gl = (GL4) GLContext.getCurrentGL();
	
		myTorus = new Torus(0.5f, 0.2f, 100);
		numTorusVertices = myTorus.getNumVertices();
		numTorusIndices = myTorus.getNumIndices();
	
		Vector3f[] vertices = myTorus.getVertices();
		Vector2f[] texCoords = myTorus.getTexCoords();
		Vector3f[] normals = myTorus.getNormals();
		int[] indices = myTorus.getIndices();
		
		float[] pvalues = new float[vertices.length*3];
		float[] tvalues = new float[texCoords.length*2];
		float[] nvalues = new float[normals.length*3];

		for (int i=0; i<numTorusVertices; i++)
		{	pvalues[i*3]   = (float) vertices[i].x();
			pvalues[i*3+1] = (float) vertices[i].y();
			pvalues[i*3+2] = (float) vertices[i].z();
			tvalues[i*2]   = (float) texCoords[i].x();
			tvalues[i*2+1] = (float) texCoords[i].y();
			nvalues[i*3]   = (float) normals[i].x();
			nvalues[i*3+1] = (float) normals[i].y();
			nvalues[i*3+2] = (float) normals[i].z();
		}
		
		gl.glGenVertexArrays(vao.length, vao, 0);
		gl.glBindVertexArray(vao[0]);
		gl.glGenBuffers(4, vbo, 0);
		
		gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
		FloatBuffer vertBuf = Buffers.newDirectFloatBuffer(pvalues);
		gl.glBufferData(GL_ARRAY_BUFFER, vertBuf.limit()*4, vertBuf, GL_STATIC_DRAW);

		gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[1]);
		FloatBuffer texBuf = Buffers.newDirectFloatBuffer(tvalues);
		gl.glBufferData(GL_ARRAY_BUFFER, texBuf.limit()*4, texBuf, GL_STATIC_DRAW);

		gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[2]);
		FloatBuffer norBuf = Buffers.newDirectFloatBuffer(nvalues);
		gl.glBufferData(GL_ARRAY_BUFFER, norBuf.limit()*4, norBuf, GL_STATIC_DRAW);
		
		gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vbo[3]);
		IntBuffer idxBuf = Buffers.newDirectIntBuffer(indices);
		gl.glBufferData(GL_ELEMENT_ARRAY_BUFFER, idxBuf.limit()*4, idxBuf, GL_STATIC_DRAW);
	}

	public static void main(String[] args) { new Prog6_2_torus_rotate(); }
	public void dispose(GLAutoDrawable drawable) {}
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height)
	{	aspect = (float) myCanvas.getWidth() / (float) myCanvas.getHeight();
		pMat.identity().setPerspective((float) Math.toRadians(60.0f), aspect, 0.1f, 1000.0f);
	}
}