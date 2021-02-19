package com.univr.streamcast;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private TextView ip_address;
    public static final int PERMISSION_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startRtspService();
    }

    private void startRtspService() {
        MediaProjectionManager manager =
                (MediaProjectionManager) this.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent intent = manager.createScreenCaptureIntent();
        startActivityForResult(intent, PERMISSION_CODE);
        Toast.makeText(MainActivity.this,"START", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        handleRecordScreenRequest(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void handleRecordScreenRequest(int requestCode, int resultCode, Intent data) {
        if (requestCode != PERMISSION_CODE) return;
        if (resultCode != Activity.RESULT_OK) return;

        // start background service
        Intent serviceIntent = new Intent(this,ScreenService.class);
        serviceIntent.putExtra("resultCode", resultCode);
        serviceIntent.putExtra("data", data);
        startService(serviceIntent);
    }

    // BroadcastReceiver that detects wifi state changements
    private final BroadcastReceiver mWifiStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {

        }
    };

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mWifiStateReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(mWifiStateReceiver,
                new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
    }
}
