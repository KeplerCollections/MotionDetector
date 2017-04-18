package com.example.kepler.motiondetector.utils;

import android.content.Context;

/**
 * Created by kepler on 4/15/2017.
 */

public class Toast {

    public static void makeText(Context context, String msg) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}
