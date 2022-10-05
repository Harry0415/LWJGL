package opengl.lib;
import org.joml.Matrix4f;

public class Algo2TranslateRotate {
		
		private double startTime;//added by MClo
		private double divisionV=1000.0;//added by MClo
		
		public final static int JUST_STATIC_IMAGE = 1;
		public final static int TRANS_ROTATE_WITH_CIRCLING = 2;
		public final static int TRANS_DIFF_IN_XYZ = 3;
		public final static int ROTATE_ALONG_X = 4;
		public final static int ROTATE_ALONG_Y = 5;
		public final static int ROTATE_ALONG_Z = 6;
		
		
		public Algo2TranslateRotate(double _startTime, double _divisionV) {
			startTime=_startTime;
			divisionV=_divisionV;
		}


		public static void justStaticImage(Matrix4f mMat, float sphLocX, float sphLocY, float sphLocZ, float ObjLocX, float ObjLocY,
				float ObjLocZ) {
			// TODO Auto-generated method stub
			/**
			 * below 2 lines was original one for static image...
			 */
			mMat.identity();
			mMat.translate(ObjLocX, ObjLocY, ObjLocZ);
			/**
			 * below 2 lines was original one for static image...
			 */
			mMat.identity();
			mMat.translate(ObjLocX, ObjLocY, ObjLocZ);
			mMat.rotateX((float) Math.toRadians(ObjLocX));
			mMat.rotateY((float) Math.toRadians(ObjLocY));
			mMat.rotateZ((float) Math.toRadians(ObjLocZ));
		}


		public static void transRotateWithCircling(Matrix4f mMat, double tf) {
			// TODO Auto-generated method stub
			/**
			 * like planet moves around sun (in center)
			 * 
			 * @param mMat
			 * @param tf   (time factor)
			 */
					
			mMat.identity();
			mMat.translate((float) Math.sin(tf) * 4.0f, 0.0f, (float) Math.cos(tf) * 4.0f);// like planet moves around sun
																							// (in center)
			mMat.rotate((float) tf, 0.0f, 1.0f, 0.0f);
		}


		public static void translateDiffInXYZ(Matrix4f mMat, double tf, float rotateSpeedX, float rotateSpeedY,
				float rotateSpeedZ) {
			// TODO Auto-generated method stub
			/**
			 * X, Y, Z all changed.
			 */
			mMat.identity();
			mMat.translate((float) (Math.sin(.35f * tf) * 2.0), (float) (Math.sin(.52f * tf) * 2.0),
					(float) (Math.sin(.7f * tf) * 2.0));
			mMat.rotateXYZ(rotateSpeedX * (float) tf, rotateSpeedY * (float) tf, rotateSpeedZ * (float) tf); 
			// e.g use the 1.75 adjusts the
			// rotation speed
		}


		public static void rotateAlongX(Matrix4f mMat, double tf) {
			// TODO Auto-generated method stub
			/**
			 * rotate along X axis
			 */
			mMat.identity();
			mMat.translate(0.0f, 0.0f, 0.0f);
			mMat.rotate((float) tf, 1.0f, 0.0f, 0.0f);
		}


		public static void rotateAlongY(Matrix4f mMat, double tf) {
			// TODO Auto-generated method stub
			/**
			 * rotate along Y axis.
			 */
			mMat.identity();
			mMat.translate(0.0f, 0.0f, 0.0f);
			mMat.rotate((float) tf, 0.0f, 1.0f, 0.0f);
		}


		public static void rotateAlongZ(Matrix4f mMat, double tf) {
			// TODO Auto-generated method stub
			/**
			 * rotate along Z axis.
			 */
			mMat.identity();
			mMat.translate(0.0f, 0.0f, 0.0f);
			mMat.rotate((float) tf, 0.0f, 0.0f, 1.0f);
		}

		
		
	
	
	
		
	
	
		



		

		

	
		


		
}
