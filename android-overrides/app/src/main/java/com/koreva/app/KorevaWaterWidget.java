package com.koreva.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class KorevaWaterWidget extends AppWidgetProvider {
    static final String ADD = "com.koreva.app.ADD_WATER";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        render(c, m, ids);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (ADD.equals(i.getAction())) {
            SharedPreferences sp = c.getSharedPreferences("koreva", 0);
            sp.edit().putInt("pw", sp.getInt("pw", 0) + sp.getInt("cup", 250)).apply();
            refresh(c);
        }
    }

    static void render(Context c, AppWidgetManager m, int[] ids) {
        SharedPreferences sp = c.getSharedPreferences("koreva", 0);
        int cup = sp.getInt("cup", 250);
        int goal = Math.max(1, sp.getInt("wgoal", 2500));
        int ml = sp.getInt("wml", 0) + sp.getInt("pw", 0);
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.koreva_water_widget);
            v.setTextViewText(R.id.wv, ml + " / " + goal + " ml");
            v.setProgressBar(R.id.wb, goal, Math.min(ml, goal), false);
            v.setTextViewText(R.id.wbtn, "+" + cup + " ml");
            Intent add = new Intent(c, KorevaWaterWidget.class).setAction(ADD);
            v.setOnClickPendingIntent(R.id.wbtn, PendingIntent.getBroadcast(c, 2, add, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            v.setOnClickPendingIntent(R.id.ww_root, PendingIntent.getActivity(c, 3, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            m.updateAppWidget(id, v);
        }
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, KorevaWaterWidget.class));
        if (ids.length > 0) render(c, m, ids);
    }
}
