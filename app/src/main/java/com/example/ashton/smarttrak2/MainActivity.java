package com.example.ashton.smarttrak2;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.NotificationCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements BluetoothAdapter.LeScanCallback {

    // State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;

    private int state;

    boolean emergency = false;
    boolean quitEmergencyTask = false;
    String phoneNumber = "7782287611";
    double latitude;
    double longitude;

    static final int PICK_CONTACT = 1;

    private boolean scanStarted;
    private boolean scanning;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;

    private RFduinoService rfduinoService;

    private Button enableBluetoothButton;
    private TextView scanStatusText;
    private Button scanButton;
    private TextView deviceInfoText;
    private TextView connectionStatusText;
    private Button connectButton;

    String bestProvider;
    LocationManager locationManager;



    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (state == BluetoothAdapter.STATE_ON) {
                upgradeState(STATE_DISCONNECTED);
            } else if (state == BluetoothAdapter.STATE_OFF) {
                downgradeState(STATE_BLUETOOTH_OFF);
            }
        }
    };

    private final BroadcastReceiver scanModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            scanning = (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE);
            scanStarted &= scanning;
            updateUi();
        }
    };

    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {
                upgradeState(STATE_CONNECTED);
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                downgradeState(STATE_DISCONNECTED);
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
//                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
                emergency = true;
                /********************
                 * handle emergency flag here maybe
                 */
//                char event = '0';
//                Bundle bundle = intent.getExtras();
//                event = bundle.getChar("EXTRA_DATA");

//                if (event != '0'){
//                    emergency = true;
//                    event = '0';
//                }
                //little endian to big endian
//                byte[] data = intent.getByteArrayExtra(RFduinoService.EXTRA_DATA);
//                String temp = data.toString();
//                byte[] stringtemp = temp.getBytes();
//                event = ByteBuffer.wrap(stringtemp).order(ByteOrder.LITTLE_ENDIAN).getChar();
//                event = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getChar();

                Log.e("BLUETOOTH RECEIVED", String.format("VALUE: %b,", emergency));
//                Toast.makeText(MainActivity.this, String.format("Received: %c, %d", event, received), Toast.LENGTH_LONG).show();
//                received++;
            }
        }
    };

    private final ServiceConnection rfduinoServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rfduinoService = ((RFduinoService.LocalBinder) service).getService();
            if (rfduinoService.initialize()) {
                if (rfduinoService.connect(bluetoothDevice.getAddress())) {
                    upgradeState(STATE_CONNECTING);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            rfduinoService = null;
            downgradeState(STATE_DISCONNECTED);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("SmartTrak");

        TextView tv = (TextView) findViewById(R.id.contact_text);
        tv.setText(String.format("Emergency Contact:\n%s", phoneNumber));

        MyLocationListener mLocationListener = new MyLocationListener();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        bestProvider = locationManager.getBestProvider(criteria, false);
        locationManager.requestLocationUpdates(bestProvider, 60000, 15, mLocationListener);

        new EmergencyTask().execute();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 1);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { Manifest.permission.ACCESS_COARSE_LOCATION }, 1);
        }


        enableBluetoothButton = (Button) findViewById(R.id.enable_bluetooth_button);
        scanStatusText = (TextView) findViewById(R.id.scan_status_text);
        scanButton = (Button) findViewById(R.id.scan_button);
        deviceInfoText = (TextView) findViewById(R.id.device_info_text);
        connectionStatusText = (TextView) findViewById(R.id.connection_status_text);
        connectButton = (Button) findViewById(R.id.connect_button);

        // Bluetooth
        enableBluetoothButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableBluetoothButton.setEnabled(false);
                enableBluetoothButton.setText(
                        bluetoothAdapter.enable() ? "Enabling bluetooth..." : "Enable failed!");
            }
        });

        // Find Device
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    scanStarted = true;
                    bluetoothAdapter.startLeScan(
                            new UUID[]{ RFduinoService.UUID_SERVICE },
                            MainActivity.this);
            }
        });

        // Connect Device
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                connectionStatusText.setText("Connecting...");
                Intent rfduinoIntent = new Intent(MainActivity.this, RFduinoService.class);
                bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
            }
        });

        Button buttonPanic = (Button) findViewById(R.id.button_panic);
        buttonPanic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSMS();
            }
        });

        Button button = (Button)findViewById(R.id.import_button);

        button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                startActivityForResult(intent, PICK_CONTACT);
            }
        });





    }

    @Override
    protected void onStart() {
        super.onStart();

        registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());

        if (state != STATE_CONNECTED){
            updateState(bluetoothAdapter.isEnabled() ? STATE_DISCONNECTED : STATE_BLUETOOTH_OFF);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        bluetoothAdapter.stopLeScan(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bluetoothAdapter.stopLeScan(this);

        unregisterReceiver(scanModeReceiver);
        unregisterReceiver(bluetoothStateReceiver);
        unregisterReceiver(rfduinoReceiver);
    }

    private class EmergencyTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params){


            while (!quitEmergencyTask){
                // implement emergency detection here
//                emergency = true;

                if (emergency){
                    emergency = false;
                    quitEmergencyTask = true;
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result){

            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Emergency Detected");
            builder.setMessage("Cancel this alert?");

            builder.setPositiveButton("YES", new DialogInterface.OnClickListener(){
               public void onClick(DialogInterface dialog, int which){
                   dialog.dismiss();
               }
            });

            builder.setNegativeButton("NO", new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which){
                    sendSMS();
                    dialog.dismiss();
                }
            });

            final AlertDialog alert = builder.create();
            alert.show();
            vibrator.vibrate(1000);

            final Handler handler = new Handler();
            final Runnable runnable = new Runnable(){
                @Override
                public void run(){
                    if (alert.isShowing()){
                        sendSMS();
                        alert.dismiss();
                    }
                }
            };

            alert.setOnDismissListener(new DialogInterface.OnDismissListener(){
                @Override
                public void onDismiss(DialogInterface dialog){
                    handler.removeCallbacks(runnable);
                }
            });

            handler.postDelayed(runnable, 10000);

            quitEmergencyTask = false;
            new EmergencyTask().execute();
        }

    }

    private void sendSMS(){

        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS);
        if (permission == PackageManager.PERMISSION_GRANTED){
            boolean locFound = getLocation();
            String tempMsg = "Emergency detected!";
            if (locFound){
                tempMsg = String.format("%s \nhttp://www.google.com/maps/place/%f,%f/@%f,%f,17z", tempMsg, latitude, longitude, latitude, longitude);
            } else {
                tempMsg = tempMsg + "\nNo location data available.";
            }
            final String message = tempMsg;

            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(phoneNumber, null, message, null, null);

            Context context = getApplicationContext();
            String toast_text = "Emergency message sent.";
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, toast_text, duration);
            toast.show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, 1);
            sendSMS();
        }

    }

    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        bluetoothAdapter.stopLeScan(this);
        bluetoothDevice = device;

        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                deviceInfoText.setText(
                        BluetoothHelper.getDeviceInfoText(bluetoothDevice, rssi, scanRecord));
                updateUi();
            }
        });
    }

    private void upgradeState(int newState) {
        if (newState > state) {
            updateState(newState);
        }
    }

    private void downgradeState(int newState) {
        if (newState < state) {
            updateState(newState);
        }
    }

    private void updateState(int newState) {
        state = newState;
        updateUi();
    }

    private void updateUi() {
        // Enable Bluetooth
        boolean on = state > STATE_BLUETOOTH_OFF;
        enableBluetoothButton.setEnabled(!on);
        enableBluetoothButton.setText(on ? "Bluetooth enabled" : "Enable Bluetooth");
        scanButton.setEnabled(on);

        // Scan
        if (scanStarted && scanning) {
            scanStatusText.setText("Scanning...");
            scanButton.setText("Stop Scan");
            scanButton.setEnabled(true);
        } else if (scanStarted) {
            scanStatusText.setText("Scan started...");
            scanButton.setEnabled(false);
        } else {
            scanStatusText.setText("");
            scanButton.setText("Scan");
            scanButton.setEnabled(true);
        }

        // Connect
        boolean connected = false;
        String connectionText = "Disconnected";
        if (state == STATE_CONNECTING) {
            connectionText = "Connecting...";
        } else if (state == STATE_CONNECTED) {
            connected = true;
            connectionText = "Connected";
        }
        connectionStatusText.setText(connectionText);
        connectButton.setEnabled(bluetoothDevice != null && state == STATE_DISCONNECTED);

    }

    public boolean getLocation(){
        boolean result = false;

        // Get the location manager
//        MyLocationListener mLocationListener = new MyLocationListener();
//        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
//        Criteria criteria = new Criteria();
//        String bestProvider = locationManager.getBestProvider(criteria, false);
//        locationManager.requestLocationUpdates(bestProvider, 1000, 15, mLocationListener);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            Location location = locationManager.getLastKnownLocation(bestProvider);

            try{
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                result = true;
            } catch (NullPointerException e){
                latitude = 0;
                longitude = 0;
                result = false;
            }
        }

        return result;
    }


    @Override public void onActivityResult(int reqCode, int resultCode, Intent data){

        super.onActivityResult(reqCode, resultCode, data);

        switch(reqCode)
        {
            case (PICK_CONTACT):
                if (resultCode == Activity.RESULT_OK)
                {
                    Uri contactData = data.getData();
                    Cursor c = managedQuery(contactData, null, null, null, null);
                    if (c.moveToFirst())
                    {
                        String id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID));

                        String hasPhone =
                                c.getString(c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER));

                        if (hasPhone.equalsIgnoreCase("1"))
                        {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, 1);
                            }


                            Cursor phones = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + id, null, null);
                            phones.moveToFirst();
                            String cNumber = phones.getString(phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                            phoneNumber = cNumber;

                            TextView tv = (TextView) findViewById(R.id.contact_text);
                            tv.setText(String.format("Emergency Contact:\n%s", phoneNumber));


                        }
                    }
                }
        }
    }

    private class MyLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(final Location location){
            if (location != null){
                latitude = location.getLatitude();
                longitude = location.getLongitude();
            }
        }

        @Override
        public void onProviderDisabled(String arg0)
        {
            // Do something here if you would like to know when the provider is disabled by the user
        }

        @Override
        public void onProviderEnabled(String arg0)
        {
            // Do something here if you would like to know when the provider is enabled by the user
        }

        @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2)
        {
            // Do something here if you would like to know when the provider status changes
        }
    };


}
