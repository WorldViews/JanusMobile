
import io from "socket.io-client";

export default class Gps {
  constructor(options) {
      options = options || {};
      this.url = options.url || "https://worldviews.org/";
      this.interval = options.interval || 1000;
      this.watchHandle = null;
      this.username = options.username || "Unknown";
  };

  start() {
      this.sock = io.connect(this.url);

      if (navigator.geolocation) {
           this.watchHandle = navigator.geolocation.watchPosition((position) => {
               console.log("GPS got loc");
               let msg = {
                   msgType: 'position',
                   version: 0.1,
                   clientType: 'android',
                   clientId: this.username,
                   t: new Date().getTime() / 1000.0,
                   position: [position.coords.latitude, position.coords.longitude],
                   coordSys: 'geo'
               };
               console.log("GPS: " + JSON.stringify(msg));
               this.sock.emit('position', msg);
               console.log("GPS emitted the msg");
           });
      }
      console.log("GPS:  start");
  };

  stop() {
      console.log("GPS:  stop");
      if (this.watchHandle) {
          navigator.geolocation.clearWatch(this.watchHandler);
          this.watchHandle = null;
      }
  };
};
