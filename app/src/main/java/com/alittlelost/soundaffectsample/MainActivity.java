package com.alittlelost.soundaffectsample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.alittlelost.soundaffect.SoundAffect;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    SoundAffect soundAffect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        soundAffect = findViewById(R.id.soundAffect);
        soundAffect.bindService(new SoundAffect.OnBindAttemptCompleteCallback() {
            @Override
            public void onSuccess() {
                soundAffect.loadUrl("http://www.sample-videos.com/audio/mp3/crowd-cheering.mp3");
                Toast.makeText(MainActivity.this, "Loaded audio from url", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure() {
                Toast.makeText(MainActivity.this, "Service bind attempt failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Unbinding media service");
        soundAffect.unbindService();
    }
}
