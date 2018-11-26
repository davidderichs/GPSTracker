package davidderichs.gpstracker;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.LinkedList;

/**
 * CustomFileManager is used to store and read information from GPSFiles.
 */
public class CustomFileManager {

    Context context;

    /**
     * Uses the given Android Context
     * @param context
     */
    CustomFileManager(Context context){
        this.context = context;
    }

    /**
     * Clears GPS file from deprecated values.
     */
    public void clearGPSFile(){
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("GPSTracks.csv", Context.MODE_PRIVATE));
            outputStreamWriter.write("");
        } catch (FileNotFoundException e) {

            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Takes a location Object and writes it's values into file "GPSTracks.csv"
     * @param location Location Object to be stores in CSV File
     * @return returns true, if file could be written on
     */
    public boolean appendGPSLocationToFile(Location location){
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("GPSTracks.csv", Context.MODE_APPEND));

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
        return true;
    }

    /**
     * Reads file "GPSTracks.csv" and returns it's values as List
     * @return LinkedList<String> containing all saved tracked locations.
     */
    public LinkedList<String> readGPSLocationsFromFile() {

        LinkedList<String> stringList = new LinkedList<String>();

        try {
            InputStream inputStream = context.openFileInput("GPSTracks.csv");

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";

                while ( (receiveString = bufferedReader.readLine()) != null ) {
                    stringList.add(receiveString);
                }

                inputStream.close();
            }
        }
        catch (FileNotFoundException e) {
            Log.e("GPSReceiver", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("GPSReceiver", "Can not read file: " + e.toString());
        }
        return stringList;
    }

    /**
     * Saves the route stored in "GPSTracks.csv" and transfers them into a GPX file "Route.gpx"     *
     */
    public void saveRouteToGPX() {
        String header = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?><gpx xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"MapSource 6.15.5\" version=\"1.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"  xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\"><trk>\n";
        String name = "<name>" + "My Route" + "</name><trkseg>\n";

        String segments = "";

        for ( String routeItem : readGPSLocationsFromFile()){
            String[] routeInfo = routeItem.split(",");
            String longitude = routeInfo[0];
            String latitude = routeInfo[1];
            segments += "<trkpt lat='" + latitude + "' lon='" + longitude + "'></trkpt>";
        }

        String footer = "</trkseg></trk></gpx>";

        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("Route.gpx", Context.MODE_PRIVATE));
            outputStreamWriter.append(header);
            outputStreamWriter.append(name);
            outputStreamWriter.append(segments);
            outputStreamWriter.append(footer);
            outputStreamWriter.flush();
            outputStreamWriter.close();
            Log.d("GPSReceiver", "Saved GPX");
            Log.d("GPSReceiver", "File Location: " + context.getFilesDir().getAbsolutePath());
        } catch (FileNotFoundException e) {

            e.printStackTrace();
        } catch (IOException e) {
            Log.e("GPSReceiver", "Error Writting Path: " + e.getMessage());
        }
    }


}
