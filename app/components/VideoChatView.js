import React, { Component } from 'react';

import {
    Button,
    TextInput,
    StyleSheet,
    Text,
    View
} from 'react-native';

import { connect } from 'react-redux';

import * as actions from '../actions';

import WebRTC, {
  RTCPeerConnection,
  RTCIceCandidate,
  RTCSessionDescription,
  RTCView,
  MediaStream,
  MediaStreamTrack,
  getUserMedia,
} from 'react-native-webrtc';

import LoginView from './LoginView';

import JanusClient from '../lib/JanusClient'

import GPS from '../lib/GPS'

import KeepAwake from 'react-native-keep-awake';

class VideoChatView extends Component {
    constructor(props) {
        super(props);
        //this.state = { username: '' };
        this.state = {
          muteVideo: false,
          muteAudio: false
        };
        let self = this;
        this.gps = new GPS({username: this.props.username});

        //let state = this.props.getState();
        JanusClient.connect(this.props.janusURL, this.props.roomId,
        { username: this.props.username,
          useOTG: this.props.useOTG,
          success: () => {
            console.log('done');
            self.setState({
                videoURL: JanusClient.state.localStream.toURL()
            });
          },
          onaddstream: (stream) => this.props.dispatch(actions.addStream(stream)),
          onremovestream: (stream) => this.props.dispatch(actions.removeStream(stream))
        });
    }

    componentDidMount() {
        this.gps.start(this.props.useOTG ? '360': null);
        KeepAwake.activate();
    }

    componentWillUnmount() {
        this.gps.stop();
        KeepAwake.deactivate();
    }

    onLogout() {
        JanusClient.disconnect();
        this.props.dispatch(actions.disconnect());
        this.props.dispatch(actions.route(LoginView));
    }

    toggleAudio() {
      this.setState({muteAudio: !this.state.muteAudio});
      JanusClient.muteAudio(!this.state.muteAudio);
    }

    toggleVideo() {
      this.setState({muteVideo: !this.state.muteVideo});
      JanusClient.muteVideo(!this.state.muteVideo);
    }

    render() {
        return (
        <View style={styles.container}>
          <RTCView style={styles.video}
            objectFit="contain"
            zOrder={0}
            streamURL={this.state.videoURL}/>
          <View style={styles.videos}>
            {this.props.streams.map((stream) =>
              <RTCView
                key={stream.reactTag}
                style={styles.smallVideo}
                objectFit="contain"
                zOrder={1}
                streamURL={stream.toURL()}
               />
            )}
          </View>
          <View style={styles.controls}>
            <View style={styles.button}>
              <Button title="Logout"
                color="red"
                onPress={() => this.onLogout() }
              />
            </View>
            <View style={styles.button}>
              <Button style={styles.button}  title="Video"
                color={this.state.muteVideo ? "red" : "green"}
                onPress={() => this.toggleVideo() }
              />
            </View>
            <View style={styles.button}>
              <Button style={styles.button}  title="Audio"
                color={this.state.muteAudio ? "red" : "green"}
                onPress={() => this.toggleAudio() }
              />
            </View>
          </View>
        </View>
        )
    }
}

function mapStateToProps(state, props) {
    return {
      janusURL: state.actions.janusURL,
      username: state.actions.username,
      roomId: state.actions.roomId,
      useOTG: state.actions.useOTG,
      streams: state.actions.streams
     };
}

export default connect(mapStateToProps)(VideoChatView);

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#000',
  },
  welcome: {
    fontSize: 20,
    textAlign: 'center',
    color: '#cfc',
    margin: 10,
  },
  button: {
    margin: 5
  },
  instructions: {
    textAlign: 'center',
    color: '#fff',
    marginBottom: 5,
  },
  controls: {
    position:'absolute',
    bottom:110,
    right:0,
    left:0,
    height:50,
    opacity:1,
    backgroundColor:'transparent',
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center'
  },
  videos: {
    position:'absolute',
    bottom:0,
    right:0,
    left:0,
    height:110,
    opacity:1,
    backgroundColor:'transparent',
    flexDirection: 'row',
  },
  video: {
    position: 'absolute',
    left: 0,
    top: 0,
    backgroundColor: '#000',
    borderWidth: 1,
    width: '100%',
    height: '100%',
  },
  smallVideo: {
    margin:5,
    backgroundColor: '#000',
    width: 100,
    height: 100,
    borderRadius: 5,
  }
});
