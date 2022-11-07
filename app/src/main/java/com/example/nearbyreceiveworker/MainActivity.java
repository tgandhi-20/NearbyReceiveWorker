package com.example.nearbyreceiveworker;

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.SimpleArrayMap;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionType;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String[] REQUIRED_PERMISSIONS;
    public String DeviceEndPointID;
    public String transfer = "File Received!!";
    public SignRecognition signRecognition;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            REQUIRED_PERMISSIONS =
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_ADVERTISE,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.CHANGE_WIFI_STATE,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                    };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            REQUIRED_PERMISSIONS =
                    new String[]{
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.CHANGE_WIFI_STATE,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                    };
        } else {
            REQUIRED_PERMISSIONS =
                    new String[]{
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.CHANGE_WIFI_STATE,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                    };
        }
    }

    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;
    private static final String TAG = "RockPaperScissors";
    private static final Strategy STRATEGY = Strategy.P2P_STAR;
    private List<String> listOfResults = new ArrayList<>();
    private List<Integer> displayedSignClass = new ArrayList<>();
    TextView textView;
    ImageView imageView;


    public static final String SERVICE_ID = "120001";

    private ConnectionsClient connectionsClient;

    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                private final SimpleArrayMap<Long, Payload> incomingFilePayloads = new SimpleArrayMap<>();
                private final SimpleArrayMap<Long, Payload> completedFilePayloads = new SimpleArrayMap<>();
                private final SimpleArrayMap<Long, String> filePayloadFilenames = new SimpleArrayMap<>();

                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    if (payload.getType() == Payload.Type.BYTES) {
                        String payloadFilenameMessage = new String(payload.asBytes(), StandardCharsets.UTF_8);
                        if(payloadFilenameMessage.substring(0,7).equals("Battery")){
                            getBatteryPercentage(endpointId);
                        }
                        else{
                            long payloadId = addPayloadFilename(payloadFilenameMessage);
                        }

                        //processFilePayload(payloadId,endpointId);

                    } else if (payload.getType() == Payload.Type.FILE) {
                        // Add this to our tracking map, so that we can retrieve the payload later.
                        incomingFilePayloads.put(payload.getId(), payload);

                    }
                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                    if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                        long payloadId = update.getPayloadId();
                        Payload payload = incomingFilePayloads.remove(payloadId);
                        completedFilePayloads.put(payloadId, payload);
                        Log.i(TAG,"File received is "+filePayloadFilenames.get(payloadId));
                        if (payload != null) {
                            if (payload.getType() == Payload.Type.FILE) {
                                processFilePayload(payloadId,endpointId);
                            }
                        }
                    }
                }

                private long addPayloadFilename(String payloadFilenameMessage) {

                    String[] parts = payloadFilenameMessage.split(":");
                    long payloadId = Long.parseLong(parts[0]);
                    String filename = parts[1];

                    filePayloadFilenames.put(payloadId, filename);
                    return payloadId;
                }

                private void processFilePayload(long payloadId, String endPointId)  {
                    // BYTES and FILE could be received in any order, so we call when either the BYTES or the FILE
                    // payload is completely received. The file payload is considered complete only when both have
                    // been received.
                    Payload filePayload = completedFilePayloads.get(payloadId);
                    String filename = filePayloadFilenames.get(payloadId);
                    if (filePayload != null) {
                        ParcelFileDescriptor img = filePayload.asFile().asParcelFileDescriptor();
                        FileDescriptor fd = img.getFileDescriptor();
                        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fd);

                        Mat image = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC4); // rgb
                        Utils.bitmapToMat(bitmap, image);
                        listOfResults.clear();

                        image = signRecognition.detectionImage(image,listOfResults,displayedSignClass);
                        if(!listOfResults.get(0).isEmpty()){
                            String resultData = "Result:"+listOfResults.get(0);
                            Log.i(TAG,"result data being sent from worker:"+resultData);
                            Payload resultPayload = Payload.fromBytes(resultData.getBytes(UTF_8));
                            connectionsClient.sendPayload(endPointId,resultPayload);
                        }
                        else{
                            Log.i(TAG,"result is empty");
                        }
                        Log.i(TAG, "Process payload file checker method for "+payloadId+","+filename);
                    } else {
                        Log.i(TAG, "Null file payload"+ payloadId+","+filename);
                    }
                }

                private void copyStream(InputStream in, OutputStream out) throws IOException {
                    try {
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                        out.flush();
                    } finally {
                        in.close();
                        out.close();
                    }
                }
            };

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    Log.i(TAG, "onEndpointFound: endpoint found, connecting");
                    textView.setText("Send Connection request");
                    DeviceEndPointID = endpointId;
                    connectionsClient.requestConnection("Pixel 4a", endpointId, connectionLifecycleCallback);
                }

                @Override
                public void onEndpointLost(String endpointId) {
                }
            };

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.i(TAG, "onConnectionInitiated: accepting connection");
                    connectionsClient.acceptConnection(endpointId, payloadCallback);

                    //opponentName = connectionInfo.getEndpointName();
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    if (result.getStatus().isSuccess()) {
                        Log.i(TAG, "onConnectionResult: connection successful");

                        connectionsClient.stopDiscovery();
                        connectionsClient.stopAdvertising();

                        int cores = getCores();
                        int frequency = getCpuClockSpeed();
                        long maxFrequency = getCpuFreq(cores);
                        Log.i(TAG,"Number of cores are: "+ cores +"frequency is:"+frequency+ "Max:"+maxFrequency);
                        String phoneData = "PhoneData:"+cores+":"+maxFrequency;
                        Payload phonePayload = Payload.fromBytes(phoneData.getBytes(UTF_8));
                        connectionsClient.sendPayload(endpointId,phonePayload);
                        textView.setText("Connection Successful");
                        //showImageChooser(endpointId);
                        // connectionsClient.sendPayload(
                        //         endpointId, Payload.fromBytes(transfer.getBytes(UTF_8)));

                    } else {
                        Log.i(TAG, "onConnectionResult: connection failed");
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endPointId) {
                    Log.i(TAG, "onDisconnected: disconnected from the opponent");
                }
            };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        connectionsClient = Nearby.getConnectionsClient(this);
        Button findDevice = (Button) findViewById(R.id.button);
        textView = findViewById(R.id.textView);
        imageView = findViewById(R.id.displayImage);
        findDevice.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startDiscovery();
            }
        });

        // load model
        try {
            signRecognition = new SignRecognition(getAssets());
            Log.d(TAG, "Model is successfully loaded");
        } catch (IOException e) {
            Log.d(TAG, "Getting some error: " + e.getMessage());
        }
    }

    static{
        if(OpenCVLoader.initDebug()){

            Log.d("check","OpenCv configured successfully");

        } else{

            Log.d("check","OpenCv doesn’t configured successfully");
        }

    }


    @Override
    protected void onStart() {
        super.onStart();

        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
            }
        }
    }

    @Override
    protected void onStop() {
        connectionsClient.stopAllEndpoints();

        super.onStop();
    }

    private static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @CallSuper
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_CODE_REQUIRED_PERMISSIONS) {
            return;
        }

        int i = 0;
        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                Log.i(TAG, "Failed to request the permission " + permissions[i]);
                Toast.makeText(this, "Missing Permission", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            i++;
        }
        recreate();
    }

    private void startDiscovery() {
        // Note: Discovery may fail. To keep this demo simple, we don't handle failures.
        connectionsClient.startDiscovery(
                SERVICE_ID, endpointDiscoveryCallback,
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build());
    }

    private void startAdvertising() {
        // Note: Advertising may fail. To keep this demo simple, we don't handle failures.
        connectionsClient.startAdvertising(
                "Main Device", SERVICE_ID, connectionLifecycleCallback,
                new AdvertisingOptions.Builder().setStrategy(STRATEGY).setConnectionType(ConnectionType.DISRUPTIVE).build());
    }

    public  void getBatteryPercentage(String endpointId){
        int b;
        if (Build.VERSION.SDK_INT >= 21) {

            BatteryManager bm = (BatteryManager) this.getSystemService(BATTERY_SERVICE);
            b =  bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } else {

            IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = this.registerReceiver(null, iFilter);

            int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
            int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;

            double batteryPct = level / (double) scale;

            b =  (int) (batteryPct * 100);
        }
        String battery = "Battery:"+Integer.toString(b);
        Payload batteryPayload = Payload.fromBytes(battery.getBytes(UTF_8));
        connectionsClient.sendPayload(endpointId,batteryPayload);
    }

    private int getNumberOfCores() {
        if(Build.VERSION.SDK_INT >= 17) {
            return Runtime.getRuntime().availableProcessors();
        }
        else {
            return getNumCoresOldPhones();
        }
    }

    /**
     * Gets the number of cores available in this device, across all processors.
     * Requires: Ability to peruse the filesystem at "/sys/devices/system/cpu"
     * @return The number of cores, or 1 if failed to get result
     */
    private int getNumCoresOldPhones() {
        //Private Class to display only CPU devices in the directory listing
        class CpuFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                //Check if filename is "cpu", followed by a single digit number
                if(Pattern.matches("cpu[0-9]+", pathname.getName())) {
                    return true;
                }
                return false;
            }
        }

        try {
            //Get directory containing CPU info
            File dir = new File("/sys/devices/system/cpu/");
            //Filter to only list the devices we care about
            File[] files = dir.listFiles(new CpuFilter());
            //Return the number of cores (virtual CPU devices)
            return files.length;
        } catch(Exception e) {
            //Default to return 1 core
            return 1;
        }
    }

    public int getCpuClockSpeed(){
        int clockSpeedHz = (int) Os.sysconf(OsConstants._SC_CLK_TCK);
        return clockSpeedHz;
    }

    public int getCores(){
        int numCores = (int) Os.sysconf(OsConstants._SC_NPROCESSORS_CONF);
        return numCores;
    }

    private long getCpuFreq(int cpuCores) {
        long maxFreq = -1;

        for (int i = 0; i < cpuCores; i++) {
            try {
                String filepath = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq";
                RandomAccessFile raf = new RandomAccessFile(filepath, "r");
                String line = raf.readLine();

                if (line != null) {
                    long freq = Long.parseLong(line);
                    if (freq > maxFreq) {
                        maxFreq = freq;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, String.format("Could not retrieve CPU frequency: \n%s", e.getMessage()));
            }
        }

        return maxFreq;
    }
}