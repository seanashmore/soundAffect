package com.alittlelost.soundaffect;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.IOException;

/**
 * Created by seanashmore on 20/09/2017.
 */

class MediaManager {

    private static final String TAG = "MediaManager";
    private MediaPlayer mediaPlayer;
    private Context context;

    private boolean prepared = false;

    MediaManager(Context context) {
        this.context = context;
    }

    void loadUrl(String url) {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(url);

            AudioAttributes.Builder audioAttributes;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                audioAttributes = new AudioAttributes.Builder();
                audioAttributes.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
                audioAttributes.setLegacyStreamType(AudioManager.STREAM_MUSIC);
                audioAttributes.setUsage(AudioAttributes.USAGE_MEDIA);
                mediaPlayer.setAudioAttributes(audioAttributes.build());
            }

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mediaPlayer) {
                    prepared = true;
                }
            });

            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            Log.e(TAG, "IOException when playing from URL: " + e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play from URL: " + e);
        }
    }

    void loadResource(int resourceId) {
        mediaPlayer = MediaPlayer.create(context, resourceId);
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                prepared = true;
            }
        });
    }

    boolean isPrepared() {
        return prepared;
    }

    int getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
    }

    void setCurrentPosition(int currentPosition) {
        mediaPlayer.seekTo(currentPosition);
    }

    void reset() {
        mediaPlayer.seekTo(0);
    }

    boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    void play() {
        mediaPlayer.start();
    }

    void pause() {
        mediaPlayer.pause();
    }

    int getDuration() {
        return (mediaPlayer != null && mediaPlayer.getDuration() != -1) ? mediaPlayer.getDuration() : 0;
    }
}
