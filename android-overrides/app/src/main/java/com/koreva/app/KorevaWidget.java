package com.koreva.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class KorevaWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        render(c, m, ids);
    }

    static void render(Context c, AppWidgetManager m, int[] ids) {
        SharedPreferences sp = c.getSharedPreferences("koreva", 0);
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.koreva_widget);
            v.setTextViewText(R.id.w_a, sp.getString("w_a", "Open Koreva to set up"));
            v.setTextViewText(R.id.w_b, sp.getString("w_b", ""));
            v.setTextViewText(R.id.w_c, sp.getString("w_c", ""));
            PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            v.setOnClickPendingIntent(R.id.w_root, pi);
            m.updateAppWidget(id, v);
        }
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, KorevaWidget.class));
        if (ids.length > 0) render(c, m, ids);
    }
}
