import * as types from '../constants/ActionTypes'

export const route = (component) => ({ type: types.ROUTE, route:component })
export const connect = (username) => ({ type: types.CONNECT, username })
export const disconnect = () => ({ type: types.DISCONNECT })
export const localStream = (videoURL) => ({ type: types.LOCAL_STREAM, videoURL })
export const useOTGCamera = (useOTG) => ({ type: types.USE_OTG_CAMERA, useOTG })
export const setRoomId = (roomId) => ({ type: types.SET_ROOM_ID, roomId })
export const addStream = (stream) => ({ type: types.ADD_STREAM, stream })
export const removeStream = (stream) => ({ type: types.REMOVE_STREAM, stream })