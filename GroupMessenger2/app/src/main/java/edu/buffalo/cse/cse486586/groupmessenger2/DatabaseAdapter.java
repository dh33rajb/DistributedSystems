package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by dheeraj on 2/14/15.
 *
 * @references: Android SQLite DB Storage tutorial: https://www.youtube.com/playlist?list=PLonJJ3BVjZW5JdoFT0Rlt3ry5Mjp7s8cT
 */

public class DatabaseAdapter {

    DatabaseHelper dbHelper;

    public DatabaseAdapter(Context context) {
        dbHelper = new DatabaseHelper(context);
    }

    public void insert(ContentValues contentValues) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cValues = new ContentValues();

        String key = (String) contentValues.get("key");
        String value = (String) contentValues.get("value");

        cValues.put(dbHelper.KEY, key);
        cValues.put(dbHelper.VALUE, value);

        long id = db.insert(DatabaseHelper.MESSAGES_TABLE, null, cValues); // indicated row id of the column that was inserted
        // System.out.println("*** Inserted " + key + ": " + value + " to the DB. ***");
        db.close();
    }

    public void update(ContentValues contentValues) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cValues = new ContentValues();

        String key = (String) contentValues.get("key");
        String value = (String) contentValues.get("value");

        cValues.put(dbHelper.KEY, key);
        cValues.put(dbHelper.VALUE, value);

        long id = db.update(DatabaseHelper.MESSAGES_TABLE, cValues, dbHelper.KEY + " = ?",
                new String[]{key}); // indicated row id of the column that was inserted
        // System.out.println("*** Updated " + key + ": " + value + " to the DB. ***");
        db.close();

    }

    /*public android.database.Cursor query(java.lang.String table,
                                         java.lang.String[] columns,
                                         java.lang.String selection,
                                         java.lang.String[] selectionArgs,
                                         java.lang.String groupBy,
                                         java.lang.String having,
                                         java.lang.String orderBy)*/

    public Cursor query(String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String columns[] = {dbHelper.KEY, dbHelper.VALUE};
        //Cursor cursor = db.query(dbHelper.MESSAGES_TABLE, columns, null, null, null, null, null);


        Cursor cursor = db.query(
                dbHelper.MESSAGES_TABLE,
                columns,
                dbHelper.KEY + " = ?",
                new String[]{selection},
                null,
                null,
                null);
        if (cursor != null) {
            cursor.moveToFirst();
            while (cursor.moveToNext()) {
                String valuee = cursor.getString(cursor.getColumnIndex("value"));
                // System.out.println("***" + valuee + "***");
            }
        }
        // System.out.println("*** Queried the key " + selection + " in the DB. ***");
        return cursor;
    }

    static class DatabaseHelper extends SQLiteOpenHelper {

        private static final String DATABASE_NAME = "GroupMessengerDB";
        private static final String MESSAGES_TABLE = "MessagesTable";
        private static final int DATABASE_VERSION = 56;

        private static final String KEY = "key";
        private static final String VALUE = "value";

        private final Context context;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null /*custom cursor*/, DATABASE_VERSION);
            this.context = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            try {
                // db.execSQL("CREATE TABLE MessagesTa  ble (keys VARCHAR PRIMARY KEY, values VARCHAR NOT NULL);");
                db.execSQL("CREATE TABLE " + MESSAGES_TABLE + " (" + KEY + " VARCHAR(255), " + VALUE + " VARCHAR(255) NOT NULL);");
            } catch (Exception e) { // should be SQLException
                e.printStackTrace();
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // nothing much for me to implement here
            db.execSQL("DROP TABLE IF EXISTS " + MESSAGES_TABLE);
            onCreate(db);
        }
    }
}