package edu.umb.cs443;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;


public class MainActivity extends FragmentActivity implements OnMapReadyCallback{

	public final static String DEBUG_TAG="edu.umb.cs443.MYMSG";


    private GoogleMap mMap;
    TextView routeBox;
    ArrayList<String> existingRoutes;
    boolean update;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MapFragment mFragment=((MapFragment) getFragmentManager().findFragmentById(R.id.map));
        mFragment.getMapAsync(this);
        existingRoutes = new ArrayList<>();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    public void getBusInfo(View v){
        TextView searchBox = findViewById(R.id.editText); // get search query
        routeBox = findViewById(R.id.textView); // box for route
        update = false;

        String mb = searchBox.getText().toString();
        if (existingRoutes.contains(mb)) {
            update = true;
        }
        else {
            existingRoutes.add(mb);
        }
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();


        if (networkInfo != null && networkInfo.isConnected()) {
            if (update) {
                mMap.clear();
                for (String r : existingRoutes) {
                    DownloadThread d = new DownloadThread(r);
                    new Thread(d).start();
                }
            }
            else {
                DownloadThread d = new DownloadThread(mb);
                new Thread(d).start();
            }

        } else {
            Toast.makeText(getApplicationContext(), "No network connection available", Toast.LENGTH_SHORT).show();
        }
    }

    private class DownloadThread implements Runnable{
        private String vUrl;
        private String pUrl;
        String baseVehicles = "https://api-v3.mbta.com/vehicles?include=route&filter%5Broute%5D=";
        String baseShapes = "https://api-v3.mbta.com/shapes?filter%5Broute%5D=";

        DownloadThread(String rt) {
            this.vUrl = baseVehicles + rt;
            this.pUrl = baseShapes + rt;
        }

        @Override
        public void run() {
            try {
                String retV = downloadUrl(vUrl);
                String retP = downloadUrl(pUrl);
                ArrayList<Vehicle> vehicles = getVehicles(retV);
                ArrayList<PolylineOptions> polylines = getPolylines(retP);

                runOnUiThread(new Runnable() {
                    public void run() {
                        for (Vehicle v : vehicles) {
                            LatLng loc = new LatLng(v.getLat(), v.getLng());
                            String t = v.getRtId() + " " + v.getDirectionName();
                            mMap.addMarker(
                                    new MarkerOptions()
                                            .position(loc)
                                            .title(t)
                                            .snippet(v.getDestination()));
                        }
                        for (PolylineOptions p : polylines) {
                            mMap.addPolyline(p);
                        }
                        if (!update) {
                            routeBox.setText(String.format("%s - %s", vehicles.get(0).getRtId(), vehicles.get(0).getRtName()));
                        }
                        LatLng loc = new LatLng(vehicles.get(0).getLat(), vehicles.get(0).getLng());
                        mMap.moveCamera(CameraUpdateFactory.newLatLng(loc));
                        mMap.animateCamera(CameraUpdateFactory.zoomTo(12));

                    }
                });


            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private String downloadUrl(String myurl) throws IOException {
            InputStream is = null;
            String rsp; // string for response

            try {
                URL url = new URL(myurl); // make URL object
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection(); // open connection
                conn.setRequestMethod("GET");

                // Starts the query
                conn.connect();

                int response = conn.getResponseCode();
                Log.i(DEBUG_TAG, "The response is: " + response);
                is = new BufferedInputStream(conn.getInputStream()); // get InputStream for response
                BufferedReader br = new BufferedReader(new InputStreamReader(is)); // make BufferedReader for it
                rsp = br.readLine(); // read response into string
                return rsp; // return it

            }catch(Exception e) {
                Log.i(DEBUG_TAG, e.toString());
            }finally {
                if (is != null) {
                    is.close();
                }
            }

            return null;
        }

        ArrayList<Vehicle> getVehicles(String retString) {
            ArrayList<Vehicle> vehicles = new ArrayList<>();
            try {
                JSONObject obj = new JSONObject(retString);
                JSONArray dta = obj.getJSONArray("data");
                for (int i = 0; i < dta.length(); i++) {
                    Vehicle v = new Vehicle();
                    JSONObject j = dta.getJSONObject(i);
                    JSONObject att = j.getJSONObject("attributes");
                    v.setDirectionId(att.getInt("direction_id"));
                    v.setId(att.getString("label"));
                    v.setLat(att.getDouble("latitude"));
                    v.setLng(att.getDouble("longitude"));

                    JSONArray inc = obj.getJSONArray("included");
                    JSONObject incl = inc.getJSONObject(0);
                    v.setRtId(incl.getString("id"));
                    JSONObject attr = incl.getJSONObject("attributes");
                    JSONArray dest = attr.getJSONArray("direction_destinations");
                    v.setDestination(dest.getString(v.getDirectionId()));
                    JSONArray dir = attr.getJSONArray("direction_names");
                    v.setDirectionName(dir.getString(v.getDirectionId()));
                    v.setRtName(attr.getString("long_name"));

                    vehicles.add(v);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return vehicles;
        }
        ArrayList<PolylineOptions> getPolylines(String retString) {
            ArrayList<PolylineOptions> polylines = new ArrayList<>();
            try {
                JSONObject obj = new JSONObject(retString);
                JSONArray dta = obj.getJSONArray("data");
                for (int i = 0; i < dta.length(); i++) {
                    JSONObject j = dta.getJSONObject(i);
                    JSONObject att = j.getJSONObject("attributes");
                    String pLine = att.getString("polyline");
                    PolylineOptions p = new PolylineOptions().addAll(PolyUtil.decode(pLine));
                    polylines.add(p);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return polylines;
        }
    }



    @Override
    public void onMapReady(GoogleMap map) {
        this.mMap=map;
    }
}
