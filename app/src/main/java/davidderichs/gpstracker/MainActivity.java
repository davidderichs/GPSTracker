package davidderichs.gpstracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ramotion.fluidslider.FluidSlider;

import java.text.DecimalFormat;

import kotlin.Unit;

import static android.hardware.SensorManager.SENSOR_DELAY_NORMAL;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, LocationListener, SensorEventListener {

    LinearLayout linearLayout_standard_content;
    LinearLayout linearLayout_Calibration;
    LinearLayout linearLayout_Settings;

    TextView latitude;
    TextView longitude;
    TextView textView_height;
    TextView textView_recalibrated_height;
    TextView textView_speed;

    BottomNavigationView navigation_bottomNavigation;
    View button_settings;
    View button_info;
    View button_calibrate;

    boolean permissionsGranted;

    double height;
    double manual_CalibratedHeight;
    double height_offset;

    private final int ACCESS_FINE_LOCATION=1;

    Button button_SwitchToGPS;
    Button button_GetHeight;
    FluidSlider slider_SetNewCalibratedHeight;

    LocationManager locationManager;
    SensorManager sensorManager;
    Sensor pressureSensor;

    DecimalFormat df;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        showPhoneStatePermissionGPS();

        manual_CalibratedHeight = 0;
        height_offset = 0;

        df = new DecimalFormat("#.000");


        linearLayout_standard_content = (LinearLayout) findViewById(R.id.linearLayout_standard_content);
        linearLayout_Calibration = (LinearLayout) findViewById(R.id.linearLayout_Calibration);
        linearLayout_Settings = (LinearLayout) findViewById(R.id.linearLayout_Settings);

        navigation_bottomNavigation = (BottomNavigationView)findViewById(R.id.bottom_navigation);
        navigation_bottomNavigation.inflateMenu(R.menu.bottom_menu);
        try {
            button_info = navigation_bottomNavigation.findViewById(R.id.action_info);
            button_info.setOnClickListener(this);
            button_calibrate = navigation_bottomNavigation.findViewById(R.id.action_calibrate);
            button_calibrate.setOnClickListener(this);
            button_settings = navigation_bottomNavigation.findViewById(R.id.action_settings);
            button_settings.setOnClickListener(this);
        } catch (Exception e){
            Log.d("GPSReceiver", "Error setting navigation items up");
            Log.d("GPSReceiver", e.getMessage());
        }

        button_SwitchToGPS = (Button) findViewById(R.id.getGPS);
        button_SwitchToGPS.setOnClickListener(this);

        button_GetHeight = (Button) findViewById(R.id.getPressure);
        button_GetHeight.setOnClickListener(this);

        latitude = (TextView) findViewById(R.id.latitude);
        longitude = (TextView) findViewById(R.id.longitude);
        textView_height = (TextView) findViewById(R.id.height);
        textView_recalibrated_height = (TextView) findViewById(R.id.recalibrated_height);
        textView_speed = (TextView) findViewById(R.id.speed);

        setupManualHeightSlider();
        getGPSData();
        getHeightViaPressure();

    }

    private void setupManualHeightSlider() {
        slider_SetNewCalibratedHeight = findViewById(R.id.setCalibratedHeight);
        slider_SetNewCalibratedHeight.invalidate();
        slider_SetNewCalibratedHeight.refreshDrawableState();
        int min_calibratedHeight = 0;
        int max_calibratedHeight = 10000;
        slider_SetNewCalibratedHeight.setPosition(0.0f);
        slider_SetNewCalibratedHeight.setBubbleText("0");
        slider_SetNewCalibratedHeight.setStartText(Integer.toString(min_calibratedHeight) + "m");
        slider_SetNewCalibratedHeight.setEndText(Integer.toString(max_calibratedHeight) + "m");
        slider_SetNewCalibratedHeight.setPositionListener(pos -> {
            manual_CalibratedHeight = pos * max_calibratedHeight;
            height_offset = manual_CalibratedHeight - height;
            final String value = String.valueOf( (int)(pos * max_calibratedHeight) );
            slider_SetNewCalibratedHeight.setBubbleText(value);
            return Unit.INSTANCE;
        });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String permissions[],
            int[] grantResults) {
        switch (requestCode) {
            case ACCESS_FINE_LOCATION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("GPSReceiver", "Permission Granted!");
                } else {
                    Log.d("GPSReceiver", "Permission Denied!");
                }
        }
    }

    private void showPhoneStatePermissionGPS() {
        int permissionCheck = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                Log.d("GPSReceiver", "Permission rationale Needed");
                this.permissionsGranted = false;
            } else {
                Log.d("GPSReceiver", "Requesting permission for ACCESS_FINE_LOCATION");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        } else {
            Log.d("GPSReceiver", "Permission for GPS granted");
        }
    }


    @SuppressLint("MissingPermission")
    private void getGPSData() {
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }

    @SuppressLint("MissingPermission")
    private void getHeightViaPressure() {
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        sensorManager.registerListener(this, pressureSensor, SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.getGPS:
                Log.d("GPSReceiver", "Switching to GPS");
                getGPSData();
                break;
            case R.id.getPressure:
                Log.d("GPSReceiver", "Getting Pressure Data");
                getHeightViaPressure();
                break;
            case R.id.action_info:
                navigationInfoClicked();
                break;
            case R.id.action_calibrate:
                navigationCalibrateClicked();
                break;
            case R.id.action_settings:
                navigationSettingsClicked();
                break;
        }
    }

    private void navigationInfoClicked(){
        Log.d("GPSReceiver", "Navigation Info clicked");
    }

    private void navigationCalibrateClicked(){
        Log.d("GPSReceiver", "Navigation Calibrate clicked");
        if(linearLayout_Calibration.getVisibility() == View.GONE){
            linearLayout_Calibration.setVisibility(View.VISIBLE);
            linearLayout_Settings.setVisibility(View.GONE);
        } else {
            linearLayout_Calibration.setVisibility(View.GONE);
        }

        if(linearLayout_Settings.getVisibility() == linearLayout_Calibration.getVisibility() && linearLayout_Settings.getVisibility() == View.GONE){
            linearLayout_standard_content.setVisibility(View.VISIBLE);
        } else {
            linearLayout_standard_content.setVisibility(View.GONE);
        }
    }

    private void navigationSettingsClicked(){
        Log.d("GPSReceiver", "Navigation Settings clicked");
        if(linearLayout_Settings.getVisibility() == View.GONE){
            linearLayout_Settings.setVisibility(View.VISIBLE);
            linearLayout_Calibration.setVisibility(View.GONE);
        } else {
            linearLayout_Settings.setVisibility(View.GONE);
        }

        if(linearLayout_Settings.getVisibility() == linearLayout_Calibration.getVisibility() && linearLayout_Settings.getVisibility() == View.GONE){
            linearLayout_standard_content.setVisibility(View.VISIBLE);
        } else {
            linearLayout_standard_content.setVisibility(View.GONE);
        }
    }


    // Onclick Events
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    // Onclick Events
    @Override
    public void onProviderEnabled(String provider) {

    }

    //onclick Events
    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onLocationChanged(Location location) {
        longitude.setText("longitude: " + df.format(location.getLongitude()));
        latitude.setText("latitude: " + df.format(location.getLatitude()));
        textView_speed.setText("Speed: " + location.getSpeed());
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.values.length > 0){

            double expo = 1/5.255f;
            double k = 288000/6.5f;

            float pressure = Math.round(event.values[0]);
            height = k * (1-Math.pow(pressure/1013, expo));

            // adjust with offset from manual Calibration
            textView_height.setText("Height: " + df.format(height+height_offset) + "m");
            textView_recalibrated_height.setText("Calibrated Height: " + df.format(manual_CalibratedHeight) + "m");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d("GPSReceiver", "PressureSensor Accuracy-Changed Event");
    }
}
