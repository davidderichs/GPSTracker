package davidderichs.gpstracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ramotion.fluidslider.FluidSlider;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.LinkedList;

import kotlin.Unit;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, LocationListener, SensorEventListener {

    LinearLayout linearLayout_main_Content;
    LinearLayout linearLayout_standard_content;
    LinearLayout linearLayout_Calibration;
    LinearLayout linearLayout_Settings;
    LinearLayout linearLayout_Route;

    ImageView imageView_Route;

    TextView textView_latitude;
    TextView textView_longitude;
    TextView textView_height;
    TextView textView_recalibrated_height;
    TextView textView_speed;

    BottomNavigationView navigation_bottomNavigation;
    View button_settings;
    View button_info;
    View button_calibrate;

    boolean permissionsGranted;
    boolean calibrationActive;

    double longitude;
    double latitude;
    double speed;
    float height;
    float height_offset;

    LinkedList<String> route = new LinkedList<>();

    private final int ACCESS_FINE_LOCATION=1;

    Button button_SwitchToGPS;
    Button button_GetHeight;
    Button button_resetCalibration;
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

        this.route.clear();

        height_offset = 0.0f;
        calibrationActive = false;

        df = new DecimalFormat("#.000");


        linearLayout_main_Content = (LinearLayout) findViewById(R.id.linearLayout_main_Content);
        linearLayout_standard_content = (LinearLayout) findViewById(R.id.linearLayout_standard_content);
        linearLayout_Calibration = (LinearLayout) findViewById(R.id.linearLayout_Calibration);
        linearLayout_Settings = (LinearLayout) findViewById(R.id.linearLayout_Settings);
        linearLayout_Route = (LinearLayout) findViewById(R.id.linearLayout_route);

        imageView_Route = (ImageView) findViewById(R.id.imageView_route);

        navigation_bottomNavigation = (BottomNavigationView)findViewById(R.id.bottom_navigation);
        navigation_bottomNavigation.inflateMenu(R.menu.bottom_menu);
        try {
            button_info = navigation_bottomNavigation.findViewById(R.id.action_route);
            button_info.setOnClickListener(this);
            button_calibrate = navigation_bottomNavigation.findViewById(R.id.action_calibrate);
            button_calibrate.setOnClickListener(this);
            button_settings = navigation_bottomNavigation.findViewById(R.id.action_settings);
            button_settings.setOnClickListener(this);
        } catch (Exception e){
//            Log.d("GPSReceiver", "Error setting navigation items up");
//            Log.d("GPSReceiver", e.getMessage());
        }

        button_SwitchToGPS = (Button) findViewById(R.id.getGPS);
        button_SwitchToGPS.setOnClickListener(this);

        button_GetHeight = (Button) findViewById(R.id.getPressure);
        button_GetHeight.setOnClickListener(this);

        button_resetCalibration = (Button) findViewById(R.id.resetCalibration);
        button_resetCalibration.setOnClickListener(this);

        textView_latitude = (TextView) findViewById(R.id.latitude);
        textView_longitude = (TextView) findViewById(R.id.longitude);
        textView_height = (TextView) findViewById(R.id.height);
        textView_recalibrated_height = (TextView) findViewById(R.id.recalibrated_height);
        textView_speed = (TextView) findViewById(R.id.speed);


        loadPreferences();
        setupManualHeightSlider();

        getGPSData();
        getHeightViaPressure();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPreferences();
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePreferences();
    }

    @Override
    protected void onStop() {
        super.onStop();
        savePreferences();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        savePreferences();
    }

    private void savePreferences(){
//        Log.d("GPSReceiver", "Saving Data to File");
        SharedPreferences.Editor editor = getSharedPreferences(getString(R.string.preference_file_key), MODE_PRIVATE).edit();
        editor.clear();
        editor.putFloat("height_offset", this.height_offset);
        editor.apply();
    }

    private void saveGPSInformationToFile(Location location){
        Log.d("GPSReceiver", "Trying to save GPS Data");
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(this.openFileOutput("GPSTracks.csv", Context.MODE_PRIVATE));

            outputStreamWriter.write(
                    location.getLongitude() +
                    "," + location.getLatitude() +
                    "," + location.getSpeed() +
                    ";");
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.d("GPSReceiver", "File write failed: " + e.toString());
        }
    }

    private void createGPSRouteFromFile(Context context) {

        try {
            InputStream inputStream = context.openFileInput("GPSTracks.csv");

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ( (receiveString = bufferedReader.readLine()) != null ) {
                    this.route.add(receiveString);
                }

                inputStream.close();
            }
        }
        catch (FileNotFoundException e) {
            Log.e("GPSReceiver", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("GPSReceiver", "Can not read file: " + e.toString());
        }
    }

    private void loadPreferences(){
//        Log.d("GPSReceiver", "Loading Data from File");
        SharedPreferences prefs = getSharedPreferences(getString(R.string.preference_file_key), MODE_PRIVATE);

//        Log.d("GPSReceiver", "OLD_HeightOffset: " + this.height_offset);
        try{
            this.height_offset = prefs.getFloat("height_offset", 0.0f);
        } catch (Exception e) {
//            Log.d("GPSReceiver", e.getMessage());
        }
//        Log.d("GPSReceiver", "NEW_HeightOffset: " + this.height_offset);

        if (this.height > 0.0f){
            this.height = this.height + this.height_offset;
            this.textView_recalibrated_height.setText(df.format(this.height));
        }
    }

    private void setupManualHeightSlider() {
        slider_SetNewCalibratedHeight = findViewById(R.id.setCalibratedHeight);
        slider_SetNewCalibratedHeight.invalidate();
        slider_SetNewCalibratedHeight.refreshDrawableState();
        float min_calibratedHeight = 0.0f;
        float max_calibratedHeight = 10000.0f;
        slider_SetNewCalibratedHeight.setPosition(this.height);
        slider_SetNewCalibratedHeight.setBubbleText(df.format(this.height));
        slider_SetNewCalibratedHeight.setStartText(Float.toString(min_calibratedHeight) + "m");
        slider_SetNewCalibratedHeight.setEndText(Float.toString(max_calibratedHeight) + "m");
        slider_SetNewCalibratedHeight.setPositionListener(pos -> {
            float manual_CalibratedHeight = pos * max_calibratedHeight;
            this.slider_SetNewCalibratedHeight.setBubbleText(df.format(manual_CalibratedHeight));
            return Unit.INSTANCE;
        });
        slider_SetNewCalibratedHeight.setBeginTrackingListener( () -> {
            this.calibrationActive = true;
            return Unit.INSTANCE;
        });
        slider_SetNewCalibratedHeight.setEndTrackingListener( () -> {
            float pos = slider_SetNewCalibratedHeight.getPosition();
            float manualCalibratedHeight = pos * max_calibratedHeight;
            this.height_offset = manualCalibratedHeight - this.height;
            this.height += this.height_offset;
            this.updateView();
//            Log.d("GPSReceiver" , "New Offset: " + this.height_offset);
            this.calibrationActive = false;
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
//                    Log.d("GPSReceiver", "Permission Granted!");
                } else {
//                    Log.d("GPSReceiver", "Permission Denied!");
                }
        }
    }

    private void showPhoneStatePermissionGPS() {
        int permissionCheck = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
//                Log.d("GPSReceiver", "Permission rationale Needed");
                this.permissionsGranted = false;
            } else {
//                Log.d("GPSReceiver", "Requesting permission for ACCESS_FINE_LOCATION");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        } else {
//            Log.d("GPSReceiver", "Permission for GPS granted");
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
        sensorManager.registerListener(this, pressureSensor, 10000000);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.getGPS:
//                Log.d("GPSReceiver", "Switching to GPS");
                getGPSData();
                break;
            case R.id.getPressure:
//                Log.d("GPSReceiver", "Getting Pressure Data");
                getHeightViaPressure();
                break;
            case R.id.action_route:
                navigationRouteClicked();
                break;
            case R.id.action_calibrate:
                navigationCalibrateClicked();
                break;
            case R.id.action_settings:
                navigationSettingsClicked();
                break;
            case R.id.resetCalibration:
                resetCalibration();
                break;
        }
    }

    private void resetCalibration(){
        this.height_offset = 0.0f;
        this.height = 0.0f;
        savePreferences();
        updateView();
    }

    private void updateView(){
        this.textView_height.setText(df.format(this.height) + "m");
        this.textView_recalibrated_height.setText(df.format(this.height)+"m");
        textView_longitude.setText("longitude: " + df.format(this.longitude));
        textView_latitude.setText("latitude: " + df.format(this.latitude));
        textView_speed.setText("Speed: " + df.format(this.speed));
    }

    private void navigationCalibrateClicked(){
//        Log.d("GPSReceiver", "Navigation Calibrate clicked");
        if(linearLayout_Calibration.getVisibility() == View.GONE){
            linearLayout_Calibration.setVisibility(View.VISIBLE);
            linearLayout_Settings.setVisibility(View.GONE);
            linearLayout_Route.setVisibility(View.GONE);
            linearLayout_standard_content.setVisibility(View.GONE);
        } else {
            linearLayout_Calibration.setVisibility(View.GONE);
            linearLayout_standard_content.setVisibility(View.VISIBLE);
        }
    }

    private void navigationSettingsClicked(){
//        Log.d("GPSReceiver", "Navigation Settings clicked");
        if(linearLayout_Settings.getVisibility() == View.GONE){
            linearLayout_Settings.setVisibility(View.VISIBLE);
            linearLayout_Calibration.setVisibility(View.GONE);
            linearLayout_Route.setVisibility(View.GONE);
            linearLayout_standard_content.setVisibility(View.GONE);
        } else {
            linearLayout_Settings.setVisibility(View.GONE);
            linearLayout_standard_content.setVisibility(View.VISIBLE);
        }
    }

    private void navigationRouteClicked(){
        this.createGPSRouteFromFile(this);

        if(linearLayout_Route.getVisibility() == View.GONE){
            linearLayout_Route.setVisibility(View.VISIBLE);
            linearLayout_Settings.setVisibility(View.GONE);
            linearLayout_Calibration.setVisibility(View.GONE);
            linearLayout_standard_content.setVisibility(View.GONE);
            drawCanvas();

        } else {
            linearLayout_Settings.setVisibility(View.GONE);
            linearLayout_standard_content.setVisibility(View.VISIBLE);
        }
    }

    private void drawCanvas() {
        try{
            int bitmapHeight = linearLayout_main_Content.getHeight();
            int bitmapWidth = linearLayout_main_Content.getWidth();
            int quarterWidth = bitmapWidth / 4;
            Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);

            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.GRAY);
            canvas.drawLine(quarterWidth, 0, quarterWidth, bitmapHeight, paint);
            canvas.drawLine(quarterWidth*2, 0, quarterWidth*2, bitmapHeight, paint);
            canvas.drawLine(quarterWidth*3, 0, quarterWidth*3, bitmapHeight, paint);

            imageView_Route.draw(canvas);
            imageView_Route.setImageBitmap(bitmap);
        } catch (Exception e){
            Log.d("GPSReceiver", e.getMessage());
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
        boolean positionChanged = false;
        if (location.getLongitude() != this.longitude){
            this.longitude = location.getLongitude();
            positionChanged = true;
        }
        if (location.getLatitude() != this.latitude){
            this.latitude = location.getLatitude();
            positionChanged = true;
        }
        if (location.getSpeed() != this.speed){
            this.speed = location.getSpeed();
        }

        if(positionChanged){
            this.saveGPSInformationToFile(location);
        }

        updateView();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(this.calibrationActive == false) {
            if(event.values.length > 0){

                double expo = 1/5.255f;
                double k = 288000/6.5f;

                float pressure = Math.round(event.values[0]);
                float new_height = (float) (k * (1-Math.pow(pressure/1013, expo)));
                this.height = new_height + this.height_offset;

                // adjust with offset from manual Calibration
                textView_height.setText("Height: " + df.format(this.height) + "m");
                textView_recalibrated_height.setText(df.format(this.height) + "m");
            }
        } else {
//            Log.d("GPSReceiver", "OnSensorChange: calibration active");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
//        Log.d("GPSReceiver", "PressureSensor Accuracy-Changed Event");
    }
}
