import { combineReducers } from 'redux'
import * as types from '../constants/ActionTypes'

import { View } from 'react-native';

const initialState = {
    username: "anon",
    route: View,
    store: null,
    videoURL: null,
    connected: false,
    janusURL: 'wss://sd6.dcpfs.net:8989/janus',
    roomId: 9000,
    useOTG: true
}

function actions(state = initialState, action) {
    switch (action.type) {
        case types.ROUTE:
            return {
                ...state,
                route: action.route
            };
        case types.CONNECT:
            return {
                ...state,
                connected: true,
                username: action.username
            };
        case types.DISCONNECT:
            return {
                ...state,
                connected: true
            };
        case types.LOCAL_STREAM:
            return {
                ...state,
                videoURL: action.videoURL
            };
        case types.USE_OTG_CAMERA:
            return {
                ...state,
                useOTG: action.useOTG
            }
        default:
            return state;
    }
}

const rootReducer = combineReducers({
    actions
})

export default rootReducer