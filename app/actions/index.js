import * as types from '../constants/ActionTypes'

export const route = (component) => ({ type: types.ROUTE, route:component })
export const connect = (username) => ({ type: types.CONNECT, username })
export const disconnect = () => ({ type: types.DISCONNECT })
export const localStream = (videoURL) => ({ type: types.LOCAL_STREAM,videoURL })
