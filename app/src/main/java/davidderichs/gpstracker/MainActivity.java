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

import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.LinkedList;
import kotlin.Unit;

/**
 * Main Activity is used to control the Apps behaviour
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener, LocationListener, SensorEventListener {

    // Layouts are used to change between different Views.
    LinearLayout linearLayout_main_Content;
    LinearLayout linearLayout_standard_content;
    LinearLayout linearLayout_Calibration;
    LinearLayout linearLayout_Settings;
    LinearLayout linearLayout_Route;

    // ImageView is used to display a Canvas, which is used to draw UTM based Routes
    ImageView imageView_Route;

    // TextViews are used to display current location information.
    TextView textView_latitude;
    TextView textView_longitude;
    TextView textView_height;
    TextView textView_recalibrated_height;
    TextView textView_speed;

    // The App uses a Bottom Navigation to changed to different Views.
    BottomNavigationView navigation_bottomNavigation;
    View button_settings;
    View button_info;
    View button_calibrate;

    // These are Buttons stored on Settings-View
    // They can be sued to Calibrate the height and save the current Route to GPX files.
    Button button_saveRouteToGPX;
    Button button_resetCalibration;
    FluidSlider slider_SetNewCalibratedHeight;

    // Permission and Calibration are critical for the App to function successfully
    boolean permissionsGranted;
    boolean calibrationActive;

    // These values are Used to store all current location information
    double longitude;
    double latitude;
    double speed;
    float height;
    float height_offset;

    // The current route is first stored into GPS_Route and can be converted to UTM_Route
    LinkedList<String> GPS_Route = new LinkedList<String>();
    LinkedList<UTMRef> UTM_Route = new LinkedList<UTMRef>();

    private final int ACCESS_FINE_LOCATION=1;

    // These Managers gather update data from the phones sensors
    LocationManager locationManager;
    SensorManager sensorManager;
    Sensor pressureSensor;

    // The decimal format is used to display values on Views.
    DecimalFormat df;

    // Filemanager is responsible for reading and writing informatino to or from files.
    CustomFileManager fileManager;

    /**
     * Loads preferences on Resume
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadPreferences();
    }
    /**
     * Saves preferences on Pause
     */
    @Override
    protected void onPause() {
        super.onPause();
        savePreferences();
    }
    /**
     * Saves preferences on Stop
     */
    @Override
    protected void onStop() {
        super.onStop();
        savePreferences();
    }
    /**
     * Saves preferences on Destroy
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        savePreferences();
    }

    /**
     * Creates the Views and Models for this MainActivity
     * @param savedInstanceState
     */
    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        height_offset = 0.0f;
        calibrationActive = false;
        df = new DecimalFormat("#.0000");

        showPhoneStatePermissionGPS();
        clearRouteInformation();
        setupFileManager();
        setupUIElements();

        loadPreferences();
        setupManualHeightSlider();

        setupLocationManager();
        setupPressureManager();
    }

    /**
     * Sets up the FileManager for writing files and reading information from them.
     */
    private void setupFileManager(){
        fileManager = new CustomFileManager(this);
        fileManager.clearGPSFile();
    }

    /**
     * Clears all Routing information that could be deprecated.
     */
    private void clearRouteInformation(){
        this.GPS_Route.clear();
        this.UTM_Route.clear();
    }

    /**
     * Sets up the UI Elements of this Activity
     */
    private void setupUIElements(){
        setupLayouts();
        setupImageView();
        setupBottomNavigation();
        setupButtons();
        setupTextViews();
    }

    /**
     * Sets up the Layouts used in this Activity
     */
    private void setupLayouts(){
        linearLayout_main_Content = (LinearLayout) findViewById(R.id.linearLayout_main_Content);
        linearLayout_standard_content = (LinearLayout) findViewById(R.id.linearLayout_standard_content);
        linearLayout_Calibration = (LinearLayout) findViewById(R.id.linearLayout_Calibration);
        linearLayout_Settings = (LinearLayout) findViewById(R.id.linearLayout_Settings);
        linearLayout_Route = (LinearLayout) findViewById(R.id.linearLayout_route);
    }

    /**
     * Sets up the Bottom Navigation.
     */
    private void setupBottomNavigation(){
        navigation_bottomNavigation = (BottomNavigationView)findViewById(R.id.bottom_navigation);
        navigation_bottomNavigation.inflateMenu(R.menu.bottom_menu);
    }

    /**
     * Sets up the ImageView which is used to draw a Canvas containing Routing locations.
     */
    private void setupImageView(){
        imageView_Route = (ImageView) findViewById(R.id.imageView_route);
    }

    /**
     * Sets up the behaviour of TextViews.
     */
    private void setupTextViews(){
        textView_latitude = (TextView) findViewById(R.id.latitude);
        textView_longitude = (TextView) findViewById(R.id.longitude);
        textView_height = (TextView) findViewById(R.id.height);
        textView_recalibrated_height = (TextView) findViewById(R.id.recalibrated_height);
        textView_speed = (TextView) findViewById(R.id.speed);
    }

    /**
     * Sets up the buttons in this Activity
     */
    private void setupButtons(){
        button_saveRouteToGPX = (Button) findViewById(R.id.button_saveRouteToGPS);
        button_saveRouteToGPX.setOnClickListener(this);

        button_resetCalibration = (Button) findViewById(R.id.resetCalibration);
        button_resetCalibration.setOnClickListener(this);

        button_info = navigation_bottomNavigation.findViewById(R.id.action_route);
        button_info.setOnClickListener(this);
        button_calibrate = navigation_bottomNavigation.findViewById(R.id.action_calibrate);
        button_calibrate.setOnClickListener(this);
        button_settings = navigation_bottomNavigation.findViewById(R.id.action_settings);
        button_settings.setOnClickListener(this);
    }

    /**
     * Reloads all UI-Elements with updated values.
     */
    private void updateView(){
        this.textView_height.setText(df.format(this.height) + "m");
        this.textView_recalibrated_height.setText(df.format(this.height)+"m");
        textView_longitude.setText("longitude: " + df.format(this.longitude));
        textView_latitude.setText("latitude: " + df.format(this.latitude));
        textView_speed.setText("Speed: " + df.format(this.speed));
    }
    /**
     * Saves all preferences specified by the user to Android.SharedPreferences
     */
    private void savePreferences(){
//        Log.d("GPSReceiver", "Saving Data to File");
        SharedPreferences.Editor editor = getSharedPreferences(getString(R.string.preference_file_key), MODE_PRIVATE).edit();
        editor.clear();
        editor.putFloat("height_offset", this.height_offset);
        editor.apply();
    }

    /**
     * Appends and saves the current location to a File.
     * @param location
     */
    private void saveGPSInformationToFile(Location location){
        this.fileManager.appendGPSLocationToFile(location);
    }

    /**
     * Gathers stored GPSRoute information from file and adds it to its runtime GPS_Route
     */
    private void createGPSRouteFromFile() {
        LinkedList<String> locations = fileManager.readGPSLocationsFromFile();
        for (String location : locations){
            this.GPS_Route.add(location);
        }
    }

    /**
     * Exports the currently stored GPS information and transfers this to a GPX file.
     */
    private void exportGPSRouteToGPX() {
        fileManager.saveRouteToGPX();
    }

    /**
     * Loads preferences stored as Android.SharedPreference
     */
    private void loadPreferences(){
        SharedPreferences prefs = getSharedPreferences(getString(R.string.preference_file_key), MODE_PRIVATE);

        try{
            this.height_offset = prefs.getFloat("height_offset", 0.0f);
        } catch (Exception e) {
            Log.d("GPSReceiver", e.getMessage());
        }

        if (this.height > 0.0f){
            this.height = this.height + this.height_offset;
            this.textView_recalibrated_height.setText(df.format(this.height));
        }
    }

    /**
     * Sets up a Slider, which can be used to calibrate height information.
     */
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

    /**
     * Checks for App permissions and logs result onto LogCat
     * @param requestCode RequestCode to be checked
     * @param permissions Not used in this case
     * @param grantResults Not used in this case
     */
    @Override
    public void onRequestPermissionsResult( int requestCode, String permissions[], int[] grantResults) {
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

    /**
     * Checks if additional Persmissions are needed for this Application to work.
     * Output on LogCat
     */
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

    /**
     * Sets Up a LocationManager for location sensor updates.
     */
    @SuppressLint("MissingPermission")
    private void setupLocationManager() {
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }

    /**
     * Sets up the Pressure sensor Management.
     */
    @SuppressLint("MissingPermission")
    private void setupPressureManager() {
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        sensorManager.registerListener(this, pressureSensor, 10000000);
    }

    /**
     * Manages different Onclick-Events for View Elements.
     * @param v Clicked View
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_saveRouteToGPS:
                exportGPSRouteToGPX();
                break;
            case R.id.action_route:
                onClick_navigationRoute();
                break;
            case R.id.action_calibrate:
                onClick_navigation_Calibrate();
                break;
            case R.id.action_settings:
                onClick_NavigationSettings();
                break;
            case R.id.resetCalibration:
                resetCalibration();
                break;
        }
    }

    /**
     * Sets Calibrated Height and Height_offset to its default values.
     */
    private void resetCalibration(){
        this.height_offset = 0.0f;
        this.height = 0.0f;
        savePreferences();
        updateView();
    }

    /**
     * Displays the Calibration View and hides all other Layout-Views.
     */
    private void onClick_navigation_Calibrate(){
//        Log.d("GPSReceiver", "Navigation Calibrate clicked");
        if(linearLayout_Calibration.getVisibility() == View.GONE){
            display_linearLayout_Calibration();
        } else {
            display_linearLayout_Standard();
        }
    }

    /**
     * Displays the Navigation-Settings View and hides all other Layout-Views.
     */
    private void onClick_NavigationSettings(){
//        Log.d("GPSReceiver", "Navigation Settings clicked");
        if(linearLayout_Settings.getVisibility() == View.GONE){
            display_linearLayout_Settings();
        } else {
            display_linearLayout_Standard();
        }
    }

    /**
     * Displays the Navigation-Route View and hides all other Layout-Views.
     */
    private void onClick_navigationRoute(){
        this.createGPSRouteFromFile();
        this.convertGPSRouteToUTMRoute();

        if(linearLayout_Route.getVisibility() == View.VISIBLE){
            display_linearLayout_Standard();
        }else {
            display_linearLayout_Route();
            drawUTMRouteOnCanvasToImageView();
        }
    }

    private void display_linearLayout_Standard(){
        linearLayout_Route.setVisibility(View.GONE);
        linearLayout_Settings.setVisibility(View.GONE);
        linearLayout_Calibration.setVisibility(View.GONE);
        linearLayout_standard_content.setVisibility(View.VISIBLE);
    }

    private void display_linearLayout_Route(){
        linearLayout_Route.setVisibility(View.VISIBLE);
        linearLayout_Settings.setVisibility(View.GONE);
        linearLayout_Calibration.setVisibility(View.GONE);
        linearLayout_standard_content.setVisibility(View.GONE);
    }

    private void display_linearLayout_Settings(){
        linearLayout_Route.setVisibility(View.GONE);
        linearLayout_Settings.setVisibility(View.VISIBLE);
        linearLayout_Calibration.setVisibility(View.GONE);
        linearLayout_standard_content.setVisibility(View.GONE);
    }

    private void display_linearLayout_Calibration(){
        linearLayout_Route.setVisibility(View.GONE);
        linearLayout_Settings.setVisibility(View.GONE);
        linearLayout_Calibration.setVisibility(View.VISIBLE);
        linearLayout_standard_content.setVisibility(View.GONE);
    }

    /**
     * Transfers GPS_Route Object into UTM_Route Object
     */
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

    /**
     * Returns the Maximum Eastern Value of all UTMRoute Locations
     * @return
     */
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
    /**
     * Returns the Minimum Eastern Value of all UTMRoute Locations
     * @return
     */
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
    /**
     * Returns the Maximum Northern Value of all UTMRoute Locations
     * @return
     */
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
    /**
     * Returns the Minimum Northern Value of all UTMRoute Locations
     * @return
     */
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

    /**
     * Draws the current UTM_Route onto a Canvas, which is then applied to the Apps ImageView.
     */
    private void drawUTMRouteOnCanvasToImageView() {
        imageView_Route.invalidate();

        try{
            int calculatedHeight = linearLayout_main_Content.getHeight();
            int calculatedWidth = linearLayout_main_Content.getWidth();

            int quarterWidth = calculatedWidth / 4;
            Bitmap bitmap = Bitmap.createBitmap(calculatedWidth, calculatedHeight, Bitmap.Config.ARGB_8888);

            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            paint.setColor(Color.GRAY);
//            canvas.drawLine(0, 0, 0, calculatedHeight, paint);
//            canvas.drawLine(calculatedWidth, 0, calculatedWidth, calculatedHeight, paint);
            canvas.drawLine(quarterWidth*2, 0, quarterWidth*2, calculatedHeight, paint);

            if(calculatedHeight > calculatedWidth){
                calculatedHeight = calculatedWidth;
            } else {
                calculatedWidth = calculatedHeight;
            }

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

                xPos1 = ( (maxEastUTMValue-minEastUTMValue) / (routeEntry.getEasting()*calculatedWidth) );
                yPos1 = ( (maxNorthUTMValue-minNorthUTMValue) / (routeEntry.getNorthing()*calculatedHeight) );

                float xPos1Canvas = (float) xPos1 * calculatedWidth;
                float yPos1Canvas = (float) yPos1 * calculatedHeight;
                yPos1Canvas = calculatedHeight - yPos1Canvas;

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

                    float xPos1Canvas = (float) xPos1 * calculatedWidth;
                    float yPos1Canvas = (float) yPos1 * calculatedHeight;
                    yPos1Canvas = calculatedHeight - yPos1Canvas;

                    float xPos2Canvas = (float) xPos2 * calculatedWidth;
                    float yPos2Canvas = (float) yPos2 * calculatedHeight;
                    yPos2Canvas = calculatedHeight - yPos2Canvas;

//                    Log.d("GPSReceiver", "xpos1: " + xPos1Canvas);
//                    Log.d("GPSReceiver", "Northing: " + routeEntry.getNorthing());
//                    Log.d("GPSReceiver", "ypos1: " + yPos1Canvas);
//                    Log.d("GPSReceiver", "xpos2: " + xPos2Canvas);
//                    Log.d("GPSReceiver", "ypos2: " + yPos2Canvas);

                    canvas.drawLine(xPos1Canvas, yPos1Canvas, xPos2Canvas, yPos2Canvas, paint);

                    xPos1 = xPos2;
                    yPos1 = yPos2;
                }
            }

//            canvas.drawLine(quarterWidth*3, 0, quarterWidth*3, bitmapHeight, paint);

            imageView_Route.draw(canvas);
            imageView_Route.setImageBitmap(bitmap);
        } catch (Exception e){
            Log.d("GPSReceiver", e.getMessage());
        }
    }

    /**
     * Manages location-changed events of the phones location sensor.
     * If new Location is found, it is stored as runtime object but also directly stored to file.
     * @param location Updated Location
     */
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

    /**
     * Manages pressure-Sensor events.
     * Uses the current pressure value and calculates the height correspondendly.
     * @param event
     */
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

    // NOT IMPLEMENTED
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    // NOT IMPLEMENTED
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    // NOT IMPLEMENTED
    @Override
    public void onProviderEnabled(String provider) {}
    // NOT IMPLEMENTED
    @Override
    public void onProviderDisabled(String provider) {}
}
