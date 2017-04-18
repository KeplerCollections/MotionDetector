package com.example.kepler.motiondetector.fingerprint;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.AsyncTask;
import android.os.CancellationSignal;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.TextView;

import com.example.kepler.motiondetector.MotionDetectionActivity;
import com.example.kepler.motiondetector.R;
import com.example.kepler.motiondetector.mail.Mail;
import com.example.kepler.motiondetector.utils.Toast;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;

/**
 * Created by whit3hawks on 11/16/16.
 */
public class FingerprintHandler extends FingerprintManager.AuthenticationCallback {

    private final SharedPreferences preferences;
    private Context context;
    private static int count=0;

    // Constructor
    public FingerprintHandler(Context mContext) {
        context = mContext;
        preferences = context.getSharedPreferences("userss", 0);
    }

    public void startAuth(FingerprintManager manager, FingerprintManager.CryptoObject cryptoObject) {
        CancellationSignal cancellationSignal = new CancellationSignal();
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        manager.authenticate(cryptoObject, cancellationSignal, 0, this, null);
    }

    @Override
    public void onAuthenticationError(int errMsgId, CharSequence errString) {
        this.update("Fingerprint Authentication error\n" + errString);
    }

    @Override
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        this.update("Fingerprint Authentication help\n" + helpString);
    }

    @Override
    public void onAuthenticationFailed() {
        this.update("Fingerprint Authentication failed.");
        if(count>2) {
            sendMail();
        }else {
            count++;
        }
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
        Intent intent = new Intent(context, MotionDetectionActivity.class);
        context.startActivity(intent);
        ((Activity) context).finish();
    }

    private void update(String e) {
        TextView textView = (TextView) ((Activity) context).findViewById(R.id.errorText);
        textView.setText(e);
    }


    private void sendMail() {
        try {
            String[] recipients = {preferences.getString("user","developer.kepler@gmail.com")};
            SendEmailAsyncTask email = new SendEmailAsyncTask();
            email.m = new Mail("developer.kepler@gmail.com", "Developer@");
            email.m.set_from("developer.kepler@gmail.com");
            email.m.setBody("Fingerprint Error");
            email.m.set_to(recipients);
            email.m.set_subject("Login Attempted");
            email.execute();
        } catch (Exception e) {
            Toast.makeText(context, "Failed");
        }
    }


    class SendEmailAsyncTask extends AsyncTask<Void, Void, String> {
        Mail m;

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
                e.printStackTrace();
                return "Authentication failed.";
            } catch (MessagingException e) {
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
            count=0;
            Toast.makeText(context,s);
        }
    }
}
