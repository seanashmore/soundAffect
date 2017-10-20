package com.alittlelost.soundaffectsample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.alittlelost.soundaffect.SoundAffect;

public class MainActivity extends AppCompatActivity {

    SoundAffect soundAffect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        soundAffect = findViewById(R.id.soundAffect);
        soundAffect.loadUrl("http://www.sample-videos.com/audio/mp3/crowd-cheering.mp3");
    }
}
