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
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.ramotion.fluidslider.FluidSlider;

import kotlin.Unit;

import static android.hardware.SensorManager.SENSOR_DELAY_NORMAL;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, LocationListener, SensorEventListener {

    TextView latitude;
    TextView longitude;
    TextView textView_height;
    TextView textView_recalibrated_height;
    TextView textView_speed;
    CheckBox checkbox_useNegativeCalibration;
    BottomNavigationView navigation_bottomNavigation;

    boolean permissionsGranted;

    int manual_CalibratedHeight;

    private final int ACCESS_FINE_LOCATION=1;

    Button button_SwitchToGPS;
    Button button_GetHeight;
    FluidSlider slider_SetNewCalibratedHeight;

    LocationManager locationManager;
    SensorManager sensorManager;
    Sensor pressureSensor;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        showPhoneStatePermissionGPS();

        manual_CalibratedHeight = 0;


        navigation_bottomNavigation = (BottomNavigationView)findViewById(R.id.bottom_navigation);
        navigation_bottomNavigation.inflateMenu(R.menu.bottom_menu);
        navigation_bottomNavigation.setOnClickListener(this);

        button_SwitchToGPS = (Button) findViewById(R.id.getGPS);
        button_SwitchToGPS.setOnClickListener(this);

        button_GetHeight = (Button) findViewById(R.id.getPressure);
        button_GetHeight.setOnClickListener(this);

        checkbox_useNegativeCalibration = (CheckBox) findViewById(R.id.checkbox_useNegativeCalibration);
        checkbox_useNegativeCalibration.setOnClickListener(this);

        latitude = (TextView) findViewById(R.id.latitude);
        longitude = (TextView) findViewById(R.id.longitude);
        textView_height = (TextView) findViewById(R.id.height);
        textView_recalibrated_height = (TextView) findViewById(R.id.recalibrated_height);
        textView_speed = (TextView) findViewById(R.id.speed);

        setupManualHeightSlider();
        getGPSData();
        getHeightViaPressure();

    }

    private void toggleCalibrationType(){
        if (checkbox_useNegativeCalibration.isChecked()){
            setupManualHeightSliderNegative();
        } else {
            setupManualHeightSlider();
        }
    }

    private void setupManualHeightSliderNegative() {
        slider_SetNewCalibratedHeight = findViewById(R.id.setCalibratedHeight);
        slider_SetNewCalibratedHeight.invalidate();
        slider_SetNewCalibratedHeight.refreshDrawableState();
        int min_calibratedHeight = 0;
        int max_calibratedHeight = 1000;
        slider_SetNewCalibratedHeight.setPosition(0.0f);
        slider_SetNewCalibratedHeight.setBubbleText("0");
        slider_SetNewCalibratedHeight.setStartText(Integer.toString(min_calibratedHeight) + "m");
        slider_SetNewCalibratedHeight.setEndText(Integer.toString(-max_calibratedHeight) + "m");
        slider_SetNewCalibratedHeight.setPositionListener(pos -> {
            manual_CalibratedHeight = (int)(-pos * max_calibratedHeight);
            final String value = String.valueOf( (int)(-pos * max_calibratedHeight) );
            slider_SetNewCalibratedHeight.setBubbleText(value);
            return Unit.INSTANCE;
        });
    }

    private void setupManualHeightSlider() {
        slider_SetNewCalibratedHeight = findViewById(R.id.setCalibratedHeight);
        slider_SetNewCalibratedHeight.invalidate();
        slider_SetNewCalibratedHeight.refreshDrawableState();
        int min_calibratedHeight = 0;
        int max_calibratedHeight = 1000;
        slider_SetNewCalibratedHeight.setPosition(0.0f);
        slider_SetNewCalibratedHeight.setBubbleText("0");
        slider_SetNewCalibratedHeight.setStartText(Integer.toString(min_calibratedHeight) + "m");
        slider_SetNewCalibratedHeight.setEndText(Integer.toString(max_calibratedHeight) + "m");
        slider_SetNewCalibratedHeight.setPositionListener(pos -> {
            manual_CalibratedHeight = (int)(pos * max_calibratedHeight);
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
            case R.id.checkbox_useNegativeCalibration:
                Log.d("GPSReceiver", "Use Negative Calibration");
                toggleCalibrationType();
            case R.id.bottom_navigation:
                Log.d("GPSReceiver", "Menu clicked");
                menuClicked(v);
        }
    }

    private void menuClicked(View v){
        int id = v.getId();
        Log.d("GPSReceiver", "clicked view id: " + id);
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
        longitude.setText("longitude: " + Double.toString(location.getLongitude()));
        latitude.setText("latitude: " + Double.toString(location.getLatitude()));
        textView_speed.setText("Speed: " + location.getSpeed());
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.values.length > 0){

            double expo = 1/5.255f;
            double k = 288000/6.5f;

            float pressure = Math.round(event.values[0]);
            double height = k * (1-Math.pow(pressure/1013, expo));
            height = Math.round(height);

            // adjust with offset from manual Calibration
            int recalibrated_height = (int) (manual_CalibratedHeight + height);

            textView_height.setText("Height: " + height + "m");
            textView_recalibrated_height.setText("Calibrated Height: " + recalibrated_height + "m");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d("GPSReceiver", "PressureSensor Accuracy-Changed Event");
    }
}
