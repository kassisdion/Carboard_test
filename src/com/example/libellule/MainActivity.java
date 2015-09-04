package com.libellule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.EyeTransform;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

/**
 * A Cardboard sample application.
 */
public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer
{
	//debug
	private static final String TAG = "MainActivity";
	
	//camera
	private static final float CAMERA_Z = 0.01f;
	
	//lights
	private final float[] mLightPosInWorldSpace = new float[]
	{ 0.0f, 2.0f, 0.0f, 1.0f };
	
	//eyes
	private final float[] mLightPosInEyeSpace = new float[4];
	private static final int COORDS_PER_VERTEX = 3;
	private final WorldLayoutData DATA = new WorldLayoutData();
	
	//floor
	private FloatBuffer mFloorVertices;
	private FloatBuffer mFloorColors;
	private FloatBuffer mFloorNormals;
	
	//cube
	private FloatBuffer mCubeVertices;
	private FloatBuffer mCubeColors;
	private FloatBuffer mCubeFoundColors;
	private FloatBuffer mCubeNormals;
	
	
	private int mGlProgram;
	private int mPositionParam;
	private int mNormalParam;
	private int mColorParam;
	private int mModelViewProjectionParam;
	private int mLightPosParam;
	private int mModelViewParam;
	private int mModelParam;
	private int mIsFloorParam;
	private float[] mModelCube;
	private float[] mCamera;
	private float[] mView;
	private float[] mHeadView;
	private float[] mModelViewProjection;
	private float[] mModelView;
	private float[] mModelFloor;
	private float mObjectDistance = 12f;
	private float mFloorDepth = 20f;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.common_ui);
		CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
		cardboardView.setRenderer(this);
		setCardboardView(cardboardView);
		mModelCube = new float[16];
		mCamera = new float[16];
		mView = new float[16];
		mModelViewProjection = new float[16];
		mModelView = new float[16];
		mModelFloor = new float[16];
		mHeadView = new float[16];
	}

	@Override
	public void onSurfaceCreated(EGLConfig config)
	{
		Log.i(TAG, "onSurfaceCreated");
		GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well
		ByteBuffer bbVertices = ByteBuffer.allocateDirect(DATA.CUBE_COORDS.length * 4);
		bbVertices.order(ByteOrder.nativeOrder());
		mCubeVertices = bbVertices.asFloatBuffer();
		mCubeVertices.put(DATA.CUBE_COORDS);
		mCubeVertices.position(0);
		ByteBuffer bbColors = ByteBuffer.allocateDirect(DATA.CUBE_COLORS.length * 4);
		bbColors.order(ByteOrder.nativeOrder());
		mCubeColors = bbColors.asFloatBuffer();
		mCubeColors.put(DATA.CUBE_COLORS);
		mCubeColors.position(0);
		ByteBuffer bbFoundColors = ByteBuffer.allocateDirect(DATA.CUBE_FOUND_COLORS.length * 4);
		bbFoundColors.order(ByteOrder.nativeOrder());
		mCubeFoundColors = bbFoundColors.asFloatBuffer();
		mCubeFoundColors.put(DATA.CUBE_FOUND_COLORS);
		mCubeFoundColors.position(0);
		ByteBuffer bbNormals = ByteBuffer.allocateDirect(DATA.CUBE_NORMALS.length * 4);
		bbNormals.order(ByteOrder.nativeOrder());
		mCubeNormals = bbNormals.asFloatBuffer();
		mCubeNormals.put(DATA.CUBE_NORMALS);
		mCubeNormals.position(0);
		// make a floor
		ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(DATA.FLOOR_COORDS.length * 4);
		bbFloorVertices.order(ByteOrder.nativeOrder());
		mFloorVertices = bbFloorVertices.asFloatBuffer();
		mFloorVertices.put(DATA.FLOOR_COORDS);
		mFloorVertices.position(0);
		ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(DATA.FLOOR_NORMALS.length * 4);
		bbFloorNormals.order(ByteOrder.nativeOrder());
		mFloorNormals = bbFloorNormals.asFloatBuffer();
		mFloorNormals.put(DATA.FLOOR_NORMALS);
		mFloorNormals.position(0);
		ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(DATA.FLOOR_COLORS.length * 4);
		bbFloorColors.order(ByteOrder.nativeOrder());
		mFloorColors = bbFloorColors.asFloatBuffer();
		mFloorColors.put(DATA.FLOOR_COLORS);
		mFloorColors.position(0);
		int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
		int gridShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);
		mGlProgram = GLES20.glCreateProgram();
		GLES20.glAttachShader(mGlProgram, vertexShader);
		GLES20.glAttachShader(mGlProgram, gridShader);
		GLES20.glLinkProgram(mGlProgram);
		GLES20.glEnable(GLES20.GL_DEPTH_TEST);
		// Object first appears directly in front of user
		Matrix.setIdentityM(mModelCube, 0);
		Matrix.translateM(mModelCube, 0, 0, 0, -mObjectDistance);
		Matrix.setIdentityM(mModelFloor, 0);
		Matrix.translateM(mModelFloor, 0, 0, -mFloorDepth, 0); // Floor appears
																// below user
		checkGLError("onSurfaceCreated");
	}

	private int loadGLShader(int type, int resId)
	{
		String code = readRawTextFile(resId);
		int shader = GLES20.glCreateShader(type);
		GLES20.glShaderSource(shader, code);
		GLES20.glCompileShader(shader);
		// Get the compilation status.
		final int[] compileStatus = new int[1];
		GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
		// If the compilation failed, delete the shader.
		if (compileStatus[0] == 0)
		{
			Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
			GLES20.glDeleteShader(shader);
			shader = 0;
		}
		if (shader == 0)
		{
			throw new RuntimeException("Error creating shader.");
		}
		return shader;
	}

	private String readRawTextFile(int resId)
	{
		InputStream inputStream = getResources().openRawResource(resId);
		try
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null)
			{
				sb.append(line).append("\n");
			}
			reader.close();
			return sb.toString();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return "";
	}

	@Override
	public void onNewFrame(HeadTransform headTransform)
	{
		GLES20.glUseProgram(mGlProgram);
		mModelViewProjectionParam = GLES20.glGetUniformLocation(mGlProgram, "u_MVP");
		mLightPosParam = GLES20.glGetUniformLocation(mGlProgram, "u_LightPos");
		mModelViewParam = GLES20.glGetUniformLocation(mGlProgram, "u_MVMatrix");
		mModelParam = GLES20.glGetUniformLocation(mGlProgram, "u_Model");
		mIsFloorParam = GLES20.glGetUniformLocation(mGlProgram, "u_IsFloor");
		Matrix.setLookAtM(mCamera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
		headTransform.getHeadView(mHeadView, 0);
		checkGLError("onReadyToDraw");
	}

	@Override
	public void onDrawEye(EyeTransform transform)
	{
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
		mPositionParam = GLES20.glGetAttribLocation(mGlProgram, "a_Position");
		mNormalParam = GLES20.glGetAttribLocation(mGlProgram, "a_Normal");
		mColorParam = GLES20.glGetAttribLocation(mGlProgram, "a_Color");
		GLES20.glEnableVertexAttribArray(mPositionParam);
		GLES20.glEnableVertexAttribArray(mNormalParam);
		GLES20.glEnableVertexAttribArray(mColorParam);
		checkGLError("mColorParam");
		// Apply the eye transformation to the camera.
		Matrix.multiplyMM(mView, 0, transform.getEyeView(), 0, mCamera, 0);
		// Set the position of the light
		Matrix.multiplyMV(mLightPosInEyeSpace, 0, mView, 0, mLightPosInWorldSpace, 0);
		GLES20.glUniform3f(mLightPosParam, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1], mLightPosInEyeSpace[2]);
		// Build the ModelView and ModelViewProjection matrices
		// for calculating cube position and light.
		Matrix.multiplyMM(mModelView, 0, mView, 0, mModelCube, 0);
		Matrix.multiplyMM(mModelViewProjection, 0, transform.getPerspective(), 0, mModelView, 0);
		drawCube();
		// Set mModelView for the floor, so we draw floor in the correct
		// location
		Matrix.multiplyMM(mModelView, 0, mView, 0, mModelFloor, 0);
		Matrix.multiplyMM(mModelViewProjection, 0, transform.getPerspective(), 0, mModelView, 0);
		drawFloor(transform.getPerspective());
	}

	public void drawCube()
	{
		// This is not the floor!
		GLES20.glUniform1f(mIsFloorParam, 0f);
		// Set the Model in the shader, used to calculate lighting
		GLES20.glUniformMatrix4fv(mModelParam, 1, false, mModelCube, 0);
		// Set the ModelView in the shader, used to calculate lighting
		GLES20.glUniformMatrix4fv(mModelViewParam, 1, false, mModelView, 0);
		// Set the position of the cube
		GLES20.glVertexAttribPointer(mPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, mCubeVertices);
		// Set the ModelViewProjection matrix in the shader.
		GLES20.glUniformMatrix4fv(mModelViewProjectionParam, 1, false, mModelViewProjection, 0);
		// Set the normal positions of the cube, again for shading
		GLES20.glVertexAttribPointer(mNormalParam, 3, GLES20.GL_FLOAT, false, 0, mCubeNormals);
		GLES20.glVertexAttribPointer(mColorParam, 4, GLES20.GL_FLOAT, false, 0, mCubeColors);
		GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
		checkGLError("Drawing cube");
	}

	public void drawFloor(float[] perspective)
	{
		// This is the floor!
		GLES20.glUniform1f(mIsFloorParam, 1f);
		// Set ModelView, MVP, position, normals, and color
		GLES20.glUniformMatrix4fv(mModelParam, 1, false, mModelFloor, 0);
		GLES20.glUniformMatrix4fv(mModelViewParam, 1, false, mModelView, 0);
		GLES20.glUniformMatrix4fv(mModelViewProjectionParam, 1, false, mModelViewProjection, 0);
		GLES20.glVertexAttribPointer(mPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, mFloorVertices);
		GLES20.glVertexAttribPointer(mNormalParam, 3, GLES20.GL_FLOAT, false, 0, mFloorNormals);
		GLES20.glVertexAttribPointer(mColorParam, 4, GLES20.GL_FLOAT, false, 0, mFloorColors);
		GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
		checkGLError("drawing floor");
	}

	private static void checkGLError(String func)
	{
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR)
		{
			Log.e(TAG, func + ": glError " + error);
			throw new RuntimeException(func + ": glError " + error);
		}
	}

	@Override
	public void onRendererShutdown()
	{
		Log.i(TAG, "onRendererShutdown");
	}

	@Override
	public void onSurfaceChanged(int width, int height)
	{
		Log.i(TAG, "onSurfaceChanged");
	}

	@Override
	public void onFinishFrame(Viewport viewport)
	{
		Log.i(TAG, "onFinishFrame");
	}

	@Override
	public void onCardboardTrigger()
	{
		Log.i(TAG, "onCardboardTrigger");
	}
}