package com.example.kepler.motiondetector;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.example.kepler.motiondetector.data.GlobalData;
import com.example.kepler.motiondetector.data.Preferences;
import com.example.kepler.motiondetector.detection.AggregateLumaMotionDetection;
import com.example.kepler.motiondetector.detection.IMotionDetection;
import com.example.kepler.motiondetector.detection.LumaMotionDetection;
import com.example.kepler.motiondetector.detection.RgbMotionDetection;
import com.example.kepler.motiondetector.image.ImageProcessing;
import com.example.kepler.motiondetector.mail.Mail;
import com.example.kepler.motiondetector.mail.OnSavePic;
import com.example.kepler.motiondetector.utils.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;

import cz.msebera.android.httpclient.Header;


/**
 * This class extends Activity to handle a picture preview, process the frame
 * for motion, and then save the file to the SD card.
 *
 * @author Justin Wetherell <phishman3579@gmail.com>
 */
public class MotionDetectionActivity extends SensorsActivity {

    private static final String TAG = "MotionDetectionActivity";

    private static SurfaceView preview = null;
    private static SurfaceHolder previewHolder = null;
    private static Camera camera = null;
    private static boolean inPreview = false;
    private static long mReferenceTime = 0;
    private static MotionDetectionActivity mInstance = null;
    private static IMotionDetection detector = null;
    private static volatile AtomicBoolean processing = new AtomicBoolean(false);
    private static OnSavePic onSavePic;
    private String mEmail;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final SharedPreferences preferences = getSharedPreferences("userss", 0);
        mEmail=preferences.getString("user","developer.kepler@gmail.com");
        onSavePic = new OnSavePic() {
            @Override
            public void sendMessage(File file) {
                if (file != null && file.exists()) {
                    sendMail(file.getPath());
                } else {
                    Toast.makeText(getApplicationContext(), "file not exist");
                }
            }
        };
        mInstance = this;
        preview = (SurfaceView) findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        if (Preferences.USE_RGB) {
            detector = new RgbMotionDetection();
        } else if (Preferences.USE_LUMA) {
            detector = new LumaMotionDetection();
        } else {
            // Using State based (aggregate map)
            detector = new AggregateLumaMotionDetection();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();

        camera.setPreviewCallback(null);
        if (inPreview) camera.stopPreview();
        inPreview = false;
        camera.release();
        camera = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();
        camera = Camera.open();
        Log.e(TAG, "onResume");

    }

    private PreviewCallback previewCallback = new PreviewCallback() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onPreviewFrame(byte[] data, Camera cam) {
            if (data == null) return;
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) return;

            if (!GlobalData.isPhoneInMotion()) {
                DetectionThread thread = new DetectionThread(data, size.width, size.height);
                thread.start();
            }
        }
    };

    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                camera.setPreviewDisplay(previewHolder);
                camera.setPreviewCallback(previewCallback);
            } catch (Throwable t) {
                Log.e("-surfaceCallback", "Exception in setPreviewDisplay()", t);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.e(TAG, "surfaceChanged");
            if (camera == null)
                return;
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = getBestPreviewSize(width, height, parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
                Log.d(TAG, "Using width=" + size.width + " height=" + size.height);
            }
            camera.setParameters(parameters);
            camera.startPreview();
            inPreview = true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Ignore
        }
    };

    private static Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea > resultArea) result = size;
                }
            }
        }

        return result;
    }

    private static final class DetectionThread extends Thread {

        private byte[] data;
        private int width;
        private int height;

        public DetectionThread(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            if (!processing.compareAndSet(false, true)) return;

            // Log.d(TAG, "BEGIN PROCESSING...");
            try {
                // Previous frame
                int[] pre = null;
                if (Preferences.SAVE_PREVIOUS) pre = detector.getPrevious();

                // Current frame (with changes)
                // long bConversion = System.currentTimeMillis();
                int[] img = null;
                if (Preferences.USE_RGB) {
                    img = ImageProcessing.decodeYUV420SPtoRGB(data, width, height);
                } else {
                    img = ImageProcessing.decodeYUV420SPtoLuma(data, width, height);
                }
                // long aConversion = System.currentTimeMillis();
                // Log.d(TAG, "Converstion="+(aConversion-bConversion));

                // Current frame (without changes)
                int[] org = null;
                if (Preferences.SAVE_ORIGINAL && img != null) org = img.clone();

                if (img != null && detector.detect(img, width, height)) {
                    // The delay is necessary to avoid taking a picture while in
                    // the
                    // middle of taking another. This problem can causes some
                    // phones
                    // to reboot.
                    long now = System.currentTimeMillis();
                    if (now > (mReferenceTime + Preferences.PICTURE_DELAY)) {
                        mReferenceTime = now;

                        Bitmap previous = null;
                        if (Preferences.SAVE_PREVIOUS && pre != null) {
                            if (Preferences.USE_RGB)
                                previous = ImageProcessing.rgbToBitmap(pre, width, height);
                            else previous = ImageProcessing.lumaToGreyscale(pre, width, height);
                        }

                        Bitmap original = null;
                        if (Preferences.SAVE_ORIGINAL && org != null) {
                            if (Preferences.USE_RGB)
                                original = ImageProcessing.rgbToBitmap(org, width, height);
                            else original = ImageProcessing.lumaToGreyscale(org, width, height);
                        }

                        Bitmap bitmap = null;
                        if (Preferences.SAVE_CHANGES) {
                            if (Preferences.USE_RGB)
                                bitmap = ImageProcessing.rgbToBitmap(img, width, height);
                            else bitmap = ImageProcessing.lumaToGreyscale(img, width, height);
                        }

                        Log.i(TAG, "Saving.. previous=" + previous + " original=" + original + " bitmap=" + bitmap);
                        Looper.prepare();
                        new SavePhotoTask().execute(previous, bitmap,original);
//                            loadToServer(data);
                    } else {
//                        Log.i(TAG, "Not taking picture because not enough time has passed since the creation of the Surface");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                processing.set(false);
            }
            // Log.d(TAG, "END PROCESSING...");

            processing.set(false);
        }

        private final class SavePhotoTask extends AsyncTask<Bitmap, Integer, File> {

            /**
             * {@inheritDoc}
             */
            @Override
            protected File doInBackground(Bitmap... data) {
                File file = null;
                for (int i = 0; i < data.length; i++) {
                    Bitmap bitmap = data[i];
                    String name = String.valueOf(System.currentTimeMillis());
                    if (bitmap != null)
                        file = save(name, bitmap);
                }
                return file;
            }

            @Override
            protected void onPostExecute(File file) {
                super.onPostExecute(file);
                onSavePic.sendMessage(file);
            }

            private File save(String name, Bitmap bitmap) {
                File photo = new File(Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_PICTURES, name + ".jpg");
                if (photo.exists()) photo.delete();

                try {
                    FileOutputStream fos = new FileOutputStream(photo.getPath());
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos.close();
                } catch (java.io.IOException e) {
                    Log.e("PictureDemo", "Exception in photoCallback", e);
                }
                return photo;
            }
        }
    }

    private void sendMail(String path) {
        try {
            Log.e(TAG, path);
            String[] recipients = {mEmail};
            SendEmailAsyncTask email = new SendEmailAsyncTask();
            email.activity = MotionDetectionActivity.this;
            email.m = new Mail("developer.kepler@gmail.com", "Developer@");
            email.m.set_from("developer.kepler@gmail.com");
            email.m.setBody("New motion detected");
            email.m.set_to(recipients);
            email.m.set_subject("Detect Motion");
            email.m.addAttachment(path);
            email.execute();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "Exception in adding attachment");
        }
    }


    class SendEmailAsyncTask extends AsyncTask<Void, Void, String> {
        Mail m;
        MotionDetectionActivity activity;

        public SendEmailAsyncTask() {
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                if (m.send()) {
                    return "Email sent.";
                } else {
                    return "Email failed to send.";
                }
            } catch (AuthenticationFailedException e) {
                Log.e(SendEmailAsyncTask.class.getName(), "Bad account details");
                e.printStackTrace();
                return "Authentication failed.";
            } catch (MessagingException e) {
                Log.e(SendEmailAsyncTask.class.getName(), "Email failed");
                e.printStackTrace();
                return "Email failed to send.";
            } catch (Exception e) {
                e.printStackTrace();
                return "Unexpected error occured.";
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Toast.makeText(getApplicationContext(), s);
        }

//        private void loadToServer(byte[] myByteArray) {
//            if (mInstance == null)
//                return;
//            Log.e(TAG, "Enter");
//            RequestParams params = new RequestParams();
////            params.put("image", new ByteArrayInputStream(myByteArray));
//            params.put("name", String.valueOf(Calendar.getInstance().getTimeInMillis()) + ".jpg");
//            params.put("action", "upload");
//            Log.e(TAG, params.toString());
//            ImagePostClient.post("controller_motion.php", params, new AsyncHttpResponseHandler() {
//                @Override
//                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
//                    try {
//                        Log.e(TAG, new String(responseBody));
//                    } catch (Exception e) {
//
//                    }
//                }
//
//                @Override
//                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
//                    try {
//                        Log.e(TAG, error.getMessage());
//                        Log.e(TAG, new String(responseBody));
//                    } catch (Exception e) {
//
//                    }
//                }
//
//                @Override
//                public void onFinish() {
//                    super.onFinish();
//                }
//            });
//        }
    }
}