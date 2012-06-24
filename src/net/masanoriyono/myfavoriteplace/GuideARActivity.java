package net.masanoriyono.myfavoriteplace;

import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class GuideARActivity extends Activity implements SensorEventListener,
		LocationListener {
	private MyView mView;
	private SensorManager mSensorManager = null;
	private LocationManager mLocationManager = null;
	private LocationListener location_listener;
	private LinearLayout.LayoutParams arLayoutParams;
	private ImageView imageView;
	private LinearLayout arLayout;
	private CameraView mCameraPreview;
	private Camera mCamera;
	
	private Intent intent;
	private double dest_lat;
	private double dest_lng;
	
	private int dest_direction;
	
	private boolean mIsMagSensor;
    private boolean mIsAccSensor;
    
    private boolean f_getCurrentLocation = false;
    
    private static final int MATRIX_SIZE = 16;
    /* 回転行列 */
    float[]  inR = new float[MATRIX_SIZE];
    float[] outR = new float[MATRIX_SIZE];
    float[]    I = new float[MATRIX_SIZE];
 
    /* センサーの値 */
    float[] orientationValues   = new float[3];
    float[] magneticValues      = new float[3];
    float[] accelerometerValues = new float[3];
    
    private GeomagneticField geomagnetic;
    /**
     * CameraView
     */
    class CameraView extends SurfaceView implements SurfaceHolder.Callback {

    	CameraView(Context context) {
    		super(context);

    		SurfaceHolder mHolder = getHolder();
    		mHolder.addCallback(this);
    		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    	}

    	/**
    	 * Surfaceに変化があった場合に呼ばれる
    	 */
    	public void surfaceChanged(SurfaceHolder holder, int format, int width,
    			int height) {
    		Camera.Parameters parameters = mCamera.getParameters();
    		
    		android.view.ViewGroup.LayoutParams lp = getLayoutParams();
    		int ch = parameters.getPreviewSize().height;
    		int cw = parameters.getPreviewSize().width;

    		Log.d("surfaceChanged","width:" + width
    				+ " height:" + height
    				+ " getPreviewSize().width:" + cw
    				+ " getPreviewSize().height:" + ch);
    		if(ch/cw > height/width){
    			lp.width = width;
    			lp.height = width*ch/cw;
    		}else{
    			lp.width = width;
    			lp.height = width*ch/cw;
    		}
    		
    		setLayoutParams(lp);
    		mCamera.setParameters(parameters);
    		mCamera.startPreview();
    	}
    	
    	
    	/**
    	 * Surfaceが生成された際に呼ばれる
    	 */
    	public void surfaceCreated(SurfaceHolder holder) {
    		if (mCamera != null) {
                return;
            }
            mCamera = Camera.open();
            try {
    			mCamera.setPreviewDisplay(holder);
    		} catch (Exception exception) {
    			mCamera.release();
    			mCamera = null;
    		}
    	}

    	/**
    	 * Surfaceが破棄された場合に呼ばれる
    	 */
    	public void surfaceDestroyed(SurfaceHolder holder) {
    		if (mCamera != null) {
    	        mCamera.stopPreview();
    	        mCamera.release();
                mCamera = null;
    	    }
    	}
    }
    
    /**
     * オーバーレイ描画用のクラス
     */
    class MyView extends View {

    	private int mYaw;
    	private int mRoll;
    	private int mPitch;

    	private double mLat;
    	private double mLon;
    	private double mBearing;

    	private int mCurX;
    	private int mCurY;

    	/**
    	 * コンストラクタ
    	 * 
    	 * @param c
    	 */
    	public MyView(Context c) {
    		super(c);
    		setFocusable(true);
    	}

    	/**
    	 * 描画処理
    	 */
    	protected void onDraw(Canvas canvas) {
    		super.onDraw(canvas);

    		/* 背景色を設定 */
    		canvas.drawColor(Color.TRANSPARENT);

    		/* 描画するための線の色を設定 */
    		Paint mPaint = new Paint();
    		mPaint.setStyle(Paint.Style.FILL);
    		mPaint.setTextSize(18);
    		mPaint.setARGB(255, 255, 255, 100);

    		/* 文字を描画 */
    		canvas.drawText("curX: " + mCurX, 20, 20, mPaint);
    		canvas.drawText("curY: " + mCurY, 80, 20, mPaint);

    		canvas.drawText("mYaw: " + mYaw, 20, 45, mPaint);
    		canvas.drawText("mRoll: " + mRoll, 120, 45, mPaint);
    		canvas.drawText("mPitch: " + mPitch, 200, 45, mPaint);

    		canvas.drawText("Latitude: " + mLat, 20, 70, mPaint);
    		canvas.drawText("Longitude: " + mLon, 20, 95, mPaint);
    		
    		canvas.drawText("Bearing: " + mBearing , 20, 120, mPaint);
    	}


    	public void onOrientationChanged(int yaw, int roll, int pitch) {
    		mYaw = yaw;
    		mRoll = roll;
    		mPitch = pitch;
    		invalidate();
    	}

    	public void onGpsChanged(double lat, double lon, double bearing) {
    		mLat = lat;
    		mLon = lon;
    		mBearing = bearing;
    		invalidate();
    	}

    	/**
    	 * タッチイベント
    	 */
    	public boolean onTouchEvent(MotionEvent event) {

    		/* X,Y座標の取得 */
    		mCurX = (int) event.getX();
    		mCurY = (int) event.getY();
    		/* 再描画の指示 */
    		invalidate();

    		return true;
    	}
    }
    
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
//		GuideARActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		
		// インテントから取得する予定。
		intent = getIntent();
		if (intent != null) {
			Log.i("intent", "not null");
			if ("DestARLocation".equals(intent.getAction())) {
				Bundle bundle = intent.getExtras();
				// これがnullの場合があるのにチェックせずに
				// キーの存在をチェックしていたのでエラーが起きていた。
				if (bundle != null) {
					if ((bundle.containsKey("dest_lat"))
							&& (bundle.containsKey("dest_lng"))) {

						dest_lat = intent.getDoubleExtra("dest_lat",
								34.9719439293057);
						dest_lng = intent.getDoubleExtra("dest_lng",
								138.38911074672706);
						Log.i("DestARLocation",
								"pos: " + dest_lat + ","+ dest_lng);

					}
				}
			}
		}
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		mCameraPreview = new CameraView(this);
		setContentView(mCameraPreview);

		mView = new MyView(this);
		addContentView(mView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		imageView = new ImageView(this);
		imageView.setImageResource(R.drawable.pin3);
		imageView.setScaleType(ImageView.ScaleType.CENTER);
		imageView.setVisibility(View.INVISIBLE);
		
		// ImageViewのLayoutParams
		arLayoutParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);
		arLayoutParams.setMargins(0, 0, 0, 0);
		
		// ImageViewを張り付けるLayout
		arLayout = new LinearLayout(this);
		arLayout.addView(imageView, arLayoutParams);
		
		// ImageViewを張り付けたLayoutを画面にはりつけ　
		addContentView(arLayout, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		
		// LocationManagerでGPSの値を取得するための設定
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		// 値が変化した際に呼び出されるリスナーの追加
		location_listener = new LocationListener() {
        	@Override
        	public void onLocationChanged(Location location) {
        		
        		Location destLocation = new Location("dest");
        		destLocation.setLatitude(dest_lat);
        		destLocation.setLongitude(dest_lng);
        		
        		Log.d("onLocationChanged", "lat:" + location.getLatitude() + " lng:"
        				+ location.getLongitude());
        		
        		geomagnetic = new GeomagneticField(
        				new Double(location.getLatitude()).floatValue(),
        				new Double(location.getLongitude()).floatValue(),
        				new Double(location.getAltitude()).floatValue(),
        				new Date().getTime());
        		
        		double l_direction = (double)location.bearingTo(destLocation);
        		
        		mView.onGpsChanged(location.getLatitude(), location.getLongitude(),l_direction);
        		
        		dest_direction = (int)l_direction;
        		
        		f_getCurrentLocation = true;
        	}
        	@Override
			public void onProviderDisabled(String provider) {
				Log.d("location_manager Status", "onProviderDisabled:" + provider);
			}
			@Override
			public void onProviderEnabled(String provider) {
				Log.d("location_manager Status", "onProviderEnabled:" + provider);
			}
			@Override
			public void onStatusChanged(String provider, int status,
					Bundle extras) {
				switch (status) {
				case LocationProvider.AVAILABLE:
					Log.d("location_manager Status", "AVAILABLE");
					break;
				case LocationProvider.OUT_OF_SERVICE:
					Log.d("location_manager Status", "OUT_OF_SERVICE");
					break;
				case LocationProvider.TEMPORARILY_UNAVAILABLE:
					Log.d("location_manager Status", "TEMPORARILY_UNAVAILABLE");
					break;
				}
			}
        };
	}
	
	public void onSensorChanged(SensorEvent event) {
		if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) return;
		
		switch (event.sensor.getType()) {
        case Sensor.TYPE_MAGNETIC_FIELD:
            magneticValues = event.values.clone();
            break;
        case Sensor.TYPE_ACCELEROMETER:
            accelerometerValues = event.values.clone();
            break;
	    }
	 
	    if (magneticValues != null && accelerometerValues != null && f_getCurrentLocation) {
	 
	        SensorManager.getRotationMatrix(inR, I, accelerometerValues, magneticValues);
	 
	        //Activityの表示が縦固定の場合。横向きになる場合、修正が必要です
	        SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR);
	        SensorManager.getOrientation(outR, orientationValues);
	        
//	        mView.onOrientationChanged((int) radianToDegree(orientationValues[0]),
//	        		(int) radianToDegree(orientationValues[1]),
//	        		(int) radianToDegree(orientationValues[2]));
	        
	        mView.onOrientationChanged(
	        		(int) (radianToDegree(orientationValues[0]) + geomagnetic.getDeclination()),
	        		(int) radianToDegree(orientationValues[1]),
	        		(int) radianToDegree(orientationValues[2]));
	        
//	        Log.v("Orientation",
//	            String.valueOf( radianToDegree(orientationValues[0]) ) + ", " + //Z軸方向,azmuth
//	            String.valueOf( radianToDegree(orientationValues[1]) ) + ", " + //X軸方向,pitch
//	            String.valueOf( radianToDegree(orientationValues[2]) ) );       //Y軸方向,roll
	        
//	        WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
//			// ディスプレイのインスタンス生成
//			Display disp = wm.getDefaultDisplay();
			
			imageView.setVisibility(View.VISIBLE);
			
			// ImageViewを位置を移動
			arLayoutParams.setMargins(
					(int) (dest_direction - (int) (radianToDegree(orientationValues[0]) + geomagnetic.getDeclination()))*27 
					+ mView.getWidth()/2 -imageView.getWidth()/2
					,10, 0, 0);
			
			// Layoutを更新
			arLayout.updateViewLayout(imageView, arLayoutParams);
	    }
	}
	
	float radianToDegree(float rad){
	    return (float) Math.floor( Math.toDegrees(rad) ) ;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		// センサの取得
        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
 
        // センサマネージャへリスナーを登録(implements SensorEventListenerにより、thisで登録する)
        for (Sensor sensor : sensors) {
 
            if( sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            	mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
                mIsMagSensor = true;
            }
 
            if( sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            	mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
                mIsAccSensor = true;
            }
        }
		
		if (mLocationManager != null) {
			/*
			 * 最小で15000msec周期、最小で1mの位置変化の場合
			 * (つまり、どんなに変化しても15000msecのより短い間隔では通知されず 1mより小さい変化の場合は通知されない。)
			 */
			// locationService_.requestLocationUpdates(bestProvider_, 15000, 1,
			// listener);

			if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				Criteria criteria = new Criteria();
//				//API10のエミュレータだと以下のコメントをはずさないと動作しない。
//				//実機だと問題なし、と思ってたけれど3Gだと動作せず。
				//なのでコメントを外す。
				//方位不要
				criteria.setBearingRequired(true);					
				//速度不要
				criteria.setSpeedRequired(false);					
				//高度不要にするとNETWORKでの捕捉になるみたい。
				criteria.setAltitudeRequired(true);
				//要求精度
				criteria.setAccuracy(Criteria.ACCURACY_FINE);
//				criteria.setAccuracy(Criteria.ACCURACY_COARSE);
				
				//許容電力消費
			    criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
			    //費用の発生？
			    criteria.setCostAllowed(false);
			    final String provider = mLocationManager.getBestProvider(criteria, true);
			    mLocationManager.requestLocationUpdates(
//						provider, 1000, 1,
						provider, 0, 0,
						location_listener);
			}
		}
		
	}

	@Override
	protected void onPause() {
//		if (mRegisteredSensor) {
//			mSensorManager.unregisterListener(this);
//			mRegisteredSensor = false;
//		}
		
		if (mIsMagSensor || mIsAccSensor) {
	        mSensorManager.unregisterListener(this);
	        mIsMagSensor = false;
	        mIsAccSensor = false;
	    }
		
		if (mLocationManager != null) {
			mLocationManager.removeUpdates(location_listener);
		}
		

		super.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
		mSensorManager.unregisterListener(this);
		
	}
	public void onDestroy() {
		super.onDestroy();
		if (mSensorManager != null) {
			mSensorManager.unregisterListener(this);
		}
		if (mLocationManager != null) {
			mLocationManager.removeUpdates(location_listener);
		}
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

	public void onLocationChanged(Location location) {
		// TODO Auto-generated method stub

	}

	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub

	}

	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub

	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub

	}
	
	@Override
	public boolean dispatchKeyEvent(KeyEvent e) {
		// 戻るボタンが押されたとき
		if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
			// ボタンが押されたとき
			if (e.getAction() == KeyEvent.ACTION_DOWN) {
				// アクティビティの終了

				finish();
			}
			// ボタンが離されたとき
			else if (e.getAction() == KeyEvent.ACTION_UP) {

			}

		}

		return super.dispatchKeyEvent(e);
	}
}
