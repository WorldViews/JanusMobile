package com.janusmobile.WebRTCModule;

import android.util.Log;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.util.Arrays;
import static org.opencv.core.CvType.CV_64F;
import static org.opencv.core.CvType.CV_32FC1;
import static org.opencv.core.CvType.CV_8UC1;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 Convert dual fisheye image to equirectangular.
 */

public class DualFisheyeStitcher {

    private static final DualFisheyeStitcher instance = new DualFisheyeStitcher();

    public static DualFisheyeStitcher getInstance(){
        return instance;
    }

    private static final String TAG = "Stitcher";

    private Mat xMap;
    private Mat yMap;

    private int viewWidth = 1280;
    private int viewHeight = 720;

    private double rmax;
    private double phiMax;

    private double cx, cy, cx2, cy2;

    private double yaw_radiansPerPixel;
    private double phi_radiansPerPixel;

    private Mat RM_inv = null;
    private boolean haveMap = false;


    byte[] ySrc = null;
    byte[] uSrc = null;
    byte[] vSrc = null;

    Mat mSrcY = null;
    Mat mSrcUsmall = null;
    Mat mSrcVsmall = null;
    Mat mSrcU = null;
    Mat mSrcV = null;
    Mat mSrcYUV = null;

    Mat mDstYUV = null;
    Mat mDstUsmall = null;
    Mat mDstVsmall = null;

    byte[] yDst = null;
    byte[] uDst = null;
    byte[] vDst = null;

    private DualFisheyeStitcher()
    {
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV Loaded");
        }

        yMap = new Mat();
        xMap = new Mat();

        mSrcU = new Mat();
        mSrcV = new Mat();
        mDstYUV = new Mat();
        mDstUsmall = new Mat();
        mDstVsmall = new Mat();

        RM_inv = Mat.eye(3,3, CV_64F);
        phiMax = Math.PI / 2.0;
    };

    public void setConfig(double cx, double cy, double cx2, double cy2, double rmax, int viewWidth, int viewHeight)
    {
        this.cx = cx;
        this.cy = cy;
        this.cx2 = cx2;
        this.cy2 = cy2;
        this.rmax = rmax;

        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;

        xMap = new Mat(viewHeight, viewWidth, CV_32FC1);
        yMap = new Mat(viewHeight, viewWidth, CV_32FC1);

        yaw_radiansPerPixel = 2 * Math.PI / (float) viewWidth;
        phi_radiansPerPixel = Math.PI / (float) viewHeight;
    }

    private Mat getRx(double a)
    {
        Mat R = new Mat(3, 3, CV_64F);
        R.put(0, 0, 1);
        R.put(0, 1, 0);
        R.put(0, 2, 0);
        R.put(1, 0, 0);
        R.put(1, 1, Math.cos(a));
        R.put(1, 2, -Math.sin(a));
        R.put(2, 0, 0);
        R.put(2, 1, Math.sin(a));
        R.put(2, 2, Math.cos(a));
        return R;
    }

    private Mat getRy(double a)
    {
        Mat R = new Mat(3, 3, CV_64F);
        R.put(0, 0, Math.cos(a));
        R.put(0, 1, 0);
        R.put(0, 2, Math.sin(a));
        R.put(1, 0, 0);
        R.put(1, 1, 1);
        R.put(1, 2, 0);
        R.put(2, 0, -Math.sin(a));
        R.put(2, 1, 0);
        R.put(2, 2, Math.cos(a));
        return R;
    }

    private Mat getRz(double a)
    {
        Mat R = new Mat(3, 3, CV_64F);
        R.put(0, 0, Math.cos(a));
        R.put(0, 1, -Math.sin(a));
        R.put(0, 2, 0);
        R.put(1, 0, Math.sin(a));
        R.put(1, 1, Math.cos(a));
        R.put(1, 2, 0);
        R.put(2, 0, 0);
        R.put(2, 1, 0);
        R.put(2, 2, 1);
        return R;
    }

    private Mat RzRxRz(double a1, double a2, double a3)
    {
        Mat m1 = getRz(a1);
        Mat m2 = getRx(a2);
        Mat m3 = getRz(a3);
        Mat m4 = new Mat();

        Core.gemm(m1, m2, 1, m3, 0, m4);
        return m4;
   
        //return getRz(a1).mul(getRx(a2).mul(getRz(a3)));
    }

    private Mat getRot_Euler_ZXZ(double yaw, double phi, double roll)
    {
        double a1 = yaw - Math.PI / 2;
        double a2 = -phi;
        double a3 = roll;
        Mat R = RzRxRz(a1, a2, a3);
        return R;


    }

    public void setRotation(double yaw, double phi, double roll)
    {
        RM_inv = getRot_Euler_ZXZ(yaw, phi, roll).t();
        String s = RM_inv.dump();
        haveMap = false;
    }


    public void generateMapAsync() {
        new Thread(new Runnable() {
            public void run() {
                generateMap();
            }
        }).start();
    }

    public void generateMap()
    {
        haveMap = false;
        Mat I = new Mat();
        Mat p1 = new Mat(3, 1, CV_64F);
        Mat p2 = new Mat(3, 1, CV_64F);
        double[] d;

        try {
            for (int i = 0; i < viewWidth; i++) {
                for (int j = 0; j < viewHeight; j++) {
                    //BEGIN transform_yawphi_to_fisheye(viewWidth - i, j, fx, fy);
                    double ix, iy, x, y, z, yaw, phi, fx, fy;

                    ix = i - viewWidth / 2;
                    iy = j;
                    yaw = -ix * yaw_radiansPerPixel; // Modified sign to fix mirror-image problem.
                    phi = +iy * phi_radiansPerPixel;

                    // BEGIN rotate_yaw_phi(yaw, phi, yaw, phi);
                    //  BEGIN sph_yaw_phi_r_to_xyz(yaw, phi, 1, x, y, z);
                    double r = 1;
                    z = r * Math.cos(phi);  // height of the point.
                    double rxy = r * Math.sin(phi); // distance of the point from z axis;
                    x = rxy * Math.cos(yaw);
                    y = rxy * Math.sin(yaw);
                    //  END sph_yaw_phi_r_to_xyz(yaw, phi, 1, x, y, z);

                    p1.put(0, 0, x);
                    p1.put(1, 0, y);
                    p1.put(2, 0, z);

                    Core.gemm(RM_inv, p1, 1, I, 0, p2);

                    d = p2.get(0, 0);
                    x = d[0];
                    d = p2.get(1, 0);
                    y = d[0];
                    d = p2.get(2, 0);
                    z = d[0];

                    //  END xyz_to_r_yaw_phi(x, y, z, r, yaw2, phi2);
                    r = Math.sqrt(x * x + y * y + z * z);
                    yaw = Math.atan2(y, x);
                    if (r == 0) {
                        phi = 0;
                    } else {
                        phi = Math.acos(z / r);
                    }
                    //  END xyz_to_r_yaw_phi(x, y, z, r, yaw2, pfinal_mathi2); Array
                    // END rotate_yaw_phi(yaw, phi, yaw, phi);

                    // BEGIN yaw_phi_to_ixy(yaw, phi, x, y);
                    if (phi < 0) {
                        phi = -phi;
                        yaw += Math.PI;
                    }
                    // BEGIN yaw_phi_to_ixy(yaw, phi, x, y);
                    if (phi > Math.PI / 2) { // back lens
                        //yaw += fisheyeBackRot;
                        //double r = phi_to_r(M_PI - phi);
                        r = (Math.PI - phi) * rmax / phiMax;
                        //double r = phi_to_r(M_PI - phi);

                        ix = cx2 - cx - r * Math.sin(yaw); // * fisheyeAspect;
                        iy = r * Math.cos(yaw) + cy2 - cy;
                    } else {  // front lens
                        // BEGIN double r = phi_to_r(phi);
                        r = phi * rmax / phiMax;
                        // END double r = phi_to_r(phi);

                        ix = r * Math.sin(yaw); // * fisheyeAspect;
                        iy = r * Math.cos(yaw);
                    }
                    // END yaw_phi_to_ixy(yaw, phi, x, y);

                    // BEGIN xy_to_ij(x, y, fx, fy);
                    fx = ix + cx;
                    fy = iy + cy;
                    // END xy_to_ij(x, y, fx, fy);
                    //END transform_yawphi_to_fisheye(viewWidth - i, j, fx, fy);

                    xMap.put(j, i, fx);
                    yMap.put(j, i, fy);
                }
            }
            haveMap = true;
        } catch (Exception e) {
            Logger logger = Logger.getAnonymousLogger();
            logger.log(Level.SEVERE, "an exception was thrown", e);
        }
    }

    public void stitch(Mat src, Mat dst)
    {
        if (!haveMap) {
            return;
        }

        Scalar color = new Scalar(0, 100, 0);
        Imgproc.remap(src, dst, xMap, yMap, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, color);

        //imwrite("/sdcard/src.jpg", src);
        //imwrite("/sdcard/dst.jpg", dst);
    }

    public void stitch(int width, int height, byte[] src, byte[] dst)
    {

        if (!haveMap) {
            System.arraycopy(src, 0, dst, 0, src.length);
            return;
        }

         // Create a YUV image from the I420sp.   This format is a plane height*width
        // of Y, followed by a plane of interleaved V and U, sub-sampled by 2 in each
        // direction.

        if (mSrcY == null)
            mSrcY = new Mat(height, width, CV_8UC1);

        if (mSrcUsmall == null)
            mSrcUsmall = new Mat(height/2, width/2, CV_8UC1);

        if (mSrcVsmall == null)
            mSrcVsmall = new Mat(height/2, width/2, CV_8UC1);

        ySrc = Arrays.copyOf(src, height*width);
        mSrcY.put(0, 0, ySrc);

        if (uSrc == null)
            uSrc = new byte[height*width/4];

        if (vSrc == null)
            vSrc = new byte[height*width/4];

        int start = height * width;
        for (int i=0; i<vSrc.length; i++) {
            vSrc[i] = src[start + i * 2];
            uSrc[i] = src[start + i * 2 + 1];
        }

        mSrcUsmall.put(0, 0, uSrc);
        Imgproc.resize(mSrcUsmall, mSrcU, new Size(width, height));


        mSrcVsmall.put(0, 0, vSrc);
        Imgproc.resize(mSrcVsmall, mSrcV, new Size(width, height));

        if (mSrcYUV == null)
            mSrcYUV = new Mat();

        List<Mat> srcChannels = Arrays.asList(mSrcY, mSrcU, mSrcV);
        Core.merge(srcChannels, mSrcYUV);

        // Process the YUV image

        stitch(mSrcYUV, mDstYUV);

        //Create I420 image from the YUV

        ArrayList<Mat> dstChanels = new ArrayList<Mat>();
        Core.split(mDstYUV, dstChanels);

        if (yDst == null)
            yDst = new byte[viewHeight*viewWidth];

        dstChanels.get(0).get(0, 0, yDst);

        if (uDst == null)
            uDst = new byte[viewHeight*viewWidth/4];

        Imgproc.resize(dstChanels.get(1), mDstUsmall, new Size(viewWidth/2, viewHeight/2));
        mDstUsmall.get(0, 0, uDst);

        if (vDst == null)
            vDst = new byte[viewHeight*viewWidth/4];

        Imgproc.resize(dstChanels.get(2), mDstVsmall, new Size(viewWidth/2, viewHeight/2));
        mDstVsmall.get(0, 0, vDst);

        System.arraycopy(yDst, 0, dst, 0, yDst.length);

        for (int i=0; i<vDst.length; i++) {
            dst[start + 2*i] = vDst[i];
            dst[start + 2*i + 1] = uDst[i];
        }
    }
}
