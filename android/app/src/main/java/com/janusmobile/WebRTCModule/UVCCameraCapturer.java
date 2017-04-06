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

import org.webrtc.VideoCapturer;

public class UVCCameraCapturer implements VideoCapturer, Runnable, USBMonitor.OnDeviceConnectListener, IFrameCallback {

    private static final String TAG = "UVCCameraCapturer";

    private Thread thread;
    private CapturerObserver frameObserver;
    private Context applicationContext;
    private volatile Handler cameraThreadHandler;
    private final AtomicBoolean isCameraRunning = new AtomicBoolean();
    private UVCCamera camera;

    private USBMonitor usbMonitor;

    private TextureView cameraView;


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

        cameraView = new TextureView(applicationContext);

        this.frameObserver = frameObserver;
        this.applicationContext = applicationContext;

        this.cameraThreadHandler = surfaceTextureHelper == null ? null : surfaceTextureHelper.getHandler();
    }

    public void startCapture(final int width, final int height, final int framerate) {
        Logging.d("UVCCameraCapturer", String.format("startCapture %d %d %d", width, height, framerate));

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
        if (camera != null) {
            camera.stopCapture();
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
        this.camera = new UVCCamera();
        this.camera.open(ctrlBlock);
        this.camera.setPreviewSize(1280, 720, 1, 30, 2, 1.0f);
        this.camera.setFrameCallback(this, UVCCamera.PIXEL_FORMAT_YUV420SP);

        final int[] textureHandleA = new int[3];
        GLES20.glGenTextures(3, textureHandleA, 0);
        final SurfaceTexture st = new SurfaceTexture(20, false);
//        final SurfaceTexture st = new SurfaceTexture(0, false);
        if (st != null) {
            Surface surface = new Surface(st);
            camera.setPreviewDisplay(surface);
        }

        this.camera.startPreview();
        this.frameObserver.onCapturerStarted(true);
    }

    @Override
    public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
        Log.v(TAG, "onDisconnect:");
        this.camera.close();
    }

    @Override
    public void onDettach(final UsbDevice device) {
//			Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        Log.v(TAG, "onDettach:");
    }

    @Override
    public void onCancel(final UsbDevice device) {
        Log.v(TAG, "onCancel:");
    }

    public void onFrame(ByteBuffer frame) {
        Log.v(TAG, "onFrame:");
        long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
        //byte data[] = frame.array();
        byte data[] = new byte[frame.remaining()];
        frame.get(data);
        this.frameObserver.onByteBufferFrameCaptured(data, 1280, 720, 0, captureTimeNs);
    }

    private synchronized void releaseCamera() {
        if (camera != null) {
            try {
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

}
