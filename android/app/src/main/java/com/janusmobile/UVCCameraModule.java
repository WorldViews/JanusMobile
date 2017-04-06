package com.janusmobile;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import android.hardware.usb.UsbDevice;
import android.util.Log;

import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;

import java.util.HashMap;
import java.util.Map;

public class UVCCameraModule extends ReactContextBaseJavaModule {
	private static final String TAG = "UVCCameraModule";
	private final boolean DEBUG = true;
	private USBMonitor mUSBMonitor;
	private HashMap<String, UVCCamera> mCameraMap = new HashMap<>();

    private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			//Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
			if (DEBUG) Log.v(TAG, "onAttach:");
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			if (DEBUG) Log.v(TAG, "onConnect:");
			UVCCamera camera = new UVCCamera();
			camera.open(ctrlBlock);
			mCameraMap.put(ctrlBlock.getDeviceName(), camera);
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			if (DEBUG) Log.v(TAG, "onDisconnect:");
			mCameraMap.remove(ctrlBlock.getDeviceName());
		}

		@Override
		public void onDettach(final UsbDevice device) {
//			Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
			if (DEBUG) Log.v(TAG, "onDettach:");
		}

		@Override
		public void onCancel(final UsbDevice device) {
			if (DEBUG) Log.v(TAG, "onCancel:");
		}
	};


    public UVCCameraModule(ReactApplicationContext reactContext) {
        super(reactContext);

		mUSBMonitor = new USBMonitor(reactContext, mOnDeviceConnectListener);

    }

    @Override
    public String getName() {
        return "UVCCameraModule";
    }

    @ReactMethod
    public void getCameraList(Callback callback) {
		WritableArray cameras = Arguments.createArray();
		for (Map.Entry<String, UVCCamera> entry : mCameraMap.entrySet()) {
			UVCCamera camera = entry.getValue();
			WritableMap cameraInfo = Arguments.createMap();
			cameraInfo.putInt("id", camera.getDevice().getDeviceId());
			cameraInfo.putString("name", camera.getDeviceName());
			cameras.pushMap(cameraInfo);
		}
		callback.invoke(cameras);
    }

}