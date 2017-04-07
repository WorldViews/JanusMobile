/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 * @flow
 */

import React, { Component } from 'react';
import {
  AppRegistry,
  StyleSheet,
  Text,
  View,
  Navigator,
  NativeModules
} from 'react-native';

import WebRTC, {
  RTCPeerConnection,
  RTCIceCandidate,
  RTCSessionDescription,
  RTCView,
  MediaStream,
  MediaStreamTrack,
  getUserMedia,
} from 'react-native-webrtc';

import { connect } from 'react-redux';

//import Janus from 'janus-gateway';
import Janus from './lib/janus'

import { Match, MemoryRouter as Router } from 'react-router';

import LoginView from './components/LoginView';
import VideoChatView from './components/VideoChatView';

import { createStore } from 'redux';
import { Provider } from 'react-redux';

import reducer from './reducers'
const store = createStore(reducer)


export default class JanusMobile extends Component {

  constructor(props) {
    super(props);
    this.state = { 
      route: LoginView,
      store,
      videoURL: null,
      connected: false,
      janusURL: 'wss://sd6.dcpfs.net:8989/janus',
      roomId: 9000
    };

    let unsubscribe = store.subscribe(() => {
        let state = store.getState();
        if (this.state.route != state.actions.route) {
          this.setState({route: state.actions.route});
        }
      }
    )
  }

  initWebRTC() {
    let isFront = true;
    let self = this;
    MediaStreamTrack.getSources(sourceInfos => {
      console.log(sourceInfos);
      let videoSourceId;
      for (const i = 0; i < sourceInfos.length; i++) {
        const sourceInfo = sourceInfos[i];
        if (sourceInfo.kind == "video" && sourceInfo.facing == (isFront ? "front" : "back")) {
          videoSourceId = sourceInfo.id;
        }
      }
      getUserMedia({
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
      }, function (stream) {
        self.localStream = stream;
        self.initJanus();
        let url = stream.toURL()
        self.setState({videoURL: url });
      }, console.error);
    });
  }

  initJanus() {
    let self = this;
    Janus.init({
      debug: "all", 
      callback: () => {
        let janusURL = self.state.janusURL;
        self.janus = new Janus({
          server: janusURL,
          success: () => {
            self.attachVideoRoom();
          }
        });
      }
    });
  }

  attachVideoRoom() {
    let self = this;
    let opaqueId = "videoroom-" + Janus.randomString(12);
    self.janus.attach({
      plugin: "janus.plugin.videoroom",
      stream: self.localStream,
      opaqueId: opaqueId,
      success: function(pluginHandle) {
        // Step 1. Right after attaching to the plugin, we send a
        // request to join
        //connection = new FeedConnection(pluginHandle, that.room.id, "main");
        //connection.register(username);
        console.log("Plugin attached! (" + pluginHandle.getPlugin() + ", id=" + pluginHandle.getId() + ")");
        self.videoRoom = pluginHandle;
        var register = { "request": "join", "room": self.state.roomId, "ptype": "publisher", "display": "xxxx" };
        pluginHandle.send({"message": register});
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
            stream: self.localStream,
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
            that.subscribeToFeeds(msg.publishers, that.room.id);
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

  // componentFactory(name) {
  //   switch (name) {
  //     case 'LoginView':
  //       return <LoginView/>
  //     case 'VideoChatView':
  //       return <VideoChatView/>
  //     default:
  //       return <LoginView/>
  //   }
  // }

    /**
     * Create a ReactElement from the specified component, the specified props
     * and the props of this AbstractApp which are suitable for propagation to
     * the children of this Component.
     *
     * @param {Component} component - The component from which the ReactElement
     * is to be created.
     * @param {Object} props - The read-only React Component props with which
     * the ReactElement is to be initialized.
     * @returns {ReactElement}
     * @protected
     */
    _createElement(component, props) {
        /* eslint-disable no-unused-vars, lines-around-comment */
        const {
            // Don't propagate the config prop(erty) because the config is
            // stored inside the Redux state and, thus, is visible to the
            // children anyway.
            config,
            // Don't propagate the dispatch and store props because they usually
            // come from react-redux and programmers don't really expect them to
            // be inherited but rather explicitly connected.
            dispatch, // eslint-disable-line react/prop-types
            store,
            // The url property was introduced to be consumed entirely by
            // AbstractApp.
            url,
            // The remaining props, if any, are considered suitable for
            // propagation to the children of this Component.
            ...thisProps
        } = this.props;
        /* eslint-enable no-unused-vars, lines-around-comment */

        // eslint-disable-next-line object-property-newline
        return React.createElement(component, { ...thisProps, ...props });
    }

  render() {
    return (<Provider store={store}>
      {this._createElement(this.state.route)}
    </Provider>)
  }

  renderxx() {
      return (
        <View style={styles.container}>
          <RTCView style={styles.video}
            objectFit="cover"
            streamURL={this.state.videoURL}/>
          <Text style={styles.welcome}>
            Welcome to React Native!
          </Text>
          <Text style={styles.instructions}>
            To get started, edit index.android.js
          </Text>
          <Text style={styles.instructions}>
            Double tap R on your keyboard to reload,{'\n'}
            Shake or press menu button for dev menu
          </Text>
        </View>
      )
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  welcome: {
    fontSize: 20,
    textAlign: 'center',
    color: '#cfc',
    margin: 10,
  },
  instructions: {
    textAlign: 'center',
    color: '#fff',
    marginBottom: 5,
  },
  video: {
    position: 'absolute',
    left: 0,
    top: 0,
    backgroundColor: '#ccc',
    borderWidth: 1,
    width: '100%',
    height: '100%',
  },
});

AppRegistry.registerComponent('JanusMobile', () => JanusMobile);
