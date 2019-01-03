package com.headuck.reactnativezebrarfid;

import android.os.AsyncTask;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.OperationFailureException;
import com.zebra.rfid.api3.RFIDReader;
import com.zebra.rfid.api3.RFIDResults;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.Readers;
import com.zebra.rfid.api3.RegionInfo;
import com.zebra.rfid.api3.RegulatoryConfig;
import com.zebra.rfid.api3.START_TRIGGER_TYPE;
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE;
import com.zebra.rfid.api3.SupportedRegions;
import com.zebra.rfid.api3.TriggerInfo;

import java.util.ArrayList;

public class RFIDScannerAsync extends AsyncTask<Object, String, String> {

    private RFIDScannerThread scannerthread;
    private Readers readers = null;
    private ArrayList<ReaderDevice> deviceList = null;
    private ReaderDevice rfidReaderDevice = null;
    boolean tempDisconnected = false;
    private Boolean reading = false;
    private ReadableMap config = null;

    protected String doInBackground(Object... objects) {
        String err = null;
        if (objects[0] != null) {
            if (((ReaderDevice) objects[0]).getRFIDReader().isConnected())
            disconnect();
        }
        try {

            Log.v("RFID", "initScanner");

            ArrayList<ReaderDevice> availableRFIDReaderList = null;
            try {
                availableRFIDReaderList = ((Readers)objects[1]).GetAvailableRFIDReaderList();
                Log.v("RFID", "Available number of reader : " + availableRFIDReaderList.size());
                ArrayList<ReaderDevice> deviceList = availableRFIDReaderList;

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
                            rfidReader.Events.addEventsListener(scannerthread);
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
                                        if ("HKG".equals(regionInfo.getRegionCode())) {
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
                    scannerthread.dispatchEvent("RFIDStatusEvent", event);
                    Log.i("RFID", "Connected to " + rfidReaderDevice.getName());
                    return "success";
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
        return "";
    }

    private void setTriggerImmediate(RFIDReader reader) throws InvalidUsageException, OperationFailureException {
        TriggerInfo triggerInfo = new TriggerInfo();
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
                    rfidReader.Events.removeEventsListener(scannerthread);
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
            scannerthread.dispatchEvent("RFIDStatusEvent", event);
            rfidReaderDevice = null;
            tempDisconnected = false;
        } else {
            Log.w("RFID", "disconnect: no device was connected");
        }

    }

    protected void onProgressUpdate(Integer... progress) {
        // optionally report progress
    }

    protected void onPostExecute(Long result) {
        // do something on the UI thread
    }

    public ReaderDevice getRederDevice(){
        return rfidReaderDevice;
    }

}
