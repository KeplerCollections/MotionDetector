package com.example.kepler.motiondetector;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.support.v13.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;

import com.example.kepler.motiondetector.fingerprint.FingerprintActivity;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

public class Splash extends AppCompatActivity {
    private KeyStore keyStore;
    private static final String KEY_NAME = "detector";
    private Cipher cipher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        final SharedPreferences preferences = getSharedPreferences("userss", 0);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (preferences.getString("user", null) == null) {
                    startActivity(new Intent(Splash.this, LoginActivity.class));
                    finish();
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        startActivity(new Intent(Splash.this, FingerprintActivity.class));
                        finish();
                    } else {
                        startActivity(new Intent(Splash.this, MotionDetectionActivity.class));
                        finish();
                    }
                }
            }
        }, 3000);
    }

    private void startMotionDeteorActivity() {
        startActivity(new Intent(Splash.this, MotionDetectionActivity.class));
        finish();
    }
}
