package com.example.ashton.smarttrak2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    boolean bluetoothConnected = false;
    boolean emergency = false;
    boolean quitEmergencyTask = false;
    String panicMsg = "Panic message!";
    String phoneNumber = "7782287611";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("SmartTrak");

        new EmergencyTask().execute();

        Button buttonPanic = (Button) findViewById(R.id.button_panic);
        buttonPanic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSMS(phoneNumber, panicMsg);
            }
        });
    }

    private class EmergencyTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params){


            while (!quitEmergencyTask){
                // implement emergency detection here
                

                if (emergency){
                    emergency = false;
                    quitEmergencyTask = true;
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result){
            String emergencyMsg = "";

            // TEST VALUE
            emergencyMsg = "Emergency message";

            sendSMS(phoneNumber, emergencyMsg);
            quitEmergencyTask = false;
            new EmergencyTask().execute();
        }

    }

    private void sendSMS(String phoneNumber, String message){
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS);
        if (permission == PackageManager.PERMISSION_GRANTED){
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(phoneNumber, null, message, null, null);

            Context context = getApplicationContext();
            String toast_text = "Emergency message sent.";
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, toast_text, duration);
            toast.show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, 1);
            sendSMS(phoneNumber, message);
        }

    }
}
