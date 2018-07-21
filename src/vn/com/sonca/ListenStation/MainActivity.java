package vn.com.sonca.ListenStation;

import java.io.File;
import java.nio.Buffer;
import java.nio.ShortBuffer;
import java.util.List;

import com.googlecode.javacv.FFmpegFrameRecorder;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.sonca.gpio.GPIOPin;
import com.sonca.gpio.PinDefinition;
import com.sonca.gpio.PinMode;

import vn.com.sonca.MyLog.MyLog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.VideoView;

public class MainActivity extends Activity {

	private final String TAG = "MainActivity";
	private final String LOG_TAG = "FFMPEG";
	
	private RelativeLayout cameraLayout;
	private String camFolderPath = "";
	private Camera mCamera;
	
	private Context context;
	
	private Button btnStart, btnStop;
	private int idxFronCamera = -1;
	private int oriFronCamera = -1;

	private Button btnRecord, btnPlay, btnSend;
	private GPIOPin gpioX16;
	
	private RelativeLayout videoLayout;
	private VideoView videoView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		context = getApplicationContext();
		
		DisplayMetrics displayMetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
		int width = displayMetrics.widthPixels;
		int height = displayMetrics.heightPixels;
		
		MyLog.e(TAG, "window width = " + width);
		MyLog.e(TAG, "window height = " + height);
		
		camFolderPath = android.os.Environment.getExternalStorageDirectory()
				.toString().concat("/DCIM/Camera");
		File camFolder = new File(camFolderPath);
		if (!camFolder.exists()) {
			camFolder.mkdirs();
		}

		cameraLayout=(RelativeLayout) findViewById(R.id.cameraLayout);
		videoLayout=(RelativeLayout) findViewById(R.id.videoLayout);
		videoView=(VideoView) findViewById(R.id.videoView);
				
		btnRecord = (Button) findViewById(R.id.btnRecord);
		btnPlay = (Button) findViewById(R.id.btnPlay);
		btnSend = (Button) findViewById(R.id.btnSend);
		
		btnRecord.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				processRecording();
			}
		});
		
		btnPlay.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				processPlayJustRecord();
			}
		});

		btnSend.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				
			}
		});
		
		// control light
		gpioX16 = new GPIOPin(PinDefinition.GPIOX16, PinMode.OUT); 
		
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		releaseCameraAndPreview();
		
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		

		try {
			if (mCamera == null) {
				idxFronCamera = getFrontCamera();
				MyLog.e(TAG, "1 idxFronCamera = " + idxFronCamera);
				if (idxFronCamera != -1) {
					mCamera = Camera.open(idxFronCamera);
				} else {
					mCamera = Camera.open();
				}
				
			}

		} catch (Exception e) {
			e.printStackTrace();
			
			try {
				idxFronCamera = -1;
				oriFronCamera = -1;
				releaseCameraAndPreview();
				
				if (mCamera == null) {
					idxFronCamera = getFrontCamera();
					MyLog.e(TAG, "2 idxFronCamera = " + idxFronCamera);
					if (idxFronCamera != -1) {
						mCamera = Camera.open(idxFronCamera);
					} else {
						mCamera = Camera.open();
					}
					
				}
			} catch (Exception e2) {
				e2.printStackTrace();
			}
			
			
		}
		
	}
	
	//---------------------------------------------
    // TODO Key
    //---------------------------------------------
	
	private int lastScanCodeUp = 0;
	private int lastScanCodeDown = 0;
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
    	int action = event.getAction(); 
		int keycode = event.getKeyCode(); 
		int scancode = event.getScanCode(); 
		
		if(scancode == 200 || scancode == 201 || scancode == 202 || scancode == 203){
			if(lastScanCodeDown != scancode){
				MyLog.e(TAG, "onKeyDown: " + action + " keycode: " + keycode + " scancode: " + scancode);
				
				lastScanCodeDown = scancode;
				lastScanCodeUp = 0;
			}
			
			return true;
		}		
						
		return super.onKeyDown(keyCode, event);
	}
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		int action = event.getAction(); 
		int keycode = event.getKeyCode(); 
		int scancode = event.getScanCode(); 
		
		if(scancode == 200 || scancode == 201 || scancode == 202 || scancode == 203){
			MyLog.e(TAG, "onKeyUp: " + action + " keycode: " + keycode + " scancode: " + scancode);
				
			if(lastScanCodeUp != scancode){
				lastScanCodeUp = scancode;
				lastScanCodeDown = 0;
				
				if(scancode == 200){ // red - record
					processRecording();
				} else if(scancode == 201){ // yellow - play
					processPlayJustRecord();
				} else if(scancode == 202){ // green - send
					
				} else if(scancode == 203){ // sensor
					
				} 
				
			}
			
			return true;
		}
		
		return super.onKeyUp(keyCode, event);
	}
	
	//---------------------------------------------
    // TODO RECORD
    //---------------------------------------------
	
	private void releaseCameraAndPreview() {
		gpioX16.low();
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

	private int getFrontCamera(){		
		for (int i=0; i< Camera.getNumberOfCameras(); i++) {
		    Camera.CameraInfo newInfo = new Camera.CameraInfo();
		    Camera.getCameraInfo(i, newInfo);
		    
		    MyLog.e(TAG, "facing = " + newInfo.facing + " -- orientation = " + newInfo.orientation);
		    
		    if (newInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
		    	oriFronCamera = newInfo.orientation;
		        return i;
		    }
		}
		
		return -1;
	}

	private String recordPath = "";
	
	private void processRecording(){
		if(mCamera == null){
			Toast.makeText(context, getString(R.string.record_3), Toast.LENGTH_SHORT).show();
			return;
		}
		
		if(recording == false){
			try {
//				gpioX16.high();
				
				MyLog.e(" "," ");
				MyLog.e(TAG, "processRecording --------------- PLAY");
				MyLog.e(" "," ");
	    		String nameTitle = "ACNOS_" + System.currentTimeMillis() + ".mp4";
	    		recordPath = camFolderPath + "/" + nameTitle;
	    		
	    		initRecorder();
	    		
	    		videoView.setVisibility(View.GONE);
				videoLayout.setVisibility(View.INVISIBLE);
				
	    		cameraView.setVisibility(View.VISIBLE);
	    		
	    		startRecording();
	            
			} catch (Exception e) {
				MyLog.e(" "," ");
				MyLog.e(TAG, "processRecording --------------- ERROR");
				MyLog.e(" "," ");
				e.printStackTrace();
			}
		} else {
			MyLog.e(" "," ");
			MyLog.e(TAG, "processRecording --------------- STOP");
			MyLog.e(" "," ");
			try {
//				gpioX16.low();
				
				stopRecording();			
                cameraView.setVisibility(View.INVISIBLE);
			
				videoLayout.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.VISIBLE);
                
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}
		
			
	}
	
	public void startRecording() {
        try {
            recorder.start();
            startTime = System.currentTimeMillis();
            recording = true;
            audioThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRecording() {
        // This should stop the audio thread from running
        runAudioThread = false;

        if (recorder != null && recording) {
            recording = false;
            Log.v(LOG_TAG, "Finishing recording, calling stop and release on recorder");
            try {
                recorder.stop();
                recorder.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            recorder = null;
        }
    }
    
	//---------------------------------------------
    // Camera thread, gets and encodes video data
    //---------------------------------------------
	class CameraView extends SurfaceView implements SurfaceHolder.Callback, PreviewCallback {

        private SurfaceHolder mHolder;
        private Camera mCamera;
        
        private int mCount = 0;

        public CameraView(Context context, Camera camera) {
            super(context);
            MyLog.e("camera","camera view");
            mCamera = camera;
            mHolder = getHolder();
            mHolder.addCallback(CameraView.this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);             
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
            	MyLog.e("camera","surfaceCreated");
                stopPreview();
    		    
                mCamera.setDisplayOrientation(getDisplayPreviewOrientation(oriFronCamera));
                
                mCamera.setPreviewDisplay(holder);
            } catch (Exception exception) {
            	exception.printStackTrace();
                mCamera.release();
                mCamera = null;
            }
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        	MyLog.e("camera","surfaceChanged");
            MyLog.e(LOG_TAG,"Setting imageWidth: " + imageWidth + " imageHeight: " + imageHeight + " frameRate: " + frameRate);
            Camera.Parameters camParams = mCamera.getParameters();
            camParams.setPreviewSize(imageWidth, imageHeight);
            
//            List<Size> listSize = camParams.getSupportedPreviewSizes();
//            for (int i = 0; i < listSize.size(); i++) {
//				Size m = listSize.get(i);
//				MyLog.e("camera", m.width + " -- " + m.height);
//			}
            
            MyLog.e(LOG_TAG,"Preview Framerate: " + camParams.getPreviewFrameRate());

            camParams.setPreviewFrameRate(frameRate);
            mCamera.setParameters(camParams);
            mCamera.setPreviewCallback(this);
            startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
            	MyLog.e("camera","surfaceDestroyed");
//            	callSurfaceDestroyed();
            } catch (Exception e) {
                // The camera has probably just been released, ignore.
            	e.printStackTrace();
            }
        }
        
        public void callSurfaceDestroyed(){
        	mHolder.removeCallback(CameraView.this);
        	mCamera.setPreviewCallback(null);
        }

        public void startPreview() {
            if (!isPreviewOn && mCamera != null) {
                isPreviewOn = true;
                mCamera.startPreview();
            }
        }

        public void stopPreview() {
            if (isPreviewOn && mCamera != null) {
                isPreviewOn = false;
                mCamera.stopPreview();
            }
        }

        @Override
        public void onPreviewFrame(final byte[] data, final Camera camera) {
            /* get video data */
        	if (yuvIplimage != null && recording) {
                try {          
//                  MyLog.e(" ", "videoRecord");
                	long t = 1000 * (System.currentTimeMillis() - startTime);

                	yuvIplimage.getByteBuffer().put(data);
                	
//                	if (t > recorder.getTimestamp()) {
                        recorder.setTimestamp(t);
//                    }
                	
                	recorder.record(yuvIplimage);
                    
                } catch (Exception e) {
                    MyLog.e(LOG_TAG,e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
	
	public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
	
	byte [] getNV21(int inputWidth, int inputHeight, Bitmap scaled) {

        int [] argb = new int[inputWidth * inputHeight];

        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte [] yuv = new byte[inputWidth*inputHeight*3/2];
        encodeYUV420SP(yuv, argb, inputWidth, inputHeight);

        scaled.recycle();

        return yuv;
    }


    void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ( (  66 * R + 129 * G +  25 * B + 128) >> 8) +  16;
                U = ( ( -38 * R -  74 * G + 112 * B + 128) >> 8) + 128;
                V = ( ( 112 * R -  94 * G -  18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
                //    pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) { 
                    yuv420sp[uvIndex++] = (byte)((V<0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte)((U<0) ? 0 : ((U > 255) ? 255 : U));
                }

                index ++;
            }
        }
    }
	
	//---------------------------------------------
    // FFMPEG
    //---------------------------------------------
	private FFmpegFrameRecorder recorder;
	    
	private int sampleAudioRateInHz = 44100;
	private int imageWidth = 320;
	private int imageHeight = 240;
	private int frameRate = 30;

	/* audio data getting thread */
	private AudioRecord audioRecord;
	private AudioRecordRunnable audioRecordRunnable;
	private Thread audioThread;
	volatile boolean runAudioThread = true;

	boolean recording = false;

	private CameraView cameraView;

	private boolean isPreviewOn = false;

	private IplImage yuvIplimage = null;

	long startTime = 0;
	
	private void initRecorder() {

    	if(mCamera == null){
    		return;
    	}
    	
        MyLog.e(LOG_TAG,"init recorder");
        
        Size previewSize = mCamera.getParameters().getPreviewSize();
        imageWidth = previewSize.width;
        imageHeight = previewSize.height;

        MyLog.i(LOG_TAG, "imageWidth: " + imageWidth);
        MyLog.i(LOG_TAG, "imageHeight: " + imageHeight);
                
//      if (imageWidth > 320) {
//			imageWidth = 960;
//			imageHeight = 720;
//		} 
        
        MyLog.i(LOG_TAG, "real imageWidth: " + imageWidth);
        MyLog.i(LOG_TAG, "real imageHeight: " + imageHeight);
        
        if (yuvIplimage == null) {
            yuvIplimage = IplImage.create(imageWidth, imageHeight, com.googlecode.javacv.cpp.opencv_core.IPL_DEPTH_8U, 2);
            MyLog.i(LOG_TAG, "create yuvIplimage");
        }

        MyLog.i(LOG_TAG, "recordPath: " + recordPath);
        recorder = new FFmpegFrameRecorder(recordPath, imageWidth, imageHeight, 1);
        recorder.setFormat("mp4");
        recorder.setSampleRate(sampleAudioRateInHz);
        recorder.setFrameRate(frameRate);

        MyLog.i(LOG_TAG, "recorder initialize success");

        audioRecordRunnable = new AudioRecordRunnable();
        audioThread = new Thread(audioRecordRunnable);

        if(cameraView == null){
            cameraView = new CameraView(context, mCamera);  
    		cameraLayout.addView(cameraView); 
        }
    }
	
	//---------------------------------------------
    // audio thread, gets and encodes audio data
    //---------------------------------------------
	class AudioRecordRunnable implements Runnable {

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            // Audio
            int bufferSize;
            short[] audioData;
            int bufferReadResult;

            bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            audioData = new short[bufferSize];

            MyLog.d(LOG_TAG, "audioRecord.startRecording()");
            audioRecord.startRecording();

            /* ffmpeg_audio encoding loop */
            while (runAudioThread) {
                //Log.v(LOG_TAG,"recording? " + recording);
                bufferReadResult = audioRecord.read(audioData, 0, audioData.length);
                if (bufferReadResult > 0) {
//                    Log.v(LOG_TAG,"bufferReadResult: " + bufferReadResult);
                    // If "recording" isn't true when start this thread, it never get's set according to this if statement...!!!
                    // Why?  Good question...
                    if (recording) {
                        try {
//                        	MyLog.e(" ", "audioRecord");
                            Buffer[] b = new Buffer[1];
                            b[0] =  ShortBuffer.wrap(audioData, 0, bufferReadResult);
                            recorder.record(b);
                            //Log.v(LOG_TAG,"recording " + 1024*i + " to " + 1024*i+1024);

                        } catch (FFmpegFrameRecorder.Exception e) {
                            MyLog.e(LOG_TAG,e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
            MyLog.e(LOG_TAG,"AudioThread Finished, release audioRecord");

            /* encoding finish, release recorder */
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                MyLog.e(LOG_TAG,"audioRecord released");
            }
        }
    }  
	
	private int getDisplayPreviewOrientation(int input){
		if(input == 0){
			return 90;
		}
		
//		if(input == 270){
//			return 90;
//		}
		
		return 0;
	}
	
    
	//---------------------------------------------
    // TODO PLAY JUST RECORD
    //---------------------------------------------
	
	private boolean playingVideo = false;
    private void processPlayJustRecord(){   	
    	if(videoView == null){
    		return;
    	}
    	
    	if(recording){
    		return;
    	}
    	
    	if(recordPath.isEmpty()){
    		return;
    	}
    	
    	File f = new File(recordPath);
    	if(!f.exists()){
    		return;
    	}
    	
    	MyLog.e(" "," ");
		MyLog.e(TAG, "processPlayJustRecord -- playingVideo = " + playingVideo);
		MyLog.e(" "," ");

    	videoLayout.setVisibility(View.VISIBLE);
    	
    	videoView.setOnPreparedListener(new OnPreparedListener() {
			
			@Override
			public void onPrepared(MediaPlayer mp) {
				
			}
		});

    	videoView.setOnCompletionListener(new OnCompletionListener() {
			
			@Override
			public void onCompletion(MediaPlayer mp) {
				playingVideo = false;
			}
		});
    	
    	videoView.setOnErrorListener(new OnErrorListener() {
			
			@Override
			public boolean onError(MediaPlayer mp, int what, int extra) {
				playingVideo = false;
				return true;
			}
		});
    	
    	if(playingVideo){
    		MyLog.e(TAG, "processPlayJustRecord -- STOP");
    		playingVideo = false;
    		videoView.stopPlayback();
    	} else {
    		MyLog.e(TAG, "processPlayJustRecord -- START");
    		MyLog.e(TAG, "recordPath = " + recordPath);
    		
    		playingVideo = true;
    		
    		videoView.stopPlayback();
    		videoView.setVideoPath(recordPath);
    		videoView.requestFocus();
    		videoView.start();
        	
    	}
    	
    	
    }
	
	
	
	
}
