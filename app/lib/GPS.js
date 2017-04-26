
import io from "socket.io-client";
import { DeviceEventEmitter } from 'react-native';
import { RNLocation as Location } from 'NativeModules';

export default class Gps {
  constructor(options) {
      options = options || {};
      this.url = options.url || "https://worldviews.org/";
      this.interval = options.interval || 1000;
      this.watchHandle = null;
      this.username = options.username || "Unknown";
  };

  start(videoType) {
    Location.startUpdatingLocation();
    this.sock = io.connect(this.url, {transports: ['websocket'], upgrade: false});

    //   if (navigator.geolocation) {
    //        this.watchHandle = navigator.geolocation.watchPosition((position) => {
    //            console.log("GPS got loc");
    //            let msg = {
    //                msgType: 'position',
    //                version: 0.1,
    //                clientType: 'android',
    //                clientId: this.username,
    //                t: new Date().getTime() / 1000.0,
    //                position: [position.coords.latitude, position.coords.longitude],
    //                coordSys: 'geo'
    //            };
    //            console.log("GPS: " + JSON.stringify(msg));
    //            this.sock.emit('position', msg);
    //            console.log("GPS emitted the msg");
    //        });
    //   }
    console.log("GPS:  start");

    var subscription = DeviceEventEmitter.addListener(
        'locationUpdated',
        (location) => {
            /* Example location returned
            {
                speed: -1,
                longitude: -0.1337,
                latitude: 51.50998,
                accuracy: 5,
                heading: -1,
                altitude: 0,
                altitudeAccuracy: -1
            }
            */
            let msg = {
                msgType: 'position',
                version: 0.1,
                clientType: 'android',
                //videoType: videoType,
                videoType: '360',
                clientId: this.username,
                t: new Date().getTime() / 1000.0,
                position: [location.latitude, location.longitude, location.altitude],
                coordSys: 'geo'
            };
            console.log("GPS: " + JSON.stringify(msg));
            this.sock.emit('position', msg);
            console.log("GPS emitted the msg");
        }
    );
  };

  stop() {
      Location.stopUpdatingLocation();
      console.log("GPS:  stop");
      if (this.watchHandle) {
          navigator.geolocation.clearWatch(this.watchHandler);
          this.watchHandle = null;
      }
  };
};
