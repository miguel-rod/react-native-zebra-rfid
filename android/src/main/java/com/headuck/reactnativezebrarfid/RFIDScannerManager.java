package com.headuck.reactnativezebrarfid;

import android.util.Log;
import org.json.JSONObject;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.LifecycleEventListener;

public class RFIDScannerManager extends ReactContextBaseJavaModule implements LifecycleEventListener {

    public final ReactApplicationContext context;

    private RFIDScannerThread scannerThread = null;

    public RFIDScannerManager(ReactApplicationContext reactContext) {
        super(reactContext);
        //this.locateTag = null;
        this.context = reactContext;
        this.context.addLifecycleEventListener(this);

        this.scannerThread = new RFIDScannerThread(this.context) {

            @Override
            public void dispatchEvent(String name, WritableMap data) {
                RFIDScannerManager.this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(name, data);
            }

            @Override
            public void dispatchEvent(String name, String data) {
                RFIDScannerManager.this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(name, data);
            }

            @Override
            public void dispatchEvent(String name, JSONObject data) {
                RFIDScannerManager.this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(name, data);
            }

            @Override
            public void dispatchEvent(String name, WritableArray data) {
                RFIDScannerManager.this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(name, data);
            }

        };
        scannerThread.start();

        Log.v("RFID", "RFIDScannerManager created");

    }


    @Override
    public String getName() {
        return "RFIDScannerManager";
    }

    @Override
    public void onHostResume() {
        if (this.scannerThread != null) {
            this.scannerThread.onHostResume();
        }
    }

    @Override
    public void onHostPause() {
        if (this.scannerThread != null) {
            this.scannerThread.onHostPause();
        }
    }

    @Override
    public void onHostDestroy() {
        if (this.scannerThread != null) {
            this.scannerThread.onHostDestroy();
        }
    }

    @Override
    public void onCatalystInstanceDestroy() {
        if (this.scannerThread != null) {
            this.scannerThread.onCatalystInstanceDestroy();
        }
    }

    @ReactMethod
    public void init() {
        if (this.scannerThread != null) {
            this.scannerThread.init(context);
        }
    }

    @ReactMethod
    public void reconnect() {
        if (this.scannerThread != null) {
            this.scannerThread.reconnect();
        }
    }
    @ReactMethod
    public void locate(String tag){
        if (this.scannerThread != null) {
            this.scannerThread.locate(tag);
        }
    }

    @ReactMethod
    public void read(ReadableMap config) {
        if (this.scannerThread != null) {
            this.scannerThread.read(config);
        }
    }

    @ReactMethod
    public void cancel() {
        if (this.scannerThread != null) {
            this.scannerThread.cancel();
        }
    }

    @ReactMethod
    public void shutdown() {
        if (this.scannerThread != null) {
            this.scannerThread.shutdown();
        }
    }

    @ReactMethod
    public void settingAntennas(int powerLevel) {
        if (this.scannerThread != null) {
            this.scannerThread.settingAntennas(powerLevel);
        }
    }

    @ReactMethod
    public void settingBeeper(String beeperVolume) {
        if (this.scannerThread != null) {
            this.scannerThread.settingBeeper(beeperVolume);
        }
    }

    @ReactMethod
    public void gettingBeeper() {
        if (this.scannerThread != null) {
            this.scannerThread.gettingBeeper();
        }
    }

}
