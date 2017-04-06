import React, { Component } from 'react';

import {
    Button,
    StyleSheet,
    TextInput,
    Text,
    View
} from 'react-native';

import VideoChatView from './VideoChatView';

import { connect } from 'react-redux';

import * as actions from '../actions';

import Toast from 'react-native-toast';

class LoginView extends Component {
    constructor(props) {
        super(props);
        this.state = { username: '' };
    }

    onLogin() {
        console.log('Login:', this.state);
        if (this.state.username) {
            this.props.dispatch(actions.connect(this.state.username));
            this.props.dispatch(actions.route(VideoChatView))
        } else {
            Toast.show("Please enter a username");
        }
    }

    render() {
        return (
            <View style={styles.container}>
                <Text style={styles.welcome}>
                    Janus Video Room
                </Text>
                <TextInput
                    style={styles.username}
                    placeholder="Username"
                    placeholderTextColor="#999"
                    underlineColorAndroid="#aaa"
                    onChangeText={(username) => this.setState({username})}
                    value={this.state.username}
                />
                <Button
                    onPress={() => this.onLogin()}
                    title="Login"
                    color="#4099FF"
                    accessibilityLabel="Login"
                />
            </View>
        )
    }
}

//const ConnectedLoginView = connect(mapStateToProps)(LoginView);
// const ConnectedLoginView = LoginView;

export default connect()(LoginView);

const styles = StyleSheet.create({
    container: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
        padding: 50,
        backgroundColor: '#333',
    },
    username: {
        height: 60,
        width: 300,
        borderColor: '#aaa', 
        borderWidth: 1,
        borderRadius: 5,
        padding: 10,
        fontSize: 20,
        color: '#000',
        backgroundColor: "#aaa",
        marginTop: 30,
        marginBottom: 30
    },
    welcome: {
        fontSize: 20,
        textAlign: 'center',
        color: '#fff',
        margin: 10,
    },
});