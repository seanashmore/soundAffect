package com.alittlelost.soundaffect;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Created by seanashmore on 27/12/2017.
 */

public class MediaService extends Service {

    MediaManager mediaManager;

    private final IBinder mBinder = new LocalBinder();

    class LocalBinder extends Binder {
        MediaService getService() {
            return MediaService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaManager = new MediaManager(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = null;
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel("101", "Media-Notifications", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(mChannel);

            notification =
                    new Notification.Builder(this, mChannel.getId())
                            .setContentTitle("Title")
                            .setContentText("Message")
                            .setSmallIcon(R.drawable.ic_skip_previous_black_24dp)
                            .setTicker("Ticker text")
                            .build();
        } else {
            notification =
                    new Notification.Builder(this)
                            .setContentTitle("Title")
                            .setContentText("Message")
                            .setSmallIcon(R.drawable.ic_skip_previous_black_24dp)
                            .setTicker("Ticker text")
                            .build();
        }

        startForeground(101, notification);

        return START_NOT_STICKY;
    }

    void loadUrl(String url) {
        mediaManager.loadUrl(url);
    }

    void loadResource(int resourceId) {
        mediaManager.loadResource(resourceId);
    }

    boolean isPlaying() {
        return mediaManager.isPlaying();
    }

    boolean isPrepared() {
        return mediaManager.isPrepared();
    }

    void play() {
        mediaManager.play();
    }

    void pause() {
        mediaManager.pause();
    }

    void reset() {
        mediaManager.reset();
    }

    int getDuration() {
        return mediaManager.getDuration();
    }

    void setCurrentPosition(int currentPosition) {
        mediaManager.setCurrentPosition(currentPosition);
    }

    int getCurrentPosition() {
        return mediaManager.getCurrentPosition();
    }
}
