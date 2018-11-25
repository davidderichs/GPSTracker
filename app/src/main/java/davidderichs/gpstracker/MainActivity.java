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
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.ramotion.fluidslider.FluidSlider;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.Iterator;
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

    LinkedList<String> GPS_Route = new LinkedList<String>();
    LinkedList<UTMRef> UTM_Route = new LinkedList<UTMRef>();

    private final int ACCESS_FINE_LOCATION=1;

    Button button_saveRouteToGPX;
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

        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(this.openFileOutput("GPSTracks.csv", Context.MODE_PRIVATE));
            outputStreamWriter.write("");
        } catch (FileNotFoundException e) {

            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.GPS_Route.clear();
        this.UTM_Route.clear();

        height_offset = 0.0f;
        calibrationActive = false;

        df = new DecimalFormat("#.0000");


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

        button_saveRouteToGPX = (Button) findViewById(R.id.button_saveRouteToGPS);
        button_saveRouteToGPX.setOnClickListener(this);

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
        try {

            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(this.openFileOutput("GPSTracks.csv", Context.MODE_APPEND));

            outputStreamWriter.write(
                    location.getLatitude() +
                    "," + location.getLongitude() +
                    "," + location.getSpeed() +
                    ";");
            outputStreamWriter.write(System.getProperty("line.separator"));
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
                    this.GPS_Route.add(receiveString);
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

    private void saveRouteToGPX() {
        String header = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?><gpx xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"MapSource 6.15.5\" version=\"1.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"  xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\"><trk>\n";
        String name = "<name>" + "My Route" + "</name><trkseg>\n";

        String segments = "";

        this.createGPSRouteFromFile(this);

        Iterator routeIterator = this.GPS_Route.iterator();
        Log.d("GPSReceiver", "route Size: " + this.GPS_Route.size());
        while (routeIterator.hasNext()){
            String routeItem = (String) routeIterator.next();
            String[] routeInfo = routeItem.split(",");
            String longitude = routeInfo[0];
            String latitude = routeInfo[1];
            segments += "<trkpt lat='" + latitude + "' lon='" + longitude + "'></trkpt>";
        }

        String footer = "</trkseg></trk></gpx>";


        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(this.openFileOutput("Route.gpx", Context.MODE_PRIVATE));
            outputStreamWriter.append(header);
            outputStreamWriter.append(name);
            outputStreamWriter.append(segments);
            outputStreamWriter.append(footer);
            outputStreamWriter.flush();
            outputStreamWriter.close();
            Log.d("GPSReceiver", "Saved GPX");
            Log.d("GPSReceiver", "File Location: " + this.getFilesDir().getAbsolutePath());
        } catch (FileNotFoundException e) {

            e.printStackTrace();
        } catch (IOException e) {
            Log.e("GPSReceiver", "Error Writting Path: " + e.getMessage());
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
            case R.id.button_saveRouteToGPS:
                saveRouteToGPX();
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
        this.convertGPSRouteToUTMRoute();

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

    private void convertGPSRouteToUTMRoute(){
        Iterator routeIterator = this.GPS_Route.iterator();
        while (routeIterator.hasNext()){
            String routeItem = (String) routeIterator.next();
            String[] routeInfo = routeItem.split(",");

            LatLng gps = new LatLng( new Double(routeInfo[0]), new Double (routeInfo[1]) );

            UTMRef utm = gps.toUTMRef();

            this.UTM_Route.add(utm);
        }
    }

    private double getMaxEastFromUTMRoute(){
        Iterator it = UTM_Route.iterator();

        double maxValue = Double.MIN_VALUE;

        while (it.hasNext()){
            UTMRef routeEntry = (UTMRef) it.next();
            if(routeEntry.getEasting()>maxValue){
                maxValue = routeEntry.getEasting();
            }
        }

        return maxValue;
    }

    private double getMinEastFromUTMRoute(){
        Iterator it = UTM_Route.iterator();

        double minValue = Double.MAX_VALUE;

        while (it.hasNext()){
            UTMRef routeEntry = (UTMRef) it.next();
            if(routeEntry.getEasting()<minValue){
                minValue = routeEntry.getEasting();
            }
        }

        return minValue;
    }

    private double getMaxNorthFromUTMRoute(){
        Iterator it = UTM_Route.iterator();
        double maxValue = Double.MIN_VALUE;
        while (it.hasNext()){
            UTMRef routeEntry = (UTMRef) it.next();
            if(routeEntry.getNorthing()>maxValue){
                maxValue = routeEntry.getNorthing();
            }
        }
        return maxValue;
    }

    private double getMinNorthFromUTMRoute(){
        Iterator it = UTM_Route.iterator();
        double minValue = Double.MAX_VALUE;
        while (it.hasNext()){
            UTMRef routeEntry = (UTMRef) it.next();
            if(routeEntry.getNorthing()<minValue){
                minValue = routeEntry.getNorthing();
            }
        }
        return minValue;
    }

    private void drawCanvas() {
        try{
            int bitmapHeight = linearLayout_main_Content.getHeight();
            int bitmapWidth = linearLayout_main_Content.getWidth();
            int quarterWidth = bitmapWidth / 4;
            Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);

            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            paint.setColor(Color.BLUE);

            double maxEastUTMValue = getMaxEastFromUTMRoute();
            double minEastUTMValue = getMinEastFromUTMRoute();

            double maxNorthUTMValue = getMaxNorthFromUTMRoute();
            double minNorthUTMValue = getMinNorthFromUTMRoute();

            int longZone;
            char latZone;

            if (this.UTM_Route.size() == 0){
                return;
            }else if(this.UTM_Route.size() < 2){
                Iterator it = this.UTM_Route.iterator();
                UTMRef routeEntry = (UTMRef) it.next();

                longZone = routeEntry.getLngZone();
                latZone = routeEntry.getLatZone();

                double xPos1;
                double yPos1;

                xPos1 = ( (maxEastUTMValue-minEastUTMValue) / (routeEntry.getEasting()*bitmapWidth) );
                yPos1 = ( (maxNorthUTMValue-minNorthUTMValue) / (routeEntry.getNorthing()*bitmapHeight) );

                float xPos1Canvas = (float) xPos1 * bitmapWidth;
                float yPos1Canvas = (float) yPos1 * bitmapHeight;

                canvas.drawLine(xPos1Canvas, yPos1Canvas, xPos1Canvas, yPos1Canvas, paint);
                canvas.drawText((latZone+Integer.toString(longZone)), 10, 25, paint);
            } else {
                double xPos1;
                double yPos1;
                double xPos2;
                double yPos2;

                Iterator it = this.UTM_Route.iterator();
                UTMRef routeEntry = (UTMRef) it.next();

                longZone = routeEntry.getLngZone();
                latZone = routeEntry.getLatZone();

                xPos1 = ( ( (routeEntry.getEasting() - minEastUTMValue) ) / (maxEastUTMValue-minEastUTMValue) );
                yPos1 = ( ( (routeEntry.getNorthing() - minNorthUTMValue) ) / (maxNorthUTMValue-minNorthUTMValue) );
                while(it.hasNext()){
                    routeEntry = (UTMRef) it.next();
                    xPos2 = ( ( (routeEntry.getEasting() - minEastUTMValue) ) / (maxEastUTMValue-minEastUTMValue) );
                    yPos2 = ( ( (routeEntry.getNorthing() - minNorthUTMValue) ) / (maxNorthUTMValue-minNorthUTMValue) );

                    float xPos1Canvas = (float) xPos1 * bitmapWidth;
                    float yPos1Canvas = (float) yPos1 * bitmapHeight;

                    float xPos2Canvas = (float) xPos2 * bitmapWidth;
                    float yPos2Canvas = (float) yPos2 * bitmapHeight;

                    Log.d("GPSReceiver", "xpos1: " + xPos1Canvas);
                    Log.d("GPSReceiver", "Northing: " + routeEntry.getNorthing());
                    Log.d("GPSReceiver", "ypos1: " + yPos1Canvas);
                    Log.d("GPSReceiver", "xpos2: " + xPos2Canvas);
                    Log.d("GPSReceiver", "ypos2: " + yPos2Canvas);

                    canvas.drawLine(xPos1Canvas, yPos1Canvas, xPos2Canvas, yPos2Canvas, paint);

                    xPos1 = xPos2;
                    yPos1 = yPos2;
                }
            }

            paint.setColor(Color.RED);
//            canvas.drawLine(quarterWidth, 0, quarterWidth, bitmapHeight, paint);
            canvas.drawLine(quarterWidth*2, 0, quarterWidth*2, bitmapHeight, paint);
            paint.setTextSize(50);
            canvas.drawText((latZone+Integer.toString(longZone)), quarterWidth*2, 0, paint);
//            canvas.drawLine(quarterWidth*3, 0, quarterWidth*3, bitmapHeight, paint);

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
        if (this.longitude != location.getLongitude() || this.latitude != location.getLatitude()){
            this.longitude = location.getLongitude();
            this.latitude = location.getLatitude();
            this.speed = location.getSpeed();

            Log.d("GPSReceiver", "location has changed, saving.");
            this.saveGPSInformationToFile(location);

            updateView();
        }
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
