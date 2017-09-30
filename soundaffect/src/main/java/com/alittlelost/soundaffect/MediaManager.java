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

public class MediaManager {

    private static final String TAG = "MediaManager";
    private MediaPlayer mediaPlayer;
    private Context context;

    private boolean prepared = false;

    public MediaManager(Context context) {
        this.context = context;
    }

    public void loadUrl(String url) {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(url);

            AudioAttributes.Builder audioAttributes = new AudioAttributes.Builder();
            audioAttributes.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
            audioAttributes.setLegacyStreamType(AudioManager.STREAM_MUSIC);
            audioAttributes.setUsage(AudioAttributes.USAGE_MEDIA);

            mediaPlayer.setAudioAttributes(audioAttributes.build());
            mediaPlayer.prepare();
        } catch (IOException e) {
            Log.e(TAG, "IOException when playing from URL: " + e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play from URL: " + e);
        }
    }

    public void loadResource(int resourceId) {
        mediaPlayer = MediaPlayer.create(context, resourceId);
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                prepared = true;
            }
        });
    }

    public boolean isPrepared() {
        return prepared;
    }

    public void setOnCompletionListener(MediaPlayer.OnCompletionListener onCompletionListener) {
        mediaPlayer.setOnCompletionListener(onCompletionListener);
    }

    public int getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
    }

    public void setCurrentPosition(int currentPosition) {
        mediaPlayer.seekTo(currentPosition);
    }

    public void reset() {
        mediaPlayer.seekTo(0);
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public void play() {
        mediaPlayer.start();
    }

    public void pause() {
        mediaPlayer.pause();
    }

    public int getDuration() {
        return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
    }
}
