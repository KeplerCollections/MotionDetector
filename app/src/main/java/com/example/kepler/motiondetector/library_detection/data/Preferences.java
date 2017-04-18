package com.example.kepler.motiondetector.library_detection.data;

/**
 * This class is used to store preferences on how to decode images and what to
 * save.
 * 
 * @author Amit Kumar Jaiswal
 */
public abstract class Preferences {


    public static final String USER_NAME="networksprojectmail@gmail.com";
    public static final String PASSWORD="networksprojectmail01";
    public static final String USER="user";
    public static final String SHRD_PREF_KEY="fa_detection";

    private Preferences() {
    }

    // Which motion detection to use
    public static boolean USE_RGB = false;
    public static boolean USE_LUMA = false;
    public static boolean USE_STATE = true;

    // Which photos to save
    public static boolean SAVE_PREVIOUS = false;
    public static boolean SAVE_ORIGINAL = true;
    public static boolean SAVE_CHANGES = true;

    // Time between saving photos
    public static int PICTURE_DELAY = 10000;
}
