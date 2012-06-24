package net.masanoriyono.myfavoriteplace;

//import com.google.android.maps.MapActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;

public class MyFavoritePlaceActivity extends Activity implements DialogInterface.OnClickListener{
    protected static final String TAG = "myfavoriteplaceActivity";
    private AlertDialog routeSrcSelectDialog;
    private AlertDialog.Builder alertDialogBuilder;
    
    private static final int MY_MAP = 2;
    private static final int MY_LIST = 3;
    
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        Button button1 = (Button)findViewById(R.id.button1);
        Button button2 = (Button)findViewById(R.id.button2);
        
        String[] str_items = {"GPSを使う","地図上で指定する"};
        alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle("地点登録にどちらの情報を使いますか？");
        alertDialogBuilder.setCancelable(true);
        alertDialogBuilder.setItems(str_items, this);
        
        
        button1.setOnClickListener(new View.OnClickListener() {
        	@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Log.i(TAG, "見るボタンが押されました。");
				
				Intent intent = new Intent(MyFavoritePlaceActivity.this,
						PlaceSimpleListActivity.class);
				
				intent.setAction("GPSAction");
				intent.putExtra("gps", false);
				
				startActivityForResult(intent, MY_LIST);
			}
		});
        
        button2.setOnClickListener(new View.OnClickListener() {
      	@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Log.i(TAG, "登録ボタンが押されました。");
				
				routeSrcSelectDialog = alertDialogBuilder.create();
		        // アラートダイアログを表示します
				routeSrcSelectDialog.show();
				
			}
		});
    }

	@Override
	public void onClick(DialogInterface dialog, int which) {
		// TODO Auto-generated method stub
		if(routeSrcSelectDialog == dialog){
			if(which == 0){
				Log.i(TAG, "GPSを使う");
				
				Intent intent = new Intent(MyFavoritePlaceActivity.this,
						MapViewActivity.class);
				intent.setAction("GPSAction");
				intent.putExtra("gps", true);
				
				startActivityForResult(intent, MY_MAP);
			}
			else if(which == 1){
				Log.i(TAG, "地図上で指定する");
				
				Intent intent = new Intent(MyFavoritePlaceActivity.this,
						MapViewActivity.class);
				
				intent.setAction("GPSAction");
				intent.putExtra("gps", false);
				
				startActivityForResult(intent, MY_MAP);

			}
		}
		
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
		
		if (e.getKeyCode() == KeyEvent.KEYCODE_MENU) {
			// ボタンが押されたとき
			if (e.getAction() == KeyEvent.ACTION_DOWN) {
				
			}
			// ボタンが離されたとき
			else if (e.getAction() == KeyEvent.ACTION_UP) {

			}

		}
		
		return super.dispatchKeyEvent(e);
	}
}