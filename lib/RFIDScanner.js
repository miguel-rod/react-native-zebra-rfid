import { NativeModules, DeviceEventEmitter } from "react-native";
import { RFIDScannerEvent } from "./RFIDScannerEvent";

const rfidScannerManager = NativeModules.RFIDScannerManager;

let instance = null;

export class RFIDScanner {
  constructor() {
    if (!instance) {
      instance = this;
      this.opened = false;
      this.deferReading = false;
      this.oncallbacks = [];
      this.config = {};
      this.locateTag = null;

      DeviceEventEmitter.addListener(
        "TagEvent",
        this.handleTagEvent.bind(this)
      );
      DeviceEventEmitter.addListener(
        "TagsEvent",
        this.handleTagsEvent.bind(this)
      );
      DeviceEventEmitter.addListener(
        "RFIDStatusEvent",
        this.handleStatusEvent.bind(this)
      );
      DeviceEventEmitter.addListener(
        "SettingEvent",
        this.handleSettingEvent.bind(this)
      );
    }
  }

  handleStatusEvent(event) {
    console.log("RFID status event " + event.RFIDStatusEvent);
    if (event.RFIDStatusEvent === "opened") {
      this.opened = true;
      if (this.deferReading) {
        rfidScannerManager.read(this.config);
        this.deferReading = false;
      }
    } else if (event.RFIDStatusEvent === "closed") {
      this.opened = false;
    }
  }

  handleSettingEvent(event) {
    console.log("Setting Event " + event.SettingEvent);
  }

  handleTagEvent(tag) {
    if (this.oncallbacks.hasOwnProperty(RFIDScannerEvent.TAG)) {
      this.oncallbacks[RFIDScannerEvent.TAG].forEach(callback => {
        callback(tag);
      });
    }
  }

  handleTagsEvent(tags) {
    if (this.oncallbacks.hasOwnProperty(RFIDScannerEvent.TAGS)) {
      this.oncallbacks[RFIDScannerEvent.TAGS].forEach(callback => {
        callback(tags);
      });
    }
  }

  init() {
    rfidScannerManager.init();
  }

  locateTag(tag) {
    this.locateTag = tag;
  }

  read(config = {}) {
    this.config = config;

    if (this.opened) {
      if (this.locateTag != null) {
        rfidScannerManager.locateTag(this.config, tag);
      } else {
        rfidScannerManager.read(this.config, null);
      }
    } else {
      this.deferReading = true;
    }
  }

  reconnect() {
    rfidScannerManager.reconnect();
  }

  cancel() {
    rfidScannerManager.cancel();
  }

  shutdown() {
    rfidScannerManager.shutdown();
  }

  settingAntennas(powerLevel) {
    rfidScannerManager.settingAntennas(powerLevel);
  }

  settingBeeper(beeperVolume) {
    rfidScannerManager.settingBeeper(beeperVolume);
  }

  gettingBeeper() {
    rfidScannerManager.gettingBeeper();
  }

  on(event, callback) {
    if (!this.oncallbacks[event]) {
      this.oncallbacks[event] = [];
    }
    this.oncallbacks[event].push(callback);
  }

  removeon(event, callback) {
    if (this.oncallbacks.hasOwnProperty(event)) {
      this.oncallbacks[event].forEach((funct, index) => {
        if (funct.toString() === callback.toString()) {
          this.oncallbacks[event].splice(index, 1);
        }
      });
    }
  }

  hason(event, callback) {
    let result = false;
    if (this.oncallbacks.hasOwnProperty(event)) {
      this.oncallbacks[event].forEach((funct, index) => {
        if (funct.toString() === callback.toString()) {
          result = true;
        }
      });
    }
    return result;
  }
}

export default new RFIDScanner();
