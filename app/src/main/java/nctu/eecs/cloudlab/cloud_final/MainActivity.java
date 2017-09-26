package nctu.eecs.cloudlab.cloud_final;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.RoundingMode;
import java.net.Socket;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.StringTokenizer;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    SensorManager sensorManager;
    LocationManager locationManager;
    LocationListener locationListener ;
    Context mContext ;
    Handler mHandler ;
    Sensor magnet, accelerometer;
    double lat, lon;
    TextView speed , distance ;
    ImageView arrow ;
    Spinner spinner ;
    Button go ;
    ProgressBar progressBar ;
    long currentTimeMillis;
    float last_degree = 0 ;
    float current_degree = 0 ;
    double cur_dest_lat = 0 ;
    double cur_dest_lon = 0 ;
    double last_dest_lat = 0 ;
    double last_dest_lon = 0 ;
    double total_dist = 0 ;
    double lowPassSpeed = 0;
    String IP = "140.113.179.15" ;
    int port = 8000 ;
    static double EARTH_RADIUS = 6378137 ;
    static double speedLowPassCoeff = 0.15;
    ImageView locating ;
    Socket mClientSocket ;
    //BufferedReader reader ;
    //BufferedWriter writer ;
    DataOutputStream writer ;
    DataInputStream reader ;
    String destination ;
    String naviDestination;
    boolean btn_available = false ;
    boolean locating_available = false ;
    boolean first = true ;
    boolean isNavigating = false;
    boolean canNavigation = true;

    ValueAnimator colorAnimation ;

    int Send_Duration = 1 ;
  //  int colorFrom = Color.parseColor("#FFFFFF");
  //  int colorTo = Color.parseColor("#FFFFAA");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this.getApplicationContext();
        speed = (TextView) findViewById(R.id.tv_speed) ;
        distance = (TextView) findViewById(R.id.tv_distance) ;
        arrow = (ImageView) findViewById(R.id.imageView);
        locating = (ImageView) findViewById(R.id.imageView2) ;
        locating.setImageAlpha(100);
        spinner = (Spinner) findViewById(R.id.spinner) ;
        go = (Button) findViewById(R.id.btn_go) ;
        progressBar = (ProgressBar) findViewById(R.id.progressBar) ;
        progressBar.setMax(100);
        progressBar.setProgress(0);

        Resources res = getResources();
        final String[] buildings = res.getStringArray(R.array.buidings);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        magnet = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        Animation animation = new AlphaAnimation(1, 0);
        animation.setDuration(600);
        animation.setInterpolator(new LinearInterpolator());
        animation.setRepeatCount(Animation.INFINITE);
        animation.setRepeatMode(Animation.REVERSE);
        arrow.startAnimation(animation);
        locating.startAnimation(animation);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                lon = location.getLongitude() ;
                lat = location.getLatitude() ;
                if (!btn_available) {
                    go.setEnabled(true);
                    btn_available = true ;
                    change_color(Color.parseColor("#FFFFFF"),Color.parseColor("#FFFFAA"));
                }
                if (!locating_available) {
                    Log.d("Alpha" , String.valueOf(locating.getImageAlpha())) ;
                    locating.setImageAlpha(255);
                    locating.setAnimation(null);
                    locating_available = true ;
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isNavigating = false;
                while(!canNavigation);

                currentTimeMillis = System.currentTimeMillis();
                naviDestination = destination;
                first = true;
                last_dest_lat = 0 ;
                last_dest_lon = 0 ;
                progressBar.setProgress(0);

                Thread request ;
                request = new Request_Thread() ;
                request.start();
            }
        });

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //Toast.makeText(mContext,"select: " + buidings[position],Toast.LENGTH_SHORT).show() ;
                destination = buildings[position] ;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        sensorManager.registerListener(this,magnet,SensorManager.SENSOR_DELAY_NORMAL) ;
        mHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch(msg.what){
                    case 1: // update distance
                        double dist = msg.getData().getDouble("distance_update") ;
                        distance.setText(String.valueOf(dist) + " m");
                        double progressStatus = dist/total_dist;
                        if(progressStatus > 1.0)
                            progressStatus = 1.0;

                        int nowProgress = (int) Math.round(100 * (1.0 - (progressStatus)));
                        progressBar.setProgress(nowProgress);

                        //progressBar.setProgress(50);
                        break ;
                    case 2: //update speed
                        DecimalFormat formatter = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
                        formatter.setRoundingMode(RoundingMode.DOWN);

                        speed.setText(formatter.format(msg.getData().getDouble("speed_update"))  + " m/min");
                        break;
                    case 3:
                        change_color(Color.parseColor("#FFFFAA"),Color.parseColor("#BEFF8F"));
                        break ;
                }

                String s ;
                s = msg.getData().getString("recv_data") ;
            }
        } ;
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if(mClientSocket != null){
                mClientSocket.close();
                reader.close();
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void change_color(int colorFrom , int colorTo){
        colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setDuration(1000); // milliseconds
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                findViewById(R.id.background).setBackgroundColor((int) animator.getAnimatedValue());
            }
        });
        colorAnimation.start();
    }

    void spin_arrow(float degree){
        RotateAnimation anim = new RotateAnimation(current_degree, -degree, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,0.5f);
        anim.setInterpolator(new LinearInterpolator());
        anim.setDuration(500);
        anim.setFillAfter(true);

        // Start animating the image
        arrow.startAnimation(anim);
        current_degree = -degree ;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float degree_phone = event.values[0];
        if(lon != 0 && lat !=0 && cur_dest_lon != 0){
           // double degree_dist = Math.atan2(cur_dest_lat - lat , cur_dest_lon - lon) ;
            double degree_dist = Math.atan2(cur_dest_lon - lon, cur_dest_lat - lat) ;
            degree_dist = (degree_dist*(180/Math.PI)) % 360 ;
            double diff = degree_phone - degree_dist ;
            if(diff < 0){
                diff += 360 ;
            }
            if(diff < 180){
                // do nothing
            } else if (diff > 180) {
                diff = diff - 360 ;
            }
            //distance.setText(" " + diff);
            spin_arrow((float)(diff));
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    class Request_Thread extends Thread {
        @Override
        public void run() {
            super.run();
            isNavigating = true;
            canNavigation = false;

            try {
                Log.d("thread" , "in thread") ;
                mClientSocket = new Socket(IP,port) ;
                reader = new DataInputStream(mClientSocket.getInputStream()) ;
                writer = new DataOutputStream(mClientSocket.getOutputStream()) ;
                Log.d("thread" , "connect complete") ;
            } catch (IOException e) {
                e.printStackTrace();
            }
            double last_lon=0 , last_lat=0 ;
            while(!mClientSocket.isClosed() && isNavigating){
                try {
                    if(last_lat != 0) {
                        long tempTime = System.currentTimeMillis();
                        long diffTime =  tempTime - currentTimeMillis;
                        currentTimeMillis = tempTime;

                        double newSpeed = GetDistance(lon,lat,last_lon,last_lat) * 60.0 / (diffTime / 1000.0);
                        lowPassSpeed = lowPassSpeed *(1.0-speedLowPassCoeff) + newSpeed * speedLowPassCoeff;
                        send_msg(2,lowPassSpeed); // speed update
                    }

                    last_lat = lat ;
                    last_lon = lon ;
                    String msg = "Navigation "+
                            String.valueOf(last_dest_lat)+" "+String.valueOf(last_dest_lon)+" " +
                            String.valueOf(lat)+" "+String.valueOf(lon)+" " +
                            naviDestination+"#" ;
                    writer.writeUTF(msg) ;
                    System.out.println(msg);
                    writer.flush();
                    String s = reader.readUTF();
                    String[] token = s.split(" ");

                    if(first) {
                        cur_dest_lat = Double.valueOf(token[0]);
                        cur_dest_lon = Double.valueOf(token[1]);
                        total_dist = Double.valueOf(token[2]);
                        send_msg(3,0);
                        first = false;
                    }

                    if(!(Math.abs(cur_dest_lat - Double.valueOf(token[0])) < 1e-06 &&
                            Math.abs(cur_dest_lon - Double.valueOf(token[1])) < 1e-06)) {
                        last_dest_lat = cur_dest_lat ;
                        last_dest_lon = cur_dest_lon ;
                        cur_dest_lat = Double.valueOf(token[0]);
                        cur_dest_lon = Double.valueOf(token[1]);
                    }

                    /*
                    if(!first){
                        if(cur_dest_lat != Double.valueOf(token[0]) &&
                                cur_dest_lon != Double.valueOf(token[1])){
                            last_lat = cur_dest_lat ;
                            last_lon = cur_dest_lon ;
                        }
                    }
                    else{
                        total_dist = Double.valueOf(token[2]) ;
                        first = false ;
                    }

                    // change destination here
                    cur_dest_lat = Double.valueOf(token[0]) ;
                    cur_dest_lon = Double.valueOf(token[1]) ;*/
                    send_msg(1,Double.valueOf(token[2]));

                    Log.d("thread" , s) ;
                    sleep(Send_Duration*1000);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if(!mClientSocket.isClosed()) {
                try {
                    mClientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            canNavigation = true;
        }
    }

    void send_msg(int what , double value){
        Message m = new Message() ;
        Bundle b = new Bundle() ;
        if(what == 1){
            b.putDouble("distance_update", value);
            m.what = 1 ;
            m.setData(b);
        }
        else if(what == 2){
            b.putDouble("speed_update", value);
            m.what = 2 ;
            m.setData(b);
        }
        else if(what == 3){
            b.putDouble("speed_update", value);
            m.what = 3 ;
            m.setData(b);
        }
        mHandler.sendMessage(m) ;
    }

    private static double rad(double d)
    {
        return d * Math.PI / 180.0;
    }

    public static double GetDistance(double lng1, double lat1, double lng2, double lat2)
    {
        double radLat1 = rad(lat1);
        double radLat2 = rad(lat2);
        double a = radLat1 - radLat2;
        double b = rad(lng1) - rad(lng2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a/2),2) +
                Math.cos(radLat1)*Math.cos(radLat2)*Math.pow(Math.sin(b/2),2)));
        s = s * EARTH_RADIUS ;
        s = Math.round(s * 10000) / 10000;
        return s;
    }

}
