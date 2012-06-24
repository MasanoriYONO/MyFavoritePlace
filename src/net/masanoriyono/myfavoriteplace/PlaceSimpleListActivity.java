package net.masanoriyono.myfavoriteplace;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;

public class PlaceSimpleListActivity extends Activity implements DialogInterface.OnClickListener{
	protected static final String TAG = "PlaceSimpleListActivity";

	private LocationManager location_manager;
	private LocationListener location_listener;

	private ListView area_list;


	double current_lat;
	double current_lng;
	
	boolean f_gps;
	
	private GeoPoint currentGeoPoint;


	private List<CustomData> listdata;
	private List<Place> place_list;
	private static final int MY_MAP = 2;
	private static final int MY_AR = 3;
	private boolean f_first = false;
	private boolean f_accuracy = false;
	private Menu mMenu;
	
	private Intent intent;
	
	private String[] area_size = null;
	
	private SQLiteOpenHelper mHelper;
	private SQLiteDatabase mWritableDb;
	private static final String MY_DB = "place.db";
	private static final int DB_VERSION = 1;
	private static final String CREATE_TABLE = "CREATE TABLE place (id integer primary key autoincrement, latitude INTEGER NOT NULL,longitude INTEGER NOT NULL,p_name TEXT NULL,p_timestamp TEXT NULL);";
	private static final String DROP_TABLE = "DROP TABLE IF EXISTS place;";
	
	private AlertDialog listrowSelectDialog;
    private AlertDialog.Builder listrowDialogBuilder;
    private int selected_index; 
    ListPlaceAdapter adapter;
    
	class RouteComparator implements Comparator<Object> {
		public static final int ASC = 1; // 昇順
		public static final int DESC = -1; // 降順
		private int sort = ASC; // デフォルトは昇順

		public RouteComparator() {

		}

		/**
		 * @param sort
		 *            StringComparator.ASC | StringComparator.DESC。昇順や降順を指定します。
		 */
		public RouteComparator(int sort) {
			this.sort = sort;
		}

		public int compare(Object arg0, Object arg1) {
			CustomData t_custom0 = (CustomData) arg0;
			CustomData t_custom1 = (CustomData) arg1;
			if (t_custom0.getDistance() > t_custom1.getDistance()) {
				return 1 * sort;
			} else if (t_custom0.getDistance() == t_custom1.getDistance()) {
				return 0;
			} else {
				return -1 * sort;
			}
		}

	}
	
	private class Place {
		private String name;
		private String timestamp;

		public Place(String name, String timestamp) {
			this.name = name;
			this.timestamp = timestamp;
		}

		public String getName() {
			return name;
		}

		public String getTimeStamp() {
			return timestamp;
		}
	}

	private class ListPlaceAdapter extends BaseAdapter {
		private Context context;

		public ListPlaceAdapter(Context context) {
			super();
			this.context = context;			
		}

		@Override
		public int getCount() {
			return place_list.size();
		}

		@Override
		public Object getItem(int position) {

			return place_list.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			Place place = (Place) getItem(position);

			if (convertView == null) {

				TextView textView1;
			    TextView textView2;
			      
				LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				convertView = inflater.inflate(R.layout.list_row2, null);
				if(place != null){
					textView1 = (TextView) convertView.findViewById(R.id.textView1);
			        textView2 = (TextView) convertView.findViewById(R.id.textView2);
			        
			        textView1.setText(place.getName());
			        textView2.setText(place.getTimeStamp());
				}

			} else {

			}

			return convertView;
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.list_menu, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case R.id.list_menu_upper_left:

			try {

				// DBの内容を削除。
				if (mWritableDb == null || mWritableDb.isOpen() == false) {
					try {
						mWritableDb = mHelper.getWritableDatabase();
					} catch (SQLiteException e) {
						Log.d("places","SQLiteException:" + e.getMessage());
						
					}
				}
				
				mWritableDb.delete("place", null, null);
				
				Toast.makeText(PlaceSimpleListActivity.this,
						"登録情報を全削除しました。",
						Toast.LENGTH_SHORT).show();

			} catch (UnsupportedOperationException e) {
				Log.d("lineOverlay",
						"UnsupportedOperationException:" + e.getMessage());
			}

			Log.v("list_menu_upper_left", "" + R.string.list_menu_upper_left);

			return true;


		case R.id.list_menu_lower_right:

			finish();

			return true;

		default:
			break;
		}

		return false;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// インテントから取得する予定。
		intent = getIntent();
		if (intent != null) {
			Log.i("intent", "not null");
			if ("GPSAction".equals(intent.getAction())) {
				Bundle bundle = intent.getExtras();
				// これがnullの場合があるのにチェックせずに
				// キーの存在をチェックしていたのでエラーが起きていた。
				if (bundle != null) {
					if (bundle.containsKey("gps")) {
						f_gps = intent.getBooleanExtra("gps", false);
					}
				}
			}
		}
		
		String[] str_items = {"削除する","ARで方角を見る"};
		listrowDialogBuilder = new AlertDialog.Builder(this);
		listrowDialogBuilder.setTitle("この地点の情報？");
		listrowDialogBuilder.setCancelable(true);
		listrowDialogBuilder.setItems(str_items, this);
        
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		setContentView(R.layout.simple_list);

		area_list = (ListView)findViewById(R.id.simple_listView);
		//area_list = new ListView(this);
		
		//setContentView(area_list);

		
		
		area_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {

				CustomData temp = listdata.get(position);


				Intent intent2Map = new Intent(PlaceSimpleListActivity.this,
						MapViewActivity.class);

				intent2Map.setAction("PlacePosition");
				intent2Map.putExtra("lat", temp.getCurrentPoint().getLatitudeE6());
				intent2Map.putExtra("lng", temp.getCurrentPoint().getLongitudeE6());
				
				Log.i("PlacePosition", temp.getCurrentPoint().getLatitudeE6() + ","
						+ temp.getCurrentPoint().getLongitudeE6());
				
				startActivityForResult(intent2Map, MY_MAP);
			}
		});
		
		//長押し
		area_list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener(){
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {
				// TODO Auto-generated method stub
				
				
				listrowSelectDialog = listrowDialogBuilder.create();
		        // アラートダイアログを表示します
				listrowSelectDialog.show();
				
				
				selected_index = position;
				
				//Clickイベントに伝播させない時はtrue,伝えたい時はfalse
				return true;
			}
			
		});
		
		location_manager = (LocationManager) getSystemService(LOCATION_SERVICE);
		location_listener = new LocationListener() {
        	@Override
        	public void onLocationChanged(Location location) {
        		Log.d("onLocationChanged", "lat:" + location.getLatitude() + " lng:"
        				+ location.getLongitude());

        		Date date = new Date();
        		String insertDateTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
        				.format(date);
        		
        		if(location.getAccuracy() > 70){
        			f_accuracy = false;
        		}
        		else{
        			String message = "緯度:" + String.valueOf(location.getLatitude()) + "\n"
        					+ "経度:" + String.valueOf(location.getLongitude()) + "\n"
        					+ "位置精度:" + String.valueOf(location.getAccuracy()) + " m\n"
        					+ "時間:" + insertDateTime;

        			
        			Toast.makeText(PlaceSimpleListActivity.this, message, Toast.LENGTH_LONG).show();
        			
        			f_accuracy = true;
        			location_manager.removeUpdates(location_listener);
        			
        		}
        			
        		current_lat = location.getLatitude();
        		current_lng = location.getLongitude();

        		currentGeoPoint = new GeoPoint((int) (location.getLatitude() * 1E6),
        				(int) (location.getLongitude() * 1E6));
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
		
		if (mWritableDb == null || mWritableDb.isOpen() == false) {
			try {
				mWritableDb = mHelper.getWritableDatabase();
			} catch (SQLiteException e) {
				Log.d("MapView",
						"SQLiteException:" + e.getMessage());
				return;
			}
		}
		
		String SQL_SELECT = "SELECT id,latitude,longitude,p_name,p_timestamp FROM place ;";

		Cursor mCursor = mWritableDb.rawQuery(SQL_SELECT, null);
		Log.d("MapView","count:" + mCursor.getCount());
		listdata = new ArrayList<CustomData>();
		if (mCursor.moveToFirst()) {
			int i=0;
			do {
				int t_id = mCursor.getInt(mCursor.getColumnIndex("id"));
				int t_lat = mCursor.getInt(mCursor.getColumnIndex("latitude"));
				int t_lng = mCursor.getInt(mCursor.getColumnIndex("longitude"));
			    String t_p_name = mCursor.getString(mCursor.getColumnIndex("p_name"));
			    String t_p_timestamp = mCursor.getString(mCursor.getColumnIndex("p_timestamp"));
			    
			    GeoPoint t_geo = new GeoPoint(t_lat,t_lng);
				//for (int i = 0; i < closed10.length; i++) {
				CustomData t_custom = new CustomData(t_id,t_p_name,t_geo,t_p_timestamp);
					
				listdata.add(t_custom);

			    Log.d("MapView","id:" + t_id
			    		+ " latitude:" + t_lat
			    		+ " longitude:" + t_lng
			    		+ " p_name:" + t_p_name
			    		+ " t_p_timestamp:" + t_p_timestamp);
			    
			    //area_pin[i] = new GeoPoint(t_lat,t_lng);
			    
			    i++;
			}while(mCursor.moveToNext());
		} else {
			//検索結果が無い
		}
		
		area_size = new String[listdata.size()];
		
		place_list = new ArrayList<Place>();
		
		for (int i = 0; i < listdata.size(); i++) {
			area_size[i] = listdata.get(i).getMapName();
			place_list.add(new Place(listdata.get(i).getMapName(), listdata.get(i).getAreaInfo()));
		}
		
		area_list.setAdapter(new ListPlaceAdapter(this));
		
		setProgressBarIndeterminateVisibility(false);

	}
	
	@Override
	public void onStop() {
		super.onStop();
		Log.d("onStop", "GPS Stop");
	}

	// onPause時にデータベースを閉じる
	@Override
	protected void onPause() {
		super.onPause();

		mHelper.close();

		if (mWritableDb != null) {
			if(mWritableDb.isOpen()){
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
				
				//最小で15000msec周期、最小で1mの位置変化の場合
				//(つまり、どんなに変化しても15000msecのより短い間隔では通知されず 1mより小さい変化の場合は通知されない。)
				 
				// locationService_.requestLocationUpdates(bestProvider_, 15000, 1,
				// listener);
	
				if (location_manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
					if (!f_first) {
	//					Toast.makeText(PlaceSimpleListActivity.this,
	//							"現在地を特定するまでしばらくお待ちください。\n位置精度が70m以下になればGPSを停止します。", Toast.LENGTH_LONG).show();
						f_first = true;
					}
					
					Criteria criteria = new Criteria();
	//				//API10のエミュレータだと以下のコメントをはずさないと動作しない。
	//				//実機だと問題なし、と思ってたけれど3Gだと動作せず。
					//なのでコメントを外す。
					//方位不要
					criteria.setBearingRequired(false);					
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
				    final String provider = location_manager.getBestProvider(criteria, true);
					//使える中で最も条件にヒットする位置情報サービスを取得する
					
					Log.d("location_manager", "provider:" + provider);
					
					
					location_manager.requestLocationUpdates(
							provider, 1000, 1,
	//						provider, 0, 0,
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
	public boolean dispatchKeyEvent(KeyEvent e) {
		// 戻るボタンが押されたとき
		if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
			// ボタンが押されたとき
			if (e.getAction() == KeyEvent.ACTION_DOWN) {
				finish();
			}
			// ボタンが離されたとき
			else if (e.getAction() == KeyEvent.ACTION_UP) {

			}

		}

		return super.dispatchKeyEvent(e);
	}
	

	@Override
	public void onClick(DialogInterface dialog, int which) {
		// TODO Auto-generated method stub
		if(listrowSelectDialog == dialog){
			if(which == 0){
				Log.i(TAG, "削除する。");
				
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
				
				if (mWritableDb == null || mWritableDb.isOpen() == false) {
					try {
						mWritableDb = mHelper.getWritableDatabase();
					} catch (SQLiteException e) {
						Log.d("MapView",
								"SQLiteException:" + e.getMessage());
						return;
					}
				}
				
				Place item = (Place)area_list.getItemAtPosition(selected_index);
				CustomData temp = listdata.get(selected_index);
				
				mWritableDb.delete("place", "id=" + temp.get_id(), null);
				
				listdata.remove(temp);
				place_list.remove(item);
				
				area_list.setAdapter(new ListPlaceAdapter(this));
			}
			else if(which == 1){
				CustomData temp = listdata.get(selected_index);
				
				Log.v("OnItemLongClick", "position: " + selected_index);
				//AR画面呼び出し。
				Intent intent2Map = new Intent(PlaceSimpleListActivity.this,
						GuideARActivity.class);

				// この書き方でエラーにはなってないけれど、調べてみるとBitmapの画像が粗いらしい。
				intent2Map.setAction("DestARLocation");
				intent2Map.putExtra("dest_lat", (double)temp.getCurrentPoint().getLatitudeE6()/1E6);
				intent2Map.putExtra("dest_lng", (double)temp.getCurrentPoint().getLongitudeE6()/1E6);
				
				startActivityForResult(intent2Map, MY_AR);

			}
		}
	}
}