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
        //soundAffect.loadUrl("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3");
        //soundAffect.loadResource(R.raw.sound_4);
        //soundAffect.play();
    }
}
