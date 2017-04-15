package com.janusmobile.WebRTCModule;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.usb.UsbDevice;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import com.janusmobile.MainActivity;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.CameraDialog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.Logging;
import org.webrtc.RendererCommon;
import org.webrtc.Size;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.CameraEnumerationAndroid.CaptureFormat;
import org.webrtc.CameraEnumerationAndroid.CaptureFormat.FramerateRange;
import org.webrtc.CameraVideoCapturer.CameraEventsHandler;
import org.webrtc.CameraVideoCapturer.CameraStatistics;
import org.webrtc.CameraVideoCapturer.CameraSwitchHandler;
import org.webrtc.SurfaceTextureHelper.OnTextureFrameAvailableListener;
import org.webrtc.VideoCapturer.CapturerObserver;

import com.janusmobile.WebRTCModule.Stitcher;

import org.webrtc.VideoCapturer;

public class UVCCameraCapturer implements VideoCapturer, Runnable, USBMonitor.OnDeviceConnectListener, IFrameCallback {

    private static final String TAG = "UVCCameraCapturer";

    private Thread thread;
    private CapturerObserver frameObserver;
    private Context applicationContext;
    private volatile Handler cameraThreadHandler;
    private final AtomicBoolean isCameraRunning = new AtomicBoolean();
    private UVCCamera camera;
    private int preferredWidth;
    private int preferredHeight;

    private USBMonitor usbMonitor;

    private Surface previewSurface;

    Stitcher stitcher;

    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver frameObserver) {
        Logging.d("UVCCameraCapturer", "initialize");

        if (applicationContext == null) {
            throw new IllegalArgumentException("applicationContext not set.");
        }
        if (frameObserver == null) {
            throw new IllegalArgumentException("frameObserver not set.");
        }
        if (isInitialized()) {
            throw new IllegalStateException("Already initialized");
        }

        this.stitcher = new Stitcher(318.6, 318.8, 959.5, 318.9, 283.5, 720, 1280);

        this.frameObserver = frameObserver;
        this.applicationContext = applicationContext;

        this.cameraThreadHandler = surfaceTextureHelper == null ? null : surfaceTextureHelper.getHandler();
    }

    public void startCapture(final int width, final int height, final int framerate) {
        Logging.d("UVCCameraCapturer", String.format("startCapture %d %d %d", width, height, framerate));
        preferredWidth = width;
        preferredHeight = height;

        if (isCameraRunning.getAndSet(true)) {
            Logging.e(TAG, "Camera has already been started.");
            return;
        }

        usbMonitor = new USBMonitor(this.applicationContext, this);
        usbMonitor.register();
//        this.thread = new Thread(this);
//        this.thread.start();
//        this.frameObserver.onCapturerStarted(true);
    }

    public void stopCapture() throws InterruptedException {
        Logging.d("UVCCameraCapturer", "stopCapture");
        usbMonitor.unregister();
        releaseCamera();
        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
        if (this.usbMonitor != null) {
            this.usbMonitor.destroy();
        }
//        isCameraRunning.getAndSet(false);
//        this.thread.wait();
//        this.frameObserver.onCapturerStopped();
    }

    public void changeCaptureFormat(int width, int height, int framerate) {
        Logging.d("UVCCameraCapturer", String.format("changeCaptureFormat %d %d %d", width, height, framerate));
    }

    public void dispose() {
        Logging.d("UVCCameraCapturer", "dispose");
        releaseCamera();
        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
        if (this.usbMonitor != null) {
            this.usbMonitor.destroy();
        }
    }

    public boolean isScreencast() {
        return false;
    }

    public void run() {
        byte[] data = new byte[(320*200*3)/2];
        for (int i = 0; i < 320*200; i++) {
            data[i] = (byte)0xff;
        }
        while (isCameraRunning.get()) {
            Logging.d("UVCCameraCapturer", "captureFrame");
            try {
                long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
                this.frameObserver.onByteBufferFrameCaptured(
                        data, 320, 200, 0, captureTimeNs);
                this.thread.sleep(100);

            } catch (InterruptedException e) {
                Logging.d("UVCCameraCapturer", "InterruptedException");
            }
        }
    }

    private boolean isInitialized() {
        return applicationContext != null && frameObserver != null;
    }

    private boolean maybePostOnCameraThread(Runnable runnable) {
        return maybePostDelayedOnCameraThread(0 /* delayMs */, runnable);
    }
    private boolean maybePostDelayedOnCameraThread(int delayMs, Runnable runnable) {
        return cameraThreadHandler != null && isCameraRunning.get()
                && cameraThreadHandler.postAtTime(
                runnable, this /* token */, SystemClock.uptimeMillis() + delayMs);
    }


    @Override
    public void onAttach(final UsbDevice device) {
        //Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
        Log.v(TAG, "onAttach:");
        usbMonitor.requestPermission(device);
    }

    @Override
    public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
        Log.v(TAG, "onConnect:");
        try {
            releaseCamera();
            this.camera = new UVCCamera();
            this.camera.open(ctrlBlock);

            // figure out width, height, and format
            String json = camera.getSupportedSize();
            JSONObject obj = new JSONObject(json);
            JSONArray formats = obj.getJSONArray("formats");

            int width = UVCCamera.DEFAULT_PREVIEW_WIDTH, height = UVCCamera.DEFAULT_PREVIEW_HEIGHT, formatId = 1;
            for (int i = 0; i < formats.length(); i++) {
                JSONObject format = formats.getJSONObject(i);
                formatId = format.getInt("default");
                JSONArray sizes = format.getJSONArray("size");
                boolean foundPreferred = false;
                for (int j = 0; j < sizes.length(); j++) {
                    String sizeString = sizes.getString(j);
                    int size[] = parseSize(sizeString);

                    if (preferredWidth == size[0] && preferredHeight == size[1]) {
                        foundPreferred = true;
                    }

                    // find the largest
                    if (width < size[0]) {
                        width = size[0];
                        height = size[1];
                    }
                }

                if (foundPreferred) {
                    width = preferredWidth;
                    height = preferredHeight;
                }
            }

            this.camera.setPreviewSize(width, height, formatId);
            this.camera.setFrameCallback(this, UVCCamera.PIXEL_FORMAT_YUV420SP);

            final int[] textureHandle = new int[1];
            GLES20.glGenTextures(1, textureHandle, 0);
            final SurfaceTexture st = new SurfaceTexture(textureHandle[0], false);
            //        final SurfaceTexture st = new SurfaceTexture(0, false);
            if (st != null) {
                previewSurface = new Surface(st);
                camera.setPreviewDisplay(previewSurface);
            }

            this.camera.startPreview();
            this.frameObserver.onCapturerStarted(true);
        }
        catch (UnsupportedOperationException e) {
            Log.e(TAG, "Unable to connect to camera: " + e.toString());
        }
        catch (JSONException e) {
            Log.e(TAG, "JSON exception: " + e.toString());
        }
    }

    @Override
    public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
        Log.v(TAG, "onDisconnect:");
//        this.camera.close();
        releaseCamera();
        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
    }

    @Override
    public void onDettach(final UsbDevice device) {
//			Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        Log.v(TAG, "onDettach:");
        releaseCamera();
    }

    @Override
    public void onCancel(final UsbDevice device) {
        Log.v(TAG, "onCancel:");
        releaseCamera();
    }

    public void onFrame(ByteBuffer frame) {
//        Log.v(TAG, "onFrame:");
        long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
        //byte data[] = frame.array();
        byte data[] = new byte[frame.remaining()];
        frame.get(data);
        byte equiData[] = new byte[2048 * ( 1024 + 1024 / 2)];
        stitcher.stitch(720, 1280, data, equiData);
        //this.frameObserver.onByteBufferFrameCaptured(data, 1280, 720, 0, captureTimeNs);
        this.frameObserver.onByteBufferFrameCaptured(equiData, 1280, 720, 0, captureTimeNs);
    }

    private synchronized void releaseCamera() {
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.setPreviewDisplay((Surface)null);
                camera.setStatusCallback(null);
                camera.setButtonCallback(null);
                camera.close();
                camera.destroy();
            } catch (final Exception e) {
                //
            }
            camera = null;
        }
    }

    // parse string that looks like "1280x720"
    private int[] parseSize(String size) {
        int retval[] = new int[2];
        String vals[] = size.split("x");
        if (vals.length != 2) {
            return retval;
        }
        retval[0] = Integer.parseInt(vals[0]);
        retval[1] = Integer.parseInt(vals[1]);
        return retval;
    }

}
