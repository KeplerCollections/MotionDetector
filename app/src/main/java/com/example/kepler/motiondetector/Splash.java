package com.example.kepler.motiondetector;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;

import com.example.kepler.motiondetector.library_detection.MotionDetectionActivity;
import com.example.kepler.motiondetector.library_detection.data.Preferences;
import com.example.kepler.motiondetector.fingerprint.FingerprintActivity;

public class Splash extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        final SharedPreferences preferences = getSharedPreferences(Preferences.SHRD_PREF_KEY, 0);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (preferences.getString(Preferences.USER, null) == null) {
                    startActivity(new Intent(Splash.this, LoginActivity.class));
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        startActivity(new Intent(Splash.this, FingerprintActivity.class));
                    } else {
                        startActivity(new Intent(Splash.this, MotionDetectionActivity.class));
                    }
                }
                finish();
            }
        }, 3000);
    }

}
