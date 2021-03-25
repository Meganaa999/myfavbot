package com.paul.mr_paul.blackbot.DBHelper;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.paul.mr_paul.blackbot.Contract.VariableContract.VariableEntry;

public class VariableDBHelper extends SQLiteOpenHelper {

    private final static String DATABASE_NAME = "variables.db";
    private final static int DATABASE_VERSION = 1;

    public VariableDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        final String CREATE_DATABASE = "CREATE TABLE " +
                VariableEntry.TABLE_NAME + " (" +
                VariableEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                VariableEntry.COLUMN_VAR + " TEXT NOT NULL, " +
                VariableEntry.COLUMN_VALUE + " TEXT NOT NULL, " +
                VariableEntry.COLUMN_TIMESTAMP + " TEXT NOT NULL" +
                ");";

        db.execSQL(CREATE_DATABASE);

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // delete the old table and recreate a new
        db.execSQL("DROP TABLE IF EXISTS " + VariableEntry.TABLE_NAME);
        onCreate(db);
    }
}
