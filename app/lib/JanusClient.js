
import WebRTC, {
  RTCPeerConnection,
  RTCIceCandidate,
  RTCSessionDescription,
  RTCView,
  MediaStream,
  MediaStreamTrack,
  getUserMedia,
} from 'react-native-webrtc';

import Janus from './janus';

class JanusClient {

    constructor() {
        this.state = {};
    }

    //connect(url, username, password, roomId, cb) {
    connect(url, roomId, options) {
        options = options || {};
        this.options = options;
        this.state.url = url;
        this.state.username = options.username || 'anon';
        this.state.password = options.password || '';
        this.state.useOTG = options.useOTG || false;
        this.state.roomId = roomId;
        this.initWebRTC(options.success);
    }

    disconnect() {
        this.janus.destroy();
    }

    initWebRTC(cb) {
        let isFront = true;
        let self = this;
        MediaStreamTrack.getSources(sourceInfos => {
          console.log(sourceInfos);
          let videoSourceId = self.options.useOTG ? "UVCCamera" : undefined;
          // for (const i = 0; i < sourceInfos.length; i++) {
          //     const sourceInfo = sourceInfos[i];
          //     if (sourceInfo.kind == "video" && sourceInfo.facing == (isFront ? "front" : "back")) {
          //     videoSourceId = sourceInfo.id;
          //     }
          // }
          let constraints = {
              audio: true,
              video: {
              mandatory: {
                  minWidth: 640, // Provide your own width, height and frame rate here
                  minHeight: 480,
                  minFrameRate: 30
              },
              facingMode: (isFront ? "user" : "environment"),
              optional: (videoSourceId ? [{ sourceId: videoSourceId }] : [])
              }
          };
          getUserMedia(constraints, function (stream) {
              self.state.localStream = stream;
              self.initJanus(self.state.url, cb);
          }, console.error);
        });
    }

  initJanus(url, cb) {
    let self = this;
    Janus.init({
      debug: "all", 
      callback: () => {
        self.janus = new Janus({
          server: url,
          success: () => {
            self.attachVideoRoom(cb);
          }
        });
      }
    });
  }

  attachVideoRoom(cb) {
    let self = this;
    let opaqueId = "videoroom-" + Janus.randomString(12);
    self.janus.attach({
      plugin: "janus.plugin.videoroom",
      stream: self.state.localStream,
      opaqueId: opaqueId,
      success: function(pluginHandle) {
        // Step 1. Right after attaching to the plugin, we send a
        // request to join
        //connection = new FeedConnection(pluginHandle, that.room.id, "main");
        //connection.register(username);
        console.log("Plugin attached! (" + pluginHandle.getPlugin() + ", id=" + pluginHandle.getId() + ")");
        self.videoRoom = pluginHandle;
        var register = { "request": "join", "room": self.state.roomId, "ptype": "publisher", "display": self.state.username };
        pluginHandle.send({"message": register});
        if (cb) {
            cb(true);
        }
      },
      error: function(error) {
        //console.error("Error attaching plugin... " + error);

      },
      consentDialog: function(on) {
        console.log("Consent dialog should be " + (on ? "on" : "off") + " now");
        // $$rootScope.$broadcast('consentDialog.changed', on);
        // if(!on){
        //   //notify if joined muted
        //   if (startMuted) {
        //     $$rootScope.$broadcast('muted.Join');
        //   }
        // }
      },
      ondataopen: function() {
        console.log("The publisher DataChannel is available");
        //connection.onDataOpen();
        //sendStatus();
      },
      onlocalstream: function(stream) {
        // Step 4b (parallel with 4a).
        // Send the created stream to the UI, so it can be attached to
        // some element of the local DOM
        console.log(" ::: Got a local stream :::");        
        // var feed = FeedsService.findMain();
        // feed.setStream(stream);
      },
      oncleanup: function () {
        console.log(" ::: Got a cleanup notification: we are unpublished now :::");
      },
      mediaState: function(medium, on) {
        console.log("Janus " + (on ? "started" : "stopped") + " receiving our " + medium);
      },
      webrtcState: function(on) {
        console.log("Janus says our WebRTC PeerConnection is " + (on ? "up" : "down") + " now");
      },
      onmessage: function (msg, jsep) {
        var event = msg.videoroom;
        console.log("Event: " + event);

        // Step 2. Response from janus confirming we joined
        if (event === "joined") {
          console.log("Successfully joined room " + msg.room);
          //ActionService.enterRoom(msg.id, username, connection);
          // Step 3. Establish WebRTC connection with the Janus server
          // Step 4a (parallel with 4b). Publish our feed on server

          // if (jhConfig.joinUnmutedLimit !== undefined && jhConfig.joinUnmutedLimit !== null) {
          //   startMuted = (msg.publishers instanceof Array) && msg.publishers.length >= jhConfig.joinUnmutedLimit;
          // }

          // connection.publish({
          //   muted: startMuted,
          //   error: function() { connection.publish({noCamera: true, muted: startMuted}); }
          // });

          let media = {
            videoRecv: false, 
            audioRecv: false,
            videoSend: true,
            audioSend: true,
            data: true,
            video: 'main'
          };

          self.videoRoom.createOffer({
            stream: self.state.localStream,
            media: media,
            success: function(jsep) {
              console.log("Got publisher SDP!");
              console.log(jsep);
              // that.config = new ConnectionConfig(pluginHandle, cfg, jsep);
              // // Call the provided callback for extra actions
              // if (options.success) { options.success(); }

              var publish = { "request": "configure", "audio": true, "video": true };
              self.videoRoom.send({"message": publish, "jsep": jsep});
              
            },
            error: function(error) {
              console.error("WebRTC error publishing");
              console.error(error);
              // // Call the provided callback for extra actions
              // if (options.error) { options.error(); }
            }
          });

          // // Step 5. Attach to existing feeds, if any
          // if ((msg.publishers instanceof Array) && msg.publishers.length > 0) {
          //   that.subscribeToFeeds(msg.publishers, that.room.id);
          // }
          // The room has been destroyed
        } else if (event === "destroyed") {
          console.log("The room has been destroyed!");
          //$$rootScope.$broadcast('room.destroy');
        } else if (event === "event") {
          // Any new feed to attach to?
          if ((msg.publishers instanceof Array) && msg.publishers.length > 0) {
            //that.subscribeToFeeds(msg.publishers, that.room.id);
          // One of the publishers has gone away?
          } else if(msg.leaving !== undefined && msg.leaving !== null) {
            var leaving = msg.leaving;
            //ActionService.destroyFeed(leaving);
          // One of the publishers has unpublished?
          } else if(msg.unpublished !== undefined && msg.unpublished !== null) {
            var unpublished = msg.unpublished;
            //ActionService.destroyFeed(unpublished);
          // Reply to a configure request
          } else if (msg.configured) {
            // connection.confirmConfig();
          // The server reported an error
          } else if(msg.error !== undefined && msg.error !== null) {
            console.log("Error message from server" + msg.error);
            // $$rootScope.$broadcast('room.error', msg.error);
          }
        }

        if (jsep !== undefined && jsep !== null) {
          self.videoRoom.handleRemoteJsep({jsep: jsep});
        }
      }      
    })
  }

}

const janusClient = new JanusClient();
export default janusClient;