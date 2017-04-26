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
    };

    let unsubscribe = store.subscribe(() => {
        let state = store.getState();
        if (this.state.route != state.actions.route) {
          this.setState({route: state.actions.route});
        }
      }
    )
  }

  static stateToProps(state) {
    return {
      roomId: state.action.roomId
    }
  }

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
