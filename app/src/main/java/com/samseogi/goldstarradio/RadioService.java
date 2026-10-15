package com.samseogi.goldstarradio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class RadioService extends Service {

    private static final String CHANNEL_ID = "GoldStarRadioChannel";
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. CPU가 절전 모드로 떨어지지 않도록 PARTIAL_WAKE_LOCK 고정
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GoldStarRadio::CpuWakeLock");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        }

        // 2. 백그라운드에서 Wi-Fi/네트워크 속도가 느려지지 않도록 고성능 락 고정
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            int lockType = WifiManager.WIFI_MODE_FULL_HIGH_PERF;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                lockType = WifiManager.WIFI_MODE_FULL_LOW_LATENCY;
            }
            wifiLock = wm.createWifiLock(lockType, "GoldStarRadio::WifiLock");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingFlags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("1966 금성사 FM 라디오")
                .setContentText("백그라운드 끊김 방지 모드로 방송 수신 중...")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1966, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(1966, notification);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "금성 라디오 백그라운드 재생",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("라디오 소리가 백그라운드에서 끊기지 않도록 유지합니다.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
