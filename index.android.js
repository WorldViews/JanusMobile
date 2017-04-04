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
  View
} from 'react-native';

var WebRTC = require('react-native-webrtc');
var {
  RTCPeerConnection,
  RTCIceCandidate,
  RTCSessionDescription,
  RTCView,
  MediaStream,
  MediaStreamTrack,
  getUserMedia,
} = WebRTC;


export default class JanusMobile extends Component {

  constructor() {
    super();
    this.state = { videoURL: null };
    this.initWebRTC();
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
            minWidth: 500, // Provide your own width, height and frame rate here
            minHeight: 300,
            minFrameRate: 30
          },
          facingMode: (isFront ? "user" : "environment"),
          optional: (videoSourceId ? [{ sourceId: videoSourceId }] : [])
        }
      }, function (stream) {
        let url = stream.toURL()
        console.log('got stream!!', url);
        self.setState({videoURL: url });
        //callback(stream);
      }, console.error);
    });
  }

  render() {
      return (<View>
        <RTCView style={styles.video} 
        objectFit="cover"
        streamURL={this.state.videoURL}/>
      </View>)
  }

  renderx() {
    return (
      <View style={styles.container}>
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
        <RTCView style={styles.video}
          objectFit="contain"
          streamURL={this.state.videoURL}/>
      </View>
    );
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
    margin: 10,
  },
  instructions: {
    textAlign: 'center',
    color: '#333333',
    marginBottom: 5,
  },
  video: {
    backgroundColor: '#ccc',
    borderWidth: 1,
    width: '100%',
    height: '100%',
  },
});

AppRegistry.registerComponent('JanusMobile', () => JanusMobile);
