package net.masanoriyono.myfavoriteplace;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class MapViewActivity extends MapActivity {
	// private static final int GET_CLOSED10_COMPLETE = -1;
	private static final int GET_ROUTE_COMPLETE = -2;
	// private static final int GET_USERMAPS_COMPLETE = -3;
	// private static final int GET_MAP_POINTS_COMPLETE = -4;
	private static final int SENSOR_CHANGED = -5;
	private static final int REMOVE_COMPLETE = -6;
	private static final int GET_START = -99;
	private static final String google_api_uri = "http://maps.google.co.jp/maps/api/directions/json";
	private Thread mLooper;
	private boolean f_processing_thread = false;

	private MapView mapView;
	private GeoPoint center_point;
	private MapController mapControl;

	private LocationManager location_manager;
	private LocationListener location_listener;
	private boolean f_accuracy = false;
	private boolean f_first = false;

	private EditText edtInput;

	double current_lat;
	double current_lng;
	private GeoPoint currentGeoPoint;

	// private AlertDialog routeConfirmDialog;
	private Intent intent;
	private int p_lat;
	private int p_lng;
	boolean f_gps;
	private int dest_latE6;
	private int dest_lngE6;

//	private int closed_point_latE6;
//	private int closed_point_lngE6;
//	private GeoPoint closed_dest_point;

	// private ArrayList<Parcelable> area_points;
	private GeoPoint closed_point;

	private SQLiteOpenHelper mHelper;
	private SQLiteDatabase mWritableDb;
	private static final String MY_DB = "place.db";
	private static final int DB_VERSION = 1;
	private static final String CREATE_TABLE = "CREATE TABLE place (id integer primary key autoincrement, latitude INTEGER NOT NULL,longitude INTEGER NOT NULL,p_name TEXT NULL,p_timestamp TEXT NULL);";
	private static final String DROP_TABLE = "DROP TABLE IF EXISTS place;";

	class MapOverlay extends Overlay {
		private static final String TAG = "MapOverlay";

		private Bitmap mBitmap;

		public MapOverlay(Bitmap bitmap) {
			this.mBitmap = bitmap;
		}

		public void draw(Canvas canvas, MapView mapView, boolean shadow) {
			canvas.drawBitmap(mBitmap,
					mapView.getWidth() / 2 - mBitmap.getWidth() / 2,
					mapView.getHeight() / 2 - mBitmap.getHeight() / 2, null);
		}
		
		@Override
		public synchronized boolean onTap(GeoPoint p, MapView mapView) {
			Projection pj = mapView.getProjection();
			
			if(hitTest(pj, p)){
				setPlace();
			}
				
				
			return super.onTap(p, mapView);
		}
		
		private boolean hitTest(Projection pj, GeoPoint gp){
			Point hit = new Point();
			pj.toPixels(gp, hit);
			if( ((mapView.getWidth() / 2 - mBitmap.getWidth() / 2) < hit.x ) &&
				((mapView.getWidth() / 2 + mBitmap.getWidth() / 2) > hit.x ) &&
				((mapView.getHeight() / 2 - mBitmap.getHeight() / 2) < hit.y ) &&
				((mapView.getHeight() / 2 + mBitmap.getHeight() / 2) > hit.y )){
				
				Log.d(TAG, "hit: " + hit.x + "," + hit.y);
				return true;
			}
			
			return false;
		}
	}

	// HandleオブジェクトからUIを更新する
	private final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case GET_ROUTE_COMPLETE:

				Toast.makeText(MapViewActivity.this, (CharSequence) msg.obj,
						Toast.LENGTH_LONG).show();

				setProgressBarIndeterminateVisibility(false);
				mapView.invalidate();

				f_processing_thread = false;

				break;

			case SENSOR_CHANGED:
				if (!f_processing_thread) {
					// mapView.invalidate();
					Log.d("SENSOR_CHANGED", "refresh:" + f_processing_thread);
				} else {
					Log.d("SENSOR_CHANGED", "waiting...");
				}
				// Log.d("SENSOR_CHANGED","Thread:" + mLooper.);
				mapView.invalidate();

				break;

			case REMOVE_COMPLETE:
				mapView.invalidate();
				f_processing_thread = false;

				break;

			case GET_START:

				f_processing_thread = true;

				Log.d("GET_START", "Thread:" + msg.obj.toString());

				break;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);

		mapView = new MapView(this, getResources().getString(R.string.map_key_release));
		mapView.setEnabled(true);
		mapView.setClickable(true);
		mapView.setBuiltInZoomControls(true);
		// 地図のセンターを撮影位置にする。
		center_point = new GeoPoint(
				new Double(34.9719439293057 * 1E6).intValue(), new Double(
						138.38911074672706 * 1E6).intValue());

		// インテントから取得する予定。
		intent = getIntent();
		if (intent != null) {
			Log.i("intent", "not null");
			if ("PlacePosition".equals(intent.getAction())) {
				Bundle bundle = intent.getExtras();
				// これがnullの場合があるのにチェックせずに
				// キーの存在をチェックしていたのでエラーが起きていた。
				if (bundle != null) {
					if ((bundle.containsKey("lat"))
							&& (bundle.containsKey("lng"))) {
						// どうもgetParcelableではキーが存在しない場合はnullが戻ってくる。
						// Bitmap bitmap_Paint2SS =
						// (Bitmap)bundle.getParcelable("net.masanoriyono.Paint2SS.BITMAP");
						p_lat = intent.getIntExtra("lat", new Double(
								34.9719439293057 * 1E6).intValue());
						p_lng = intent.getIntExtra("lng", new Double(
								138.38911074672706 * 1E6).intValue());
						center_point = new GeoPoint(p_lat, p_lng);

						Log.i("PlacePosition", p_lat + "," + p_lng);
					
					}
				}
			} else if ("GPSAction".equals(intent.getAction())) {
				Bundle bundle = intent.getExtras();
				// これがnullの場合があるのにチェックせずに
				// キーの存在をチェックしていたのでエラーが起きていた。
				if (bundle != null) {
					if (bundle.containsKey("gps")) {
						f_gps = intent.getBooleanExtra("gps", true);
					}
				}
			}
		}

		location_manager = (LocationManager) getSystemService(LOCATION_SERVICE);
		location_listener = new LocationListener() {
			@Override
			public void onLocationChanged(Location location) {
				Log.d("onLocationChanged", "lat:" + location.getLatitude()
						+ " lng:" + location.getLongitude());

//				Date data_taken = new Date(location.getTime());
//				String dateFormat = "yyyy-MM-dd HH:mm:ss.SSSZ";
//
//				android.text.format.DateFormat df = new android.text.format.DateFormat();
				Date date = new Date();
				String insertDateTime = (new SimpleDateFormat(
						"yyyy-MM-dd HH:mm:ss")).format(date);

				if (location.getAccuracy() > 70) {
					f_accuracy = false;
				} else {
					String message = "緯度:"
							+ String.valueOf(location.getLatitude()) + "\n"
							+ "経度:" + String.valueOf(location.getLongitude())
							+ "\n" + "位置精度:"
							+ String.valueOf(location.getAccuracy())
							+ " m\n"
							+ "時間:" + insertDateTime;

					if (f_gps) {
						Toast.makeText(MapViewActivity.this, message,
								Toast.LENGTH_LONG).show();
					}
					
					f_accuracy = true;
					location_manager.removeUpdates(location_listener);

				}

				current_lat = location.getLatitude();
				current_lng = location.getLongitude();

				currentGeoPoint = new GeoPoint(
						(int) (location.getLatitude() * 1E6),
						(int) (location.getLongitude() * 1E6));
				if (f_gps) {
					mapControl.setCenter(currentGeoPoint);
				}
			}

			@Override
			public void onProviderDisabled(String provider) {
				Log.d("location_manager Status", "onProviderDisabled:"
						+ provider);
			}

			@Override
			public void onProviderEnabled(String provider) {
				Log.d("location_manager Status", "onProviderEnabled:"
						+ provider);
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

		mapControl = mapView.getController();
		// mapControl.setZoom(16);
		// 地図の表示範囲を指定。中心とターゲットの2点の2倍なら表示されるはず。
		mapControl.zoomToSpan(dest_latE6, dest_lngE6);
		mapControl.setCenter(center_point);

		// 画面上で固定の画像。地図をスクロールしても固定。
		Bitmap mBitmap = BitmapFactory.decodeResource(getResources(),
				R.drawable.pin2);
		MapOverlay center_view = new MapOverlay(mBitmap);
		mapView.getOverlays().add(center_view);
		
		setContentView(mapView);

	}

	// onPause時にデータベースを閉じる
	@Override
	protected void onPause() {
		super.onPause();

		mHelper.close();

		if (mWritableDb != null) {
			if (mWritableDb.isOpen()) {
				mWritableDb.close();
			}
		}

		// 位置情報の更新を止める
		location_manager.removeUpdates(location_listener);
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();

		chkGpsService();
		
		if (f_gps) {
			if (location_manager != null) {
				/*
				 * 最小で15000msec周期、最小で1mの位置変化の場合
				 * (つまり、どんなに変化しても15000msecのより短い間隔では通知されず 1mより小さい変化の場合は通知されない。)
				 */
				// locationService_.requestLocationUpdates(bestProvider_, 15000, 1,
				// listener);
	
				if (location_manager
						.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
					if (!f_first) {
						Toast.makeText(MapViewActivity.this,
								"現在地を特定するまでしばらくお待ちください。\n位置精度が70m以下になればGPSを停止します。",
								Toast.LENGTH_LONG).show();
						f_first = true;
					}
	
					Criteria criteria = new Criteria();
					// //API10のエミュレータだと以下のコメントをはずさないと動作しない。
					// //実機だと問題なし、と思ってたけれど3Gだと動作せず。
					// なのでコメントを外す。
					// 方位不要
					criteria.setBearingRequired(false);
					// 速度不要
					criteria.setSpeedRequired(false);
					// 高度不要にするとNETWORKでの捕捉になるみたい。
					criteria.setAltitudeRequired(true);
					// 要求精度
					criteria.setAccuracy(Criteria.ACCURACY_FINE);
					// criteria.setAccuracy(Criteria.ACCURACY_COARSE);
	
					// 許容電力消費
					criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
					// 費用の発生？
					criteria.setCostAllowed(false);
					final String provider = location_manager.getBestProvider(
							criteria, true);
					// 使える中で最も条件にヒットする位置情報サービスを取得する
	
					Log.d("location_manager", "provider:" + provider);
	
					location_manager.requestLocationUpdates(provider, 1000, 1,
					// provider, 0, 0,
							location_listener);
	
					Log.d("onResume", "location_manager.requestLocationUpdates");
	
				}
			}
		}

		// SQLiteOpenHelperインスタンスを取得するための情報
		// コンテキスト
		Context CONTEXT = this;
		// データベース名 / メモリ上で使用する場合は null
		String DATABASE_NAME = MY_DB;
		// CursorFactory : Cursorを作成するためのインスタンス
		CursorFactory CURSOR_FACTORY = null;
		// バージョン、1から始まる。
		// バージョンが変更されたときにコールされる
		// onUpgrade と onDowngrade の引数に渡される
		int DATABASE_VERSION = 1;

		// MySQLiteOpenHelperインスタンスを生成
		mHelper = new MySQLiteOpenHelper(CONTEXT, DATABASE_NAME,
				CURSOR_FACTORY, DATABASE_VERSION);
	}

	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();

		mLooper = new Thread();
		// スレッド処理を開始
		if (mLooper != null) {
			mLooper.start();
		}
	}

	/**
	 * GPSセンサの状態をチェック。
	 * 
	 */
	private void chkGpsService() {
		// GPSセンサーが利用可能か？
		if (!location_manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {

			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
					this);
			alertDialogBuilder
					.setMessage("GPSが有効になっていません。有効化してください。")
					.setCancelable(false)

					// GPS設定画面起動用ボタンとイベントの定義
					.setPositiveButton("GPS設定画面へ",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									Intent callGPSSettingIntent = new Intent(
											android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
									startActivity(callGPSSettingIntent);
								}
							});
			// //キャンセルボタン処理
			// alertDialogBuilder.setNegativeButton("キャンセル",
			// new DialogInterface.OnClickListener(){
			// public void onClick(DialogInterface dialog, int id){
			// dialog.cancel();
			// }
			// });
			AlertDialog alert = alertDialogBuilder.create();
			// 設定画面へ移動するかの問い合わせダイアログを表示
			alert.show();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the currently selected menu XML resource.
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.title_only, menu);

		// Disable the spinner since we've already created the menu and the user
		// can no longer pick a different menu XML.
		// mSpinner.setEnabled(false);

		return true;
	}

	private void setPlace(){
		try {
			// ダイアログ表示。
			edtInput = new EditText(this);
			// Show Dialog
			new AlertDialog.Builder(this)
					.setIcon(R.drawable.ic_launcher)
					.setTitle("ここはどこ？")
					.setView(edtInput)
					.setPositiveButton("OK",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									/* OKボタンをクリックした時の処理 */
									// new
									// AlertDialog.Builder(MapViewActivity.this)
									// .setTitle("ここは: " +
									// edtInput.getText().toString())
									// .show();
									currentGeoPoint = mapView
											.getMapCenter();
	
									SimpleDateFormat sdf = new SimpleDateFormat(
											"yyyy-MM-dd HH:mm:ss");
									String p_timestamp = sdf
											.format(new Date());
	
									Log.d("OKボタンをクリックした時",
											p_timestamp
													+ ":"
													+ edtInput.getText()
															.toString()
													+ currentGeoPoint
															.getLatitudeE6()
													+ ":"
													+ currentGeoPoint
															.getLongitudeE6());
	
									// DBへ登録処理。
									if (mWritableDb == null
											|| mWritableDb.isOpen() == false) {
										try {
											mWritableDb = mHelper
													.getWritableDatabase();
										} catch (SQLiteException e) {
											Log.d("area_list",
													"SQLiteException:"
															+ e.getMessage());
											return;
										}
									}
	
									// 現在地
									ContentValues values = new ContentValues();
									values.put("p_name", edtInput.getText()
											.toString());
									values.put("latitude",
											currentGeoPoint.getLatitudeE6());
									values.put("longitude", currentGeoPoint
											.getLongitudeE6());
									values.put("p_timestamp", p_timestamp);
									mWritableDb.insert("place", null,
											values);
	
									Toast.makeText(MapViewActivity.this,
											"地図の中央の地点を登録しました。",
											Toast.LENGTH_SHORT).show();
								}
							})
					.setNegativeButton("Cancel",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									/* Cancel ボタンをクリックした時の処理 */
								}
							}).show();
	
		} catch (UnsupportedOperationException e) {
			Log.d("lineOverlay",
					"UnsupportedOperationException:" + e.getMessage());
		}
	
		Log.v("setPlace", "complete.");
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		// Intent intent;
		Message msg;

		switch (item.getItemId()) {
		// For "Title only": Examples of matching an ID with one assigned in
		// the XML
		case R.id.menu_upper_left:
			setPlace();

			return true;

		case R.id.menu_lower_right:

			finish();

			return true;

		default:
			break;
		}

		return false;
	}

	@Override
	protected boolean isRouteDisplayed() {
		// TODO Auto-generated method stub
		return false;
	}

	/**
	 * Googleのルート検索結果の解析を別スレッドで実行。
	 * 
	 * @param dest_lat
	 *            :緯度
	 * @param dest_lng
	 *            ：経度
	 */
	public void get_route(final double dest_lat, final double dest_lng) {
		// 通信部分。
		// UIスレッドとは別スレッドでアップロードを実行
		Runnable getRouteRunnable = new Runnable() {
			// boolean running = true; // 実行状態を表すメンバ変数

			public void run() {// UIスレッドとは別スレッドでアップロードを実行
				Message msg = Message.obtain();
				msg.what = GET_START;
				msg.obj = new String("get_route");
				handler.sendMessage(msg);

				DefaultHttpClient client = new DefaultHttpClient();

				// p_lat = new Double(mapView.getMapCenter().getLatitudeE6()) /
				// 1E6;
				// p_lng = new Double(mapView.getMapCenter()
				// .getLongitudeE6()) / 1E6;

				// Log.d("get_JSON", "lat:" + current_lat + " lng:" +
				// current_lng);

				StringBuilder uri_query = new StringBuilder();
				uri_query.append(google_api_uri).append("?origin=")
						.append(p_lat).append(",").append(p_lng)
						.append("&destination=").append(dest_lat).append(",")
						.append(dest_lng).append("&sensor=false&mode=walking");

				HttpGet request = new HttpGet(uri_query.toString());

				Log.d("get_route", "URL:" + uri_query.toString());

				HttpResponse response = null;
				try {
					response = client.execute(request);
				} catch (Exception e) {
					Log.d("get_route", "Error Execute");
				}

				int status = response.getStatusLine().getStatusCode();
				if (HttpStatus.SC_OK == status) {
					try {
						ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
						response.getEntity().writeTo(outputStream);
						String data = outputStream.toString();
						Log.d("response", data);
						// //jsonp -> json
						// int first_parenthesis = data.indexOf("(");
						// data = data.substring(first_parenthesis+1);
						// int last_parenthesis = data.lastIndexOf(")");
						// data = data.substring(0,last_parenthesis);

						JSONObject rootObject = new JSONObject(data);
						// Log.d("JSONSampleActivity",
						// "rootObject:" + rootObject.toString());
						JSONArray routeArray = rootObject
								.getJSONArray("routes");
						Log.d("get_route", "routeArray:" + routeArray.length());

						JSONArray legsArray = routeArray.getJSONObject(0)
								.getJSONArray("legs");
						JSONObject legs_distance_Object = legsArray
								.getJSONObject(0).getJSONObject("distance");

						String res_route_distance = legs_distance_Object
								.getString("text");
						Log.d("get_route", "legs_distance_Object:"
								+ res_route_distance);

						JSONObject legs_duration_Object = legsArray
								.getJSONObject(0).getJSONObject("duration");
						String res_route_duration = legs_duration_Object
								.getString("text");
						Log.d("get_route", "legs_duration_Object:"
								+ res_route_duration);

						JSONArray stepsArray = legsArray.getJSONObject(0)
								.getJSONArray("steps");

						Log.d("get_route", "stepsArray:" + stepsArray);
						// GeoPoint[] start_location = new
						// GeoPoint[stepsArray.length()];
						// GeoPoint[] end_location = new
						// GeoPoint[stepsArray.length()];
						GeoPoint[] route_location = new GeoPoint[stepsArray
								.length()];

						route_location[0] = new GeoPoint((int) (stepsArray
								.getJSONObject(0)
								.getJSONObject("start_location")
								.getDouble("lat") * 1E6), (int) (stepsArray
								.getJSONObject(0)
								.getJSONObject("start_location")
								.getDouble("lng") * 1E6));

						for (int i = 1; i < stepsArray.length(); i++) {
							JSONObject jsonObject = stepsArray.getJSONObject(i);
							Log.d("get_JSON",
									"start_location:"
											+ jsonObject
													.getJSONObject("start_location"));
							Log.d("get_JSON",
									"end_location:"
											+ jsonObject
													.getJSONObject("end_location"));

							// start_location[i] = new
							// GeoPoint((int)(jsonObject.getJSONObject("start_location").getDouble("lat")
							// * 1E6)
							// ,(int)(jsonObject.getJSONObject("start_location").getDouble("lng")
							// * 1E6));
							//
							// end_location[i] = new
							// GeoPoint((int)(jsonObject.getJSONObject("end_location").getDouble("lat")
							// * 1E6)
							// ,(int)(jsonObject.getJSONObject("end_location").getDouble("lng")
							// * 1E6));

							route_location[i] = new GeoPoint((int) (jsonObject
									.getJSONObject("end_location").getDouble(
											"lat") * 1E6), (int) (jsonObject
									.getJSONObject("end_location").getDouble(
											"lng") * 1E6));
						}

						for (int j = 0; j < route_location.length; j++) {

							// if(j == stepsArray.length() - 1){
							// lineOverlay = new LineOverlay(start_location[j],
							// start_location[0]);
							// }
							// else{
							// lineOverlay = new LineOverlay(start_location[j],
							// start_location[j+1]);
							// }
							Log.d("JSON plot",
									"lat:"
											+ route_location[j].getLatitudeE6()
											+ " lng:"
											+ route_location[j]
													.getLongitudeE6());
						}
						// RouteOverlay routeOverlay = new RouteOverlay(
						// route_location);
						// mapView.getOverlays().add(routeOverlay);

						msg = Message.obtain();
						msg.what = GET_ROUTE_COMPLETE;
						msg.obj = new String("距離：" + res_route_distance
								+ "\n時間：" + res_route_duration);
						// ハンドラーに処理が終了したことを通知する
						((Handler) handler).sendMessage(msg);

					} catch (Exception e) {
						Log.e("JSON plot", e.getMessage());
					}
				}
			}
		};
		Thread getRoute_thread = new Thread(getRouteRunnable);
		getRoute_thread.start();

	}

}