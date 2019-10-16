package com.headuck.reactnativezebrarfid;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.util.ArrayList;
import java.lang.NullPointerException;
import android.util.JsonReader;

import com.zebra.rfid.api3.*;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import static android.os.AsyncTask.SERIAL_EXECUTOR;

public abstract class RFIDScannerThread extends Thread implements RfidEventsListener {

    private ReactApplicationContext context;

    private Readers readers = null;
    private ArrayList<ReaderDevice> deviceList = null;
    private ReaderDevice rfidReaderDevice = null;
    boolean tempDisconnected = false;
    private Boolean reading = false;
    private ReadableMap config = null;

    public RFIDScannerThread(ReactApplicationContext context) {
        this.context = context;
    }

    public void run() {

    }

    private void connect() {
        String err = null;
        if (this.rfidReaderDevice != null) {
            if (rfidReaderDevice.getRFIDReader().isConnected()) return;
            disconnect();
        }
        try {

            Log.v("RFID", "initScanner");

            ArrayList<ReaderDevice> availableRFIDReaderList = null;
            try {
                availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
                Log.v("RFID", "Available number of reader : " + availableRFIDReaderList.size());
                deviceList = availableRFIDReaderList;

            } catch (InvalidUsageException e) {
                Log.e("RFID", "Init scanner error - invalid message: " + e.getMessage());
            } catch (NullPointerException ex) {
                Log.e("RFID", "Blue tooth not support on device");
            }

            int listSize = (availableRFIDReaderList == null) ? 0 : availableRFIDReaderList.size();
            Log.v("RFID", "Available number of reader : " + listSize);

            if (listSize > 0) {
                ReaderDevice readerDevice = availableRFIDReaderList.get(0);
                RFIDReader rfidReader = readerDevice.getRFIDReader();
                // Connect to RFID reader
                Log.v("RFID", "Available rfid reader : " + rfidReader);
                if (rfidReader != null) {
                    while (true) {
                        try {
                            rfidReader.connect();
                            rfidReader.Config.getDeviceStatus(true, false, false);
                            rfidReader.Events.addEventsListener(this);
                            // Subscribe required status notification
                            rfidReader.Events.setInventoryStartEvent(true);
                            rfidReader.Events.setInventoryStopEvent(true);
                            // enables tag read notification
                            rfidReader.Events.setTagReadEvent(true);
                            rfidReader.Events.setReaderDisconnectEvent(true);
                            rfidReader.Events.setBatteryEvent(true);
                            rfidReader.Events.setBatchModeEvent(true);
                            rfidReader.Events.setHandheldEvent(true);
                            // Set trigger mode
                            setTriggerImmediate(rfidReader);
                            break;
                        } catch (OperationFailureException ex) {
                            if (ex.getResults() == RFIDResults.RFID_READER_REGION_NOT_CONFIGURED) {
                                // Get and Set regulatory configuration settings
                                try {
                                    RegulatoryConfig regulatoryConfig = rfidReader.Config.getRegulatoryConfig();
                                    SupportedRegions regions = rfidReader.ReaderCapabilities.SupportedRegions;
                                    int len = regions.length();
                                    boolean regionSet = false;
                                    for (int i = 0; i < len; i++) {
                                        RegionInfo regionInfo = regions.getRegionInfo(i);
                                        if ("USA".equals(regionInfo.getRegionCode())) {
                                            regulatoryConfig.setRegion(regionInfo.getRegionCode());
                                            rfidReader.Config.setRegulatoryConfig(regulatoryConfig);
                                            Log.i("RFID", "Region set to " + regionInfo.getName());
                                            regionSet = true;
                                            break;
                                        }
                                    }
                                    if (!regionSet) {
                                        err = "Region not found";
                                        break;
                                    }
                                } catch (OperationFailureException ex1) {
                                    err = "Error setting RFID region: " + ex1.getMessage();
                                    break;
                                }
                            } else if (ex.getResults() == RFIDResults.RFID_CONNECTION_PASSWORD_ERROR) {
                                // Password error
                                err = "Password error";
                                break;
                            } else if (ex.getResults() == RFIDResults.RFID_BATCHMODE_IN_PROGRESS) {
                                // handle batch mode related stuff
                                err = "Batch mode in progress";
                                break;
                            } else {
                                err = ex.getResults().toString();
                                break;
                            }
                        } catch (InvalidUsageException e1) {
                            Log.e("RFID", "InvalidUsageException: " + e1.getMessage() + " " + e1.getInfo());
                            err = "Invalid usage " + e1.getMessage();
                            break;
                        }
                    }
                } else {
                    err = "Cannot get rfid reader";
                }
                if (err == null) {
                    // Connect success
                    rfidReaderDevice = readerDevice;
                    tempDisconnected = false;
                    WritableMap event = Arguments.createMap();
                    event.putString("RFIDStatusEvent", "opened");
                    this.dispatchEvent("RFIDStatusEvent", event);
                    Log.i("RFID", "Connected to " + rfidReaderDevice.getName());
                    return;
                }
            } else {
                err = "No connected device";
            }
        } catch (InvalidUsageException e) {
            err = "connect: invalid usage error: " + e.getMessage();
        }
        if (err != null) {
            Log.e("RFID", err);
        }
    }

    /**
     * Set trigger mode
     */
    private void setTriggerImmediate(RFIDReader reader) throws InvalidUsageException, OperationFailureException {
        TriggerInfo triggerInfo = new TriggerInfo();
        reader.Events.addEventsListener(this);
        // Start trigger: set to immediate mode
        triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE);
        // Stop trigger: set to immediate mode
        triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE);
        reader.Config.setStartTrigger(triggerInfo.StartTrigger);
        reader.Config.setStopTrigger(triggerInfo.StopTrigger);
    }

    private void disconnect() {

        if (this.rfidReaderDevice != null){
            RFIDReader rfidReader = rfidReaderDevice.getRFIDReader();
            String err = null;
            Log.v("RFID", "disconnected");
            if (!rfidReader.isConnected()) {
                Log.i("RFID", "disconnect: already disconnected");
                // already disconnected
            } else {
                try {
                    rfidReader.disconnect();
                } catch (InvalidUsageException e) {
                    err = "disconnect: invalid usage error: " + e.getMessage();
                } catch (OperationFailureException ex) {
                    err = "disconnect: " + ex.getResults().toString();
                }
            }
            try {
                if (rfidReader.Events != null) {
                    rfidReader.Events.removeEventsListener(this);
                }
            } catch (InvalidUsageException e) {
                err = "disconnect: invalid usage error when removing events: " + e.getMessage();
            } catch (OperationFailureException ex) {
                err = "disconnect: error removing events: " + ex.getResults().toString();
            }
            if (err != null) {
                Log.e("RFID", err);
            }
            // Ignore error and send feedback
            WritableMap event = Arguments.createMap();
            event.putString("RFIDStatusEvent", "closed");
            this.dispatchEvent("RFIDStatusEvent", event);
            rfidReaderDevice = null;
            tempDisconnected = false;
        } else {
            Log.w("RFID", "disconnect: no device was connected");
        }

    }

    public abstract void dispatchEvent(String name, WritableMap data);
    public abstract void dispatchEvent(String name, String data);
    public abstract void dispatchEvent(String name, WritableArray data);

    public void onHostResume() {
        if (readers != null){
            this.connect();
        } else {
             Log.e("RFID", "Can't resume - reader is null");
        }
    }

    public void onHostPause() {
        if (this.reading){
            this.cancel();
        }
        this.disconnect();
    }

    public void onHostDestroy() {
        if (this.reading){
            this.cancel();
        }
        shutdown();
    }

    public void onCatalystInstanceDestroy() {
        if (this.reading){
            this.cancel();
        }
        shutdown();
    }

    private class connectAsync extends AsyncTask<Object, Void, ReaderDevice> {
        protected ReaderDevice doInBackground(Object... objects) {
            Log.v("RFID", "init");
            readers = new Readers(context, ENUM_TRANSPORT.BLUETOOTH);
            try {
                ArrayList<ReaderDevice> availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
                Log.v("RFID", "Available number of reader : " + availableRFIDReaderList.size());
                deviceList = availableRFIDReaderList;

                Log.v("RFID", "Scanner thread initialized");
            } catch (InvalidUsageException e) {
                Log.e("RFID", "Init scanner error - invalid message: " + e.getMessage());
            } catch (NullPointerException ex) {
                Log.e("RFID", "Blue tooth not support on device");
            }
            reading = false;

            String err = null;
            if (rfidReaderDevice != null) {
                if (rfidReaderDevice.getRFIDReader().isConnected())
                disconnect();
            }
            try {

                Log.v("RFID", "initScanner");

                ArrayList<ReaderDevice> availableRFIDReaderList = null;
                try {
                    availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
                    Log.v("RFID", "Available number of reader : " + availableRFIDReaderList.size());
                    deviceList = availableRFIDReaderList;

                } catch (InvalidUsageException e) {
                    Log.e("RFID", "Init scanner error - invalid message: " + e.getMessage());
                } catch (NullPointerException ex) {
                    Log.e("RFID", "Blue tooth not support on device");
                }

                int listSize = (availableRFIDReaderList == null) ? 0 : availableRFIDReaderList.size();
                Log.v("RFID", "Available number of reader : " + listSize);

                if (listSize > 0) {
                    ReaderDevice readerDevice = availableRFIDReaderList.get(0);
                    RFIDReader rfidReader = readerDevice.getRFIDReader();
                    // Connect to RFID reader
                    Log.v("RFID", "Available rfid reader : " + rfidReader);
                    if (rfidReader != null) {
                        while (true) {
                            try {
                                rfidReader.connect();
                                rfidReader.Config.getDeviceStatus(true, false, false);
                                //rfidReader.Events.addEventsListener(this);
                                // Subscribe required status notification
                                rfidReader.Events.setInventoryStartEvent(true);
                                rfidReader.Events.setInventoryStopEvent(true);
                                // enables tag read notification
                                rfidReader.Events.setTagReadEvent(true);
                                rfidReader.Events.setReaderDisconnectEvent(true);
                                rfidReader.Events.setBatteryEvent(true);
                                rfidReader.Events.setBatchModeEvent(true);
                                rfidReader.Events.setHandheldEvent(true);
                                // Set trigger mode
                                setTriggerImmediate(rfidReader);
                                break;
                            } catch (OperationFailureException ex) {
                                if (ex.getResults() == RFIDResults.RFID_READER_REGION_NOT_CONFIGURED) {
                                    // Get and Set regulatory configuration settings
                                    try {
                                        RegulatoryConfig regulatoryConfig = rfidReader.Config.getRegulatoryConfig();
                                        SupportedRegions regions = rfidReader.ReaderCapabilities.SupportedRegions;
                                        int len = regions.length();
                                        boolean regionSet = false;
                                        for (int i = 0; i < len; i++) {
                                            RegionInfo regionInfo = regions.getRegionInfo(i);
                                            if ("USA".equals(regionInfo.getRegionCode())) {
                                                regulatoryConfig.setRegion(regionInfo.getRegionCode());
                                                rfidReader.Config.setRegulatoryConfig(regulatoryConfig);
                                                Log.i("RFID", "Region set to " + regionInfo.getName());
                                                regionSet = true;
                                                break;
                                            }
                                        }
                                        if (!regionSet) {
                                            err = "Region not found";
                                            break;
                                        }
                                    } catch (OperationFailureException ex1) {
                                        err = "Error setting RFID region: " + ex1.getMessage();
                                        break;
                                    }
                                } else if (ex.getResults() == RFIDResults.RFID_CONNECTION_PASSWORD_ERROR) {
                                    // Password error
                                    err = "Password error";
                                    break;
                                } else if (ex.getResults() == RFIDResults.RFID_BATCHMODE_IN_PROGRESS) {
                                    // handle batch mode related stuff
                                    err = "Batch mode in progress";
                                    break;
                                } else {
                                    err = ex.getResults().toString();
                                    break;
                                }
                            } catch (InvalidUsageException e1) {
                                Log.e("RFID", "InvalidUsageException: " + e1.getMessage() + " " + e1.getInfo());
                                err = "Invalid usage " + e1.getMessage();
                                break;
                            }
                        }
                    } else {
                        err = "Cannot get rfid reader";
                    }
                    if (err == null) {
                        // Connect success
                        return readerDevice;

                    }
                } else {
                    err = "No connected device";
                }
            } catch (InvalidUsageException e) {
                err = "connect: invalid usage error: " + e.getMessage();
            }
            if (err != null) {
                Log.e("RFID", err);
            }
            return null;
        }

        protected void onProgressUpdate(Integer... progress) {
            // optionally report progress
        }

        protected void onPostExecute(ReaderDevice result) {
            // do something on the UI thread
            rfidReaderDevice = result;
            if (rfidReaderDevice != null) {
                tempDisconnected = false;
                WritableMap event = Arguments.createMap();
                event.putString("RFIDStatusEvent", "opened");
                dispatchEvent("RFIDStatusEvent", event);
                Log.i("RFID", "Connected to " + rfidReaderDevice.getName());
            }
        }
    }

    public void init(Context context) {
        // Register receiver

        connectAsync connAsync = new connectAsync();
        connAsync.executeOnExecutor(SERIAL_EXECUTOR,this.rfidReaderDevice, this.readers);
        // this.connect();
    }

    private class shutdownAsync extends AsyncTask<Object, Void, String>{
        protected String doInBackground(Object... objects) {
            if (rfidReaderDevice != null) {
                disconnect();
            }
            // Unregister receiver
            if (readers != null) {
                readers.Dispose();
                readers = null;
            }
            deviceList = null;
            Log.v("RFID","shutdown " + rfidReaderDevice);
            return "";
        }

        protected void onProgressUpdate(Integer... progress) {
            // optionally report progress
        }

        protected void onPostExecute(String result) {
            // do something on the UI thread

        }
    }

    public void shutdown() {
        shutdownAsync shutdownAsync = new shutdownAsync();
        shutdownAsync.executeOnExecutor(SERIAL_EXECUTOR);
    }

    public void read(ReadableMap config) {
        if (this.reading) {
            Log.e("RFID", "already reading");
            return;
        }
        String err = null;
        if (this.rfidReaderDevice != null) {
            if (!rfidReaderDevice.getRFIDReader().isConnected()) {
                err = "read: device not connected";
            } else {
                RFIDReader rfidReader = rfidReaderDevice.getRFIDReader();
                try {
                    // Perform inventory
                    rfidReader.Actions.Inventory.perform(null, null, null);
                    reading = true;
                } catch (InvalidUsageException e) {
                    err = "read: invalid usage error on scanner read: " + e.getMessage();
                } catch (OperationFailureException ex) {
                    err = "read: error setting up scanner read: " + ex.getResults().toString();
                }
            }
        } else {
            err = "read: device not initialised";
        }
        if (err != null) {
            Log.e("RFID", err);
        }
    }


    public void cancel() {
        String err = null;
        if (this.rfidReaderDevice != null) {
            if (!rfidReaderDevice.getRFIDReader().isConnected()) {
                err = "cancel: device not connected";
            } else {
                if (reading) {
                    RFIDReader rfidReader = rfidReaderDevice.getRFIDReader();
                    try {
                        // Stop inventory
                        rfidReader.Actions.Inventory.stop();
                    } catch (InvalidUsageException e) {
                        err = "cancel: invalid usage error on scanner read: " + e.getMessage();
                    } catch (OperationFailureException ex) {
                        err = "cancel: error setting up scanner read: " + ex.getResults().toString();
                    }
                    reading = false;
                }
            }
        } else {
            err = "cancel: device not initialised";
        }
        if (err != null) {
            Log.e("RFID", err);
        }
    }

    public void reconnect() {
        if (this.rfidReaderDevice != null) {
            if (tempDisconnected) {
                RFIDReader rfidReader = rfidReaderDevice.getRFIDReader();
                if (!rfidReader.isConnected()) {
                    String err = null;
                    try {
                        // Stop inventory
                        rfidReader.reconnect();
                    } catch (InvalidUsageException e) {
                        err = "reconnect: invalid usage error: " + e.getMessage();
                    } catch (OperationFailureException ex) {
                        err = "reconnect error: " + ex.getResults().toString();
                    }
                    if (err != null) {
                        Log.e("RFID", err);
                    } else {
                        tempDisconnected = false;
                        WritableMap event = Arguments.createMap();
                        event.putString("RFIDStatusEvent", "opened");
                        this.dispatchEvent("RFIDStatusEvent", event);
                        Log.i("RFID", "Reconnected to " + rfidReaderDevice.getName());
                    }
                } else {
                    Log.i("RFID", rfidReaderDevice.getName() + " is already connected");
                }
            } else {
                Log.i("RFID", "reconnect: not temp disconnected");
            }
        } else {
            Log.i("RFID", "reconnect: device is null");
        }
    }

    public void settingAntennas(int powerLevel) {
        String err = null;
        try {
            if (this.rfidReaderDevice != null) {
                Antennas.AntennaRfConfig antennaRfConfig = this.rfidReaderDevice.getRFIDReader().Config.Antennas.getAntennaRfConfig(1);
                Log.i("RFID","ori config: " +antennaRfConfig);
                if(powerLevel != antennaRfConfig.getTransmitPowerIndex()) {
                    antennaRfConfig.setrfModeTableIndex(4);
                    antennaRfConfig.setTari(0);
                    antennaRfConfig.setTransmitPowerIndex(powerLevel);
                    this.rfidReaderDevice.getRFIDReader().Config.Antennas.setAntennaRfConfig(1, antennaRfConfig);
                    this.rfidReaderDevice.getRFIDReader().Config.saveConfig();
                }
                WritableMap event = Arguments.createMap();
                event.putString("SettingEvent", "Setting Antennas Completed");
                Log.i("RFID","" + event);
                this.dispatchEvent("SettingEvent", event);
                Log.i("RFID", "Setting antennas completed");
            }
        } catch (InvalidUsageException e) {
            err = "Setting Invalid error " + e.getMessage();
        } catch (OperationFailureException e) {
            err = "Setting Operation error " + e.getMessage();
        }
        if (err != null) {
            Log.e("RFID", err);
            WritableMap event = Arguments.createMap();
            event.putString("SettingEvent", "Setting Antennas Failed");
            this.dispatchEvent("SettingEvent", event);
        }
    }

    public void gettingBeeper() {
        String err = null;
        try {
            if (this.rfidReaderDevice != null) {
                BEEPER_VOLUME oribeeperVolume = this.rfidReaderDevice.getRFIDReader().Config.getBeeperVolume();
                WritableMap event = Arguments.createMap();
                event.putString("SettingEvent", oribeeperVolume.toString());
                Log.i("RFID", "successful");
                Log.i("RFID","ORIG Volume " + oribeeperVolume.toString());
                this.dispatchEvent("SettingEvent", event);
            }
            Log.i("RFID", "successful22");
        } catch (InvalidUsageException e) {
            err = "Setting Invalid error " + e.getMessage();
        } catch (OperationFailureException e) {
            err = "Setting Operation error " + e.getMessage();
        }
        if (err != null) {
            Log.e("RFID", err);
            WritableMap event = Arguments.createMap();
            event.putString("SettingEvent", "Getting Beeper Volume Failed");
            this.dispatchEvent("SettingEvent", event);
        }
    }

    public void settingBeeper(String beeperVolume) {
        String err = null;
        try {
            if (this.rfidReaderDevice != null && beeperVolume != null) {
                BEEPER_VOLUME oribeeperVolume = this.rfidReaderDevice.getRFIDReader().Config.getBeeperVolume();
                if (beeperVolume != oribeeperVolume.toString()) {
                    switch (beeperVolume) {
                        case "HIGH":
                            this.rfidReaderDevice.getRFIDReader().Config.setBeeperVolume(BEEPER_VOLUME.HIGH_BEEP);
                            break;
                        case "MEDIUM":
                            this.rfidReaderDevice.getRFIDReader().Config.setBeeperVolume(BEEPER_VOLUME.MEDIUM_BEEP);
                            break;
                        case "LOW":
                            this.rfidReaderDevice.getRFIDReader().Config.setBeeperVolume(BEEPER_VOLUME.LOW_BEEP);
                            break;
                        case "QUIET":
                            this.rfidReaderDevice.getRFIDReader().Config.setBeeperVolume(BEEPER_VOLUME.QUIET_BEEP);
                            break;
                    }
                    this.rfidReaderDevice.getRFIDReader().Config.saveConfig();
                }
                WritableMap event = Arguments.createMap();
                event.putString("SettingEvent", "Setting Bepper Completed");
                Log.i("RFID","" + event);
                this.dispatchEvent("SettingEvent", event);
                Log.i("RFID", "Setting bepper completed");
            }
        } catch (InvalidUsageException e) {
            err = "Setting Invalid error " + e.getMessage();
        } catch (OperationFailureException e) {
            err = "Setting Operation error " + e.getMessage();
        }
        if (err != null) {
            Log.e("RFID", err);
            WritableMap event = Arguments.createMap();
            event.putString("SettingEvent", "Setting Beeper Failed");
            this.dispatchEvent("SettingEvent", event);
        }
    }

    @Override
    public void eventReadNotify(RfidReadEvents rfidReadEvents) {
        // reader not active
        if (rfidReaderDevice == null) return;
        RFIDReader rfidReader = rfidReaderDevice.getRFIDReader();

        TagDataArray tagArray = rfidReader.Actions.getReadTagsEx(1000);
        if (tagArray != null) {
            //WritableArray rfidTags = Arguments.createArray();
            for (int i = 0; i < tagArray.getLength(); i++) {
                TagData tag = tagArray.getTags()[i];

                Log.i("RFID", "Tag ID = " + tag.getTagID());
                if (tag.getOpCode() == null) {
                    Log.w ("RFID", "null opcode");
                } else {
                    Log.w ("RFID", "opcode " + tag.getOpCode().toString());
                }
                JSONObject tagPayload = new JSONObject();
                    tagPayload.put("tag",tag.getTagID());
                    tagPayload.put("distance",tag.LocationInfo.getRelativeDistance());
                this.dispatchEvent("TagEvent", tagPayload);
                //rfidTags.pushString(tag.getTagID());
            }
            //this.dispatchEvent("TagsEvent", rfidTags);
        }
    }

    @Override
    public void eventStatusNotify(RfidStatusEvents rfidStatusEvents) {
        WritableMap event = Arguments.createMap();

        STATUS_EVENT_TYPE statusEventType = rfidStatusEvents.StatusEventData.getStatusEventType();
        if (statusEventType == STATUS_EVENT_TYPE.INVENTORY_START_EVENT) {
            event.putString("RFIDStatusEvent", "inventoryStart");
            reading = true;
        } else if (statusEventType == STATUS_EVENT_TYPE.INVENTORY_STOP_EVENT) {
            event.putString("RFIDStatusEvent", "inventoryStop");
            reading = false;
        } else if (statusEventType == STATUS_EVENT_TYPE.DISCONNECTION_EVENT) {
            event.putString("RFIDStatusEvent", "disconnect");
            reading = false;
            tempDisconnected = true;
        } else if (statusEventType == STATUS_EVENT_TYPE.BATCH_MODE_EVENT) {
            event.putString("RFIDStatusEvent", "batchMode");
            Log.i("RFID", "batch mode event: " + rfidStatusEvents.StatusEventData.BatchModeEventData.toString());
        } else if (statusEventType == STATUS_EVENT_TYPE.BATTERY_EVENT) {
            int level = rfidStatusEvents.StatusEventData.BatteryData.getLevel();
            event.putString("RFIDStatusEvent", "battery " + level);
            Log.i("RFID", "battery level " + level);
        } else if (statusEventType == STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
            HANDHELD_TRIGGER_EVENT_TYPE eventData = rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent();
            if (eventData == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {
                this.read(this.config);
            } else if (eventData == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
                this.cancel();
            }
        }
        if (event.hasKey("RFIDStatusEvent")) {
            this.dispatchEvent("RFIDStatusEvent", event);
        }
    }
}
