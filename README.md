# AudioFetch Android SDK Sample

## Overview

This is an example app for how to integrate and use the v3 AudioFetch SDK. The SDK
now takes the form of two libraries: audio and discovery.

The discovery library, afDisco, is responsible for discovering AudioFetch boxes on the local network and returning a list of available channels after the discovery process is complete.

The audio library, afAudio, is responsible for playing the audio from the discovered boxes.

This sample assumes that the afDisco distribution is a peer of this directory:

    android-audiofetch-sdk-sample-v3
    af_disco_android

You can, of course, organize in any way you wish, but the relative paths in the gradle build assume this structure for this sample app. The afAudio library takes the form of an aar that is included in app/libs here for convienence.


## Building and Running

With the two libraries in the above directory structure, open this folder in Android Studio, then build and run. This sample app will show a list of channels and allow you to play and pause.

This app is intended to be a minimal example app to show the integration of the AudioFetch SDK.


## Integrating afAudio into your App

Link with the aar as this sample app does in its app level build.gradle.kts:

    // af_audio integration
    implementation("io.reactivex.rxjava2:rxjava:2.2.0")
    implementation("io.reactivex.rxjava2:rxandroid:2.0.1")
    implementation("com.jakewharton.rxrelay2:rxrelay:2.0.0")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation(files("libs/afaudiolib.aar"))

The afAudio library uses RxJava for communication between it and the host app.


## Integrating afDisco into your App

The afDisco library is re-written in v3 for improved performance and stability, and is implemented in Dart and uses a Flutter method channel for communication between the afDisco library and the host app. Note that while Flutter is used, it is only used for the method channel, not for any user interface elements, etc.

The relative path to the afDisco library goes in the settings.gradle.kts as in this sample app, along with the overall flutter library:

    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories {
            google()
            mavenCentral()
    
            maven(url = "https://storage.googleapis.com/download.flutter.io")  // <--- Add this line
            maven(url = "../af_disco_android")                                 // <--- Add this line
        }
    }

Then, in the app level gradle file build.gradle.kts:

    // af_disco integration
    debugImplementation("com.audiofetch.af_disco_module:flutter_debug:1.0")
    releaseImplementation("com.audiofetch.af_disco_module:flutter_release:1.0")
    add("profileImplementation", "com.audiofetch.af_disco_module:flutter_profile:1.0")


## Permissions

Android requires a number of permissions for local network access, please see the manifest file in this example.


## afDisco Integration

The MainActivity.java in this sample app shows the afDisco integration in detail, highlights of it are shown here.

Imports:

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

In onCreate(), instantiate the Flutter Engine and create the method channel for communication:

    // Instantiate a FlutterEngine for communication to AfDisco and start it
    flutterEngine = new FlutterEngine(this);
    flutterEngine.getDartExecutor().executeDartEntrypoint(
            DartExecutor.DartEntrypoint.createDefault()
    );
    FlutterEngineCache.getInstance().put("af_disco", flutterEngine);
    
    // Create a method channel for communication with AfDisco
    afDiscoMethodChannel = new MethodChannel(flutterEngine.getDartExecutor(), "com.audiofetch/afDisco");
    afDiscoMethodChannel.setMethodCallHandler( ... )

The method call handler then receives messages from the afDisco module, details below in the API section.


## afAudio Integration

Imports:

`// af_audio imports`
`import com.audiofetch.afaudiolib.bll.app.AFAudioService;`
`import com.audiofetch.afaudiolib.api.AfApi;`
`import android.content.ServiceConnection;`
`import android.content.ComponentName;`
`import android.os.IBinder;`
`import android.content.Intent;`
`import android.content.Context;`
`import android.os.Handler;`

In the MainActivity onCreate(), start the audio service as a foreground service. Android no longer allows long running background services and if audio is started as a background services, the OS will stop it after about 5-10 minutes.

This code starts the service, creates a connection to it, and finally uses the exposed api to init the audio subsystem, and then start the audio. Note that while audio is started here, silence is output until an AudioFetch box is connected to via the api.

For this SDK, host apps do this on startup and leave the audio service running for the duration of the app's lifecycle.

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



## SDK API

For discovery, messages over the method channel take the form of a message with parameters. the "newDisco" and "discoResults" messages have json as their parameters.

    -> afDiscoUp
    <- startDisco
    -> newDisco
    -> discoResults

The newDisco message is sent incrementally during discovery as new boxes (APB's) are found. These have raw information about boxes, but can be ignored. The discoResults message contans a json map of all channels found during the discovery period which lasts 6 seconds. The json is organized by "UI Channel Number", which is the channel number displayed in the AudioFetch listening app, where boxes configured as "Box A" have Channels starting at 1.

The sample app parses this json map of channels and translates that into a string list for display as a scrolling list in the app. It also keeps track of the APB ip address and raw APB channel for that UI Channel so that it may call setApbAndChannel() in the audio API (below).

For audio:

    AFAudioService.api().initAudioSubsystem();
    AFAudioService.api().startAudio();
    AFAudioService.api().setApbAndChannel( boxIP, boxChannel );
    AFAudioService.api().muteAudio();
    AFAudioService.api().unmuteAudio();

In the sample app, tapping "Play" or "Pause" mutes or umutes the audio. While the UI says "Pause", the real-time audio stream from the box of course keeps going, so in implementation, the SDK is muting the audio from that stream.





