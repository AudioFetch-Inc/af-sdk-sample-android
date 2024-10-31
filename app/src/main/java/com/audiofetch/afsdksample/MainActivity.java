package com.audiofetch.afsdksample;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

// af_disco imports
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodCall;
import androidx.annotation.NonNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// af_audio imports
import com.audiofetch.afaudiolib.bll.app.AFAudioService;
import com.audiofetch.afaudiolib.api.AfApi;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;
import android.content.Intent;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;

// UI Imports
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import java.util.List;
import android.view.LayoutInflater;
import android.widget.Button;
import android.view.View.OnClickListener;
import android.view.View;
import android.widget.TextView;


// Class to track discovered channels
class AFChannel {
    String chNum;
    String chName;
    String ips[];

    public AFChannel(String inChNum, String inChName, String inIps []) {
        chNum = inChNum;
        chName = inChName;
        ips = inIps;
    }
    
}


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // AFDisco
    public FlutterEngine flutterEngine;
    public MethodChannel afDiscoMethodChannel;

    // AFAudio
    protected AFAudioService mAFAudioSvc;
    protected boolean mIsAFAudioSvcBound = false;
    protected ServiceConnection mAFAudioSvcConn;
    protected Handler mUiHandler = new Handler();

    // Discovered channels, map of UI channel number to the AFChannel class above
    HashMap<String,AFChannel> channels;

    // UI
    ListView channelList;
    Button playPauseButton;
    ArrayAdapter<String> listAdapter;
    ArrayList<String> channelNames;
    HashMap<Integer,String> posToUiChNum;
    Boolean isPlaying = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI Get refererence to our list view
        channelList = findViewById(R.id.list);
        channelNames = new ArrayList<String>();
        listAdapter = new ArrayAdapter<String>(this, R.layout.list_item, channelNames);
        channelList.setAdapter(listAdapter);

        // Top Play pause button click handling
        playPauseButton = findViewById(R.id.playPause);
        playPauseButton.setOnClickListener( new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPlaying) {
                    isPlaying = false;
                    playPauseButton.setText("Play");
                    AFAudioService.api().muteAudio();
                }
                else {
                    isPlaying = true;
                    playPauseButton.setText("Pause");  
                    AFAudioService.api().unmuteAudio();
                }
            }
        });

        // Instantiate a FlutterEngine for communication to AfDisco and start it
        flutterEngine = new FlutterEngine(this);
        flutterEngine.getDartExecutor().executeDartEntrypoint(
                DartExecutor.DartEntrypoint.createDefault()
        );
        FlutterEngineCache.getInstance().put("af_disco", flutterEngine);

        // Create a method channel for communication with AfDisco
        afDiscoMethodChannel = new MethodChannel(flutterEngine.getDartExecutor(), "com.audiofetch/afDisco");
        afDiscoMethodChannel.setMethodCallHandler(
                new MethodChannel.MethodCallHandler() {
                    @Override
                    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
                        String msg = call.argument("msg");
                        String params = call.argument("params");

                        // Handle messages from AfDisco
                        if (call.method.equals("FromClientToHost")) {
                            Log.i(TAG, String.format("From af_disco to android: %s, %s", msg, params) );
                            if (msg.equals("afDiscoUp") ) {
                                Log.i(TAG, "MainActivity af_disco plugin says running");  
                                sendToAfDisco("startDisco","");                          
                            }
                            else if (msg.equals("newDisco")) {
                                // This is sent on every new box discovery incrementally
                                // These calls happen immediately and provide raw information
                                // about each AudioFetch box.
                            }
                            else if (msg.equals("discoResults")) {
                                // This is the completed results after the discovery period
                                // ie, 6 seconds and contains the completed list of all boxes discovered.
                                Log.i("discoResults", params);

                                // Convert JSON into our iv of channels
                                try {
                                    JSONObject allChannels = new JSONObject(params);

                                    // empty out our channels and our list translation
                                    channels = new HashMap<String,AFChannel>();
                                    posToUiChNum = new HashMap<Integer,String>();

                                    // Iterate over all the channels found
                                    Iterator<String> keys = allChannels.keys();
                                    int len = 0;
                                    while(keys.hasNext()) {
                                        len += 1;
                                        keys.next();
                                    }

                                    // Form up a list of channels for display, as well as a map to translate
                                    // that list index into channel number.
                                    channelNames.clear();
                                    int pos = 0;
                                    keys = allChannels.keys();
                                    while(keys.hasNext()) {
                                        String key = keys.next();
                                        JSONObject channel = (JSONObject) allChannels.get(key);
                                        if ( channel instanceof JSONObject) {
                                            JSONArray ipArr = (JSONArray) channel.get("ips");
                                            String[] ips = new String[ipArr.length()];
                                            
                                            int i = 0;
                                            while (i < ipArr.length()) {
                                                ips[i] = ipArr.optString(i);
                                                i += 1;
                                            }

                                            AFChannel ch = new AFChannel(channel.getString("chNum"), channel.getString("chName"), ips);
                                            channels.put(key, ch);
                                            channelNames.add("Channel: " + key);
                                            posToUiChNum.put(pos, key);

                                            pos += 1;
                                        }
                                    }

                                } catch (JSONException ex) {
                                    Log.i(TAG, "Json parse fail:", ex);
                                }

                                // Force redraw of the newly formed channel list.
                                listAdapter.notifyDataSetChanged();
                            }

                        } else {
                            result.notImplemented();
                        }
                    }
                });

        // Start the AFAudio Service
        startAFAudioService();
    }

    // Send a message to the AfDisco library.
    public void sendToAfDisco(String msg, String params) {
        try {
            JSONObject json = new JSONObject();
            json.put("msg", msg);
            json.put("params", params);
            afDiscoMethodChannel.invokeMethod("fromHostToClient", json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // Start the audiofetch audio service. Typically called once at application startup.
    protected MainActivity startAFAudioService() {
        if (mAFAudioSvc == null) {
            final Intent serviceIntent = new Intent(this, AFAudioService.class);
            // Start audio service in as a foreground service
            Context context = getApplicationContext();
            context.startForegroundService(serviceIntent);
            bindService(new Intent(this, AFAudioService.class), getAFAudioServiceConnection(), 0);
        }
        return this;
    }

    // Connect to the started AF audio service.
    protected ServiceConnection getAFAudioServiceConnection() {
        if (mAFAudioSvcConn == null) {
            mAFAudioSvcConn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {
                    if (service instanceof AFAudioService.AFAudioBinder) {
                        //LG.Debug(TAG, "AFAudioService connected");
                        AFAudioService.AFAudioBinder binder = (AFAudioService.AFAudioBinder) service;
                        mAFAudioSvc = binder.getService();

                        if (null != mAFAudioSvc) {
                            Context ctx = getApplicationContext();
                            // app context must be set before initing audio subsystem
                            AFAudioService.api().setAppContext( getApplicationContext() );
                            AFAudioService.api().initAudioSubsystem();

                            mIsAFAudioSvcBound = true;
                            mAFAudioSvc.hideNotifcations();

                            mUiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    startAFAudioServiceAudio();
                                }
                            });

                            // Subscribe to API messages.
                            doSubscriptions();
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    mIsAFAudioSvcBound = false;
                    mAFAudioSvcConn = null;
                    mAFAudioSvc = null;
                }
            };
        }
        return mAFAudioSvcConn;
    }

    // Subclasses can override this to subscribe to api messages
    public void doSubscriptions() {
        // subsclasses override
    }

    // Tell the AF Audio service to start playing audio.
    protected boolean startAFAudioServiceAudio() {
        boolean started = false;
        if (mAFAudioSvc != null) {
            started = true;
            mUiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    AFAudioService.api().startAudio();
                }
            }, 500);
        }
        return started;
    }

    // Tell the AF Audio service to stop playing audio.
    protected void stopAFAudioService() {
        if (mAFAudioSvc != null) {
            mAFAudioSvc.hideNotifcations();
            if (mIsAFAudioSvcBound && null != mAFAudioSvcConn) {
                unbindService(mAFAudioSvcConn);
            }
            mAFAudioSvc.quit();
        }
    }

    // Click handler for the list of channels
    public void onClick(View v) {
        // Get which list index was clicked
        int pos = listAdapter.getPosition(( (TextView)v).getText().toString());

        // Translate that index to the ui channel number
        String uiCh = posToUiChNum.get(pos);

        // Now, get the channel for that ui channel number
        AFChannel ch = channels.get(uiCh);

        // Finally, tell the audio service to play that channel
        AFAudioService.api().setApbAndChannel(ch.ips[0],Integer.parseInt(ch.chNum),ch.chName);

        isPlaying = true;
        playPauseButton.setText("Pause");  
    }  
}


