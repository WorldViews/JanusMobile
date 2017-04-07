import React, { Component } from 'react';

import {
    Image,
    StyleSheet,
    TextInput,
    Text,
    View
} from 'react-native';

import Button from 'react-native-button';

import VideoChatView from './VideoChatView';

import { connect } from 'react-redux';

import * as actions from '../actions';

import Toast from 'react-native-toast';

import CheckBox from 'react-native-checkbox';

class LoginView extends Component {
    constructor(props) {
        super(props);
        this.state = { 
            username: '',
            roomId: props.roomId.toString(),
            useOTG: props.useOTG
         };
    }

    onLogin() {
        console.log('Login:', this.state);
        if (this.state.username) {
            this.props.dispatch(actions.setRoomId(parseInt(this.state.roomId)));
            this.props.dispatch(actions.useOTGCamera(this.state.useOTG));
            this.props.dispatch(actions.connect(this.state.username));
            this.props.dispatch(actions.route(VideoChatView))
        } else {
            Toast.show("Please enter a username");
        }
    }

    useOTG(checked) {
        //this.props.dispatch(actions.useOTGCamera(!checked));
        this.setState({useOTG:!checked});
    }

    render() {
        return (
            <View style={styles.container}>
                <Image style={styles.logo}
                    source={require('../img/logo.png')}
                    />
                <Text style={styles.welcome}>
                    Janus Video Room
                </Text>
                <TextInput
                    style={styles.input}
                    placeholder="Username"
                    placeholderTextColor="#aaa"
                    underlineColorAndroid="#fff"
                    onChangeText={(username) => this.setState({username})}
                    value={this.state.username}
                />
                <TextInput
                    style={styles.input}
                    placeholder="RoomId"
                    placeholderTextColor="#aaa"
                    underlineColorAndroid="#fff"
                    onChangeText={(roomId) => this.setState({roomId})}
                    value={this.state.roomId}
                />                
                <View style={styles.checkbox}>
                    <CheckBox label="Use OTG Camera"
                        labelStyle={styles.text}
                        checked={this.state.useOTG}
                        underlayColor="#4099ff"
                        onChange={(checked) => this.useOTG(checked)}
                        />
                </View>
                <Button
                    activeOpacity={0.7}
                    style={styles.login}
                    onPress={() => this.onLogin()}
                >LOGIN</Button>
            </View>
        )
    }
}

//const ConnectedLoginView = connect(mapStateToProps)(LoginView);
// const ConnectedLoginView = LoginView;

function mapStateToProps(state) {
    return { 
        roomId: state.actions.roomId,
        useOTG: state.actions.useOTG
    };
}

export default connect(mapStateToProps)(LoginView);

const styles = StyleSheet.create({
    container: {
        flex: 1,
        alignItems: 'center',
        padding: 50,
        backgroundColor: '#4099ff',
    },
    logo: {
        marginTop: 40
    },
    login: {
        padding: 10,
        backgroundColor: '#4c66a4',
        width: 270,
        color: '#fff',
        borderRadius: 5,
        borderWidth: 2,
        borderColor: '#3c56b4'
    },
    input: {
        height: 60,
        width: 300,
        borderColor: '#aaa', 
        borderWidth: 1,
        borderRadius: 5,
        padding: 10,
        fontSize: 20,
        color: '#000',
        backgroundColor: "#fff",
        marginTop: 10   ,
        marginBottom: 10
    },
    welcome: {
        fontSize: 25,
        textAlign: 'center',
        color: '#fff',
        margin: 10,
    },
    checkbox: {
        marginBottom: 10
    },
    text: {
        color: '#fff'
    }
});