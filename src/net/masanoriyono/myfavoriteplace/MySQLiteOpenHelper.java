package net.masanoriyono.myfavoriteplace;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class MySQLiteOpenHelper extends SQLiteOpenHelper {
	Context mContext;
	static final String MY_DB = "place.db";
	static final int DB_VERSION = 1;
	static final String CREATE_TABLE = "CREATE TABLE place (id integer primary key autoincrement, latitude INTEGER NOT NULL,longitude INTEGER NOT NULL,p_name TEXT NULL,p_timestamp TEXT NULL);";
	static final String DROP_TABLE = "DROP TABLE IF EXISTS place;";
	
	public MySQLiteOpenHelper(Context context, String name,
			CursorFactory factory, int version) {
		super(context, name, factory, version);
		// TODO Auto-generated constructor stub
		mContext = context;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		// TODO Auto-generated method stub
		// データベース処理開始
		db.beginTransaction();
		try {
			// テーブル作成を実行
			db.execSQL(CREATE_TABLE);
			db.setTransactionSuccessful();
			
			Log.d("DB","Create Table");
			
		} finally {
			// データベース終了処理
			db.endTransaction();
		}

	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// TODO Auto-generated method stub
		db.execSQL(DROP_TABLE);
		onCreate(db);
	}
	
	@Override
	public void onOpen(SQLiteDatabase db) {
		// データベースが開かれたときに実行される
		// これの実装は任意
		super.onOpen(db);

	}

}
