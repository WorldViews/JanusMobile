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

class VideoChatView extends Component {
    constructor(props) {
        super(props);
        //this.state = { username: '' };
        this.state = {};
        let self = this;

        //let state = this.props.getState();
        JanusClient.connect(this.props.janusURL, this.props.username, '', this.props.roomId, () => {
            console.log('done');
            self.setState({
                videoURL: JanusClient.state.localStream.toURL()
            });
        });
    }

    onLogout() {
        JanusClient.disconnect();
        this.props.dispatch(actions.disconnect());
        this.props.dispatch(actions.route(LoginView));
    }

    toggleAudio() {

    }

    toggleVideo() {

    }

    render() {
        return (
        <View style={styles.container}>
          <RTCView style={styles.video}
            objectFit="cover"
            streamURL={this.state.videoURL}/>
            <View style={styles.bottom}>
              <Button title="Logout"
                color="red"
                onPress={() => this.onLogout() }
              />
              <Button title="Audio"
                onPress={() => this.toggleAudio() }
              />
              <Button title="Video"
                onPress={() => this.toggleVideo() }
              />
          </View>
        </View>
        )
    }
}

function mapStateToProps(state, props) {
    return { janusURL: state.actions.janusURL, 
        username: state.actions.username,
    roomId: state.actions.roomId };
}

export default connect(mapStateToProps)(VideoChatView);

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
  bottom: {
    position:'absolute',
    bottom:30,
    right:0,
    left:0,
    height:50,
    opacity:1,
    backgroundColor:'transparent',
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center'
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