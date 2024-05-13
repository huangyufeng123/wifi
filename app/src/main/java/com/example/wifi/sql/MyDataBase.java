package com.example.wifi.sql;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import com.example.wifi.model.Wifi;

import java.util.ArrayList;
import java.util.List;


public class MyDataBase extends SQLiteOpenHelper {

    public MyDataBase(@Nullable Context context) {
        super(context, "ad_abdusalam_wifi", null, 1);

    }

    public void clearTable(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(tableName, null, null);
        db.close();
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        //在这里创建数据表

        String tb_wifi = "create table tb_wifi(" +
                "id integer PRIMARY KEY AUTOINCREMENT," +
                "wifi double," +
                "time timestamp DEFAULT CURRENT_TIMESTAMP" +
                ")";
        db.execSQL(tb_wifi);

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public void insertMultipleWifi(List<Wifi> dataList) {
        SQLiteDatabase db = getWritableDatabase();
        for (Wifi device : dataList) {
            ContentValues values = new ContentValues();
            values.put("wifi", device.getWifi());
            values.put("time", device.getTime());
            db.insert("tb_wifi", null, values);
        }
        db.close();
    }

    public Wifi getLatestWifi() {
        Wifi latestWifi = null;
        SQLiteDatabase db =getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM tb_wifi ORDER BY time DESC LIMIT 1", null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int wifiIndex = cursor.getColumnIndex("wifi");
                int timeIndex = cursor.getColumnIndex("time");

                if (wifiIndex >= 0 && timeIndex >= 0) {
                    double wifi = cursor.getDouble(wifiIndex);
                    Long time = cursor.getLong(timeIndex);

                    latestWifi = new Wifi(wifi,time);
                }
            }
            cursor.close();
        }

        return latestWifi;
    }


}