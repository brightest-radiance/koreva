package com.koreva.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.Calendar;
import java.util.Date;
import org.json.JSONArray;
import org.json.JSONObject;

@CapacitorPlugin(name = "KorevaAlarm")
public class KorevaAlarmPlugin extends Plugin {

    @PluginMethod
    public void schedule(PluginCall call) {
        JSArray a = call.getArray("alarms");
        getContext().getSharedPreferences("koreva", 0).edit().putString("alarms", a == null ? "[]" : a.toString()).apply();
        Alarms.scheduleAll(getContext());
        AlarmManager am = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        JSObject r = new JSObject();
        r.put("exact", Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms());
        call.resolve(r);
    }

    @PluginMethod
    public void settings(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getContext().getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            }
        } catch (Exception e) { }
        call.resolve();
    }

    @PluginMethod
    public void test(PluginCall call) {
        Alarms.oneShot(getContext(), "Koreva test alarm", 10, 5000);
        call.resolve();
    }

    public static class Alarms {
        static void scheduleAll(Context c) {
            SharedPreferences sp = c.getSharedPreferences("koreva", 0);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            try {
                JSONArray old = new JSONArray(sp.getString("codes", "[]"));
                for (int i = 0; i < old.length(); i++) {
                    PendingIntent p = PendingIntent.getBroadcast(c, old.getInt(i), new Intent(c, AlarmReceiver.class), PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                    if (p != null) am.cancel(p);
                }
                JSONArray arr = new JSONArray(sp.getString("alarms", "[]"));
                JSONArray codes = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject a = arr.getJSONObject(i);
                    long t = next(a);
                    if (t < 0) continue;
                    int code = Math.abs(a.getString("id").hashCode() % 100000);
                    Intent in = new Intent(c, AlarmReceiver.class).putExtra("l", a.optString("l", "Alarm")).putExtra("sn", a.optInt("sn", 10));
                    PendingIntent p = PendingIntent.getBroadcast(c, code, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    set(c, am, t, p);
                    codes.put(code);
                }
                sp.edit().putString("codes", codes.toString()).apply();
            } catch (Exception e) { }
        }

        static void set(Context c, AlarmManager am, long t, PendingIntent p) {
            PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            try { am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, show), p); } catch (SecurityException e) { }
        }

        static void oneShot(Context c, String label, int sn, long delayMs) {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent in = new Intent(c, AlarmReceiver.class).putExtra("l", label).putExtra("sn", sn).putExtra("once", true);
            PendingIntent p = PendingIntent.getBroadcast(c, 99999, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            set(c, am, System.currentTimeMillis() + delayMs, p);
        }

        static long next(JSONObject a) {
            JSONArray d = a.optJSONArray("d");
            if (d == null || d.length() == 0) return -1;
            for (int o = 0; o < 8; o++) {
                Calendar k = Calendar.getInstance();
                k.add(Calendar.DAY_OF_YEAR, o);
                k.set(Calendar.HOUR_OF_DAY, a.optInt("h"));
                k.set(Calendar.MINUTE, a.optInt("m"));
                k.set(Calendar.SECOND, 0);
                k.set(Calendar.MILLISECOND, 0);
                int jd = k.get(Calendar.DAY_OF_WEEK) - 1;
                for (int i = 0; i < d.length(); i++) {
                    if (d.optInt(i) == jd && k.getTimeInMillis() > System.currentTimeMillis()) return k.getTimeInMillis();
                }
            }
            return -1;
        }
    }

    public static class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            Alarms.scheduleAll(c);
            ContextCompat.startForegroundService(c, new Intent(c, AlarmService.class).putExtras(i));
        }
    }

    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) { Alarms.scheduleAll(c); }
    }

    public static class AlarmService extends Service {
        MediaPlayer mp;
        Vibrator vb;
        Handler hd = new Handler(Looper.getMainLooper());

        @Override
        public IBinder onBind(Intent i) { return null; }

        @Override
        public int onStartCommand(Intent i, int f, int id) {
            if (i == null) { stopAll(); return START_NOT_STICKY; }
            String act = i.getAction();
            String l = i.getStringExtra("l");
            int sn = i.getIntExtra("sn", 10);
            if ("STOP".equals(act)) { stopAll(); return START_NOT_STICKY; }
            if ("SNOOZE".equals(act)) { Alarms.oneShot(this, l == null ? "Alarm" : l, sn, sn * 60000L); stopAll(); return START_NOT_STICKY; }
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("koreva_alarm", "Koreva alarms", NotificationManager.IMPORTANCE_HIGH);
                ch.setSound(null, null);
                ch.enableVibration(false);
                nm.createNotificationChannel(ch);
            }
            Intent ai = new Intent(this, AlarmActivity.class).putExtras(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent fs = PendingIntent.getActivity(this, 1, ai, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, AlarmService.class).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent snz = PendingIntent.getService(this, 3, new Intent(this, AlarmService.class).setAction("SNOOZE").putExtras(i), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new NotificationCompat.Builder(this, "koreva_alarm")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(l == null ? "Alarm" : l)
                .setContentText("Tap to open")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(fs, true)
                .setContentIntent(fs)
                .setOngoing(true)
                .addAction(0, "Stop", stop)
                .addAction(0, "Snooze " + sn + " min", snz)
                .build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(1001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(1001, n);
            play();
            hd.postDelayed(this::stopAll, 120000);
            return START_NOT_STICKY;
        }

        void play() {
            try {
                Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                mp.setDataSource(this, u);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
            } catch (Exception e) { }
            vb = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vb != null) vb.vibrate(new long[]{0, 700, 500}, 0);
        }

        void stopAll() {
            hd.removeCallbacksAndMessages(null);
            try { if (mp != null) { mp.stop(); mp.release(); mp = null; } } catch (Exception e) { }
            if (vb != null) vb.cancel();
            stopForeground(true);
            stopSelf();
        }

        @Override
        public void onDestroy() { stopAll(); super.onDestroy(); }
    }

    public static class AlarmActivity extends Activity {
        @Override
        protected void onCreate(Bundle b) {
            super.onCreate(b);
            if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            final Intent src = getIntent();
            String l = src.getStringExtra("l");
            int sn = src.getIntExtra("sn", 10);
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setGravity(Gravity.CENTER);
            r.setBackgroundColor(Color.parseColor("#0D0E12"));
            r.setPadding(60, 60, 60, 60);
            TextView t = new TextView(this);
            t.setText(android.text.format.DateFormat.getTimeFormat(this).format(new Date()));
            t.setTextColor(Color.WHITE);
            t.setTextSize(64);
            t.setGravity(Gravity.CENTER);
            TextView lb = new TextView(this);
            lb.setText(l == null ? "Alarm" : l);
            lb.setTextColor(Color.parseColor("#AAAAAA"));
            lb.setTextSize(24);
            lb.setGravity(Gravity.CENTER);
            lb.setPadding(0, 10, 0, 80);
            Button s = new Button(this);
            s.setText("Stop");
            s.setTextSize(22);
            s.setOnClickListener(v -> { startService(new Intent(this, AlarmService.class).setAction("STOP")); finish(); });
            Button z = new Button(this);
            z.setText("Snooze " + sn + " min");
            z.setTextSize(18);
            z.setOnClickListener(v -> { startService(new Intent(this, AlarmService.class).setAction("SNOOZE").putExtras(src)); finish(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 170);
            lp.setMargins(0, 20, 0, 20);
            r.addView(t);
            r.addView(lb);
            r.addView(s, lp);
            r.addView(z, lp);
            setContentView(r);
        }
    }
}
