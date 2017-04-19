package com.janusmobile.WebRTCModule;

import android.util.Log;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.util.Arrays;
import static org.opencv.core.CvType.CV_32F;
import static org.opencv.core.CvType.CV_32FC1;
import static org.opencv.core.CvType.CV_64F;
import static org.opencv.core.CvType.CV_64FC1;
import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.core.CvType.CV_8UC3;
import java.util.ArrayList;
import java.util.List;
import java.util.List.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 Convert dual fisheye image to equirectangular.
 */

public class Stitcher {

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
    private boolean needMap = true;

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

    public Stitcher(double cx, double cy, double cx2, double cy2, double rmax, int viewWidth, int viewHeight) {

        //System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        if (OpenCVLoader.initDebug()) {
            // do some opencv stuff
            Log.d(TAG, "OpenCV Loaded");
        }

        yMap = new Mat();
        xMap = new Mat();

        mSrcU = new Mat();
        mSrcV = new Mat();
        mDstYUV = new Mat();
        mDstUsmall = new Mat();
        mDstVsmall = new Mat();

        RM_inv = new Mat();
        RM_inv = Mat.eye(3,3, CV_64F);

        this.cx = cx;
        this.cy = cy;
        this.cx2 = cx2;
        this.cy2 = cy2;
        this.rmax = rmax;

        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;

        phiMax = Math.PI / 2.0;
        //fisheyeAspect = 1.0;
        //fisheyeBackRot = 0.0;
        needMap = true;

        xMap = new Mat(viewHeight, viewWidth, CV_32FC1);
        yMap = new Mat(viewHeight, viewWidth, CV_32FC1);

        yaw_radiansPerPixel = 2 * Math.PI / (float) viewWidth;
        phi_radiansPerPixel = Math.PI / (float) viewHeight;
    }

    Mat getRx(double a)
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

    Mat getRy(double a)
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

    Mat getRz(double a)
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

    Mat RzRxRz(double a1, double a2, double a3)
    {
        return getRz(a1).mul(getRx(a2).mul(getRz(a3)));
    }

    Mat getRot_Euler_ZXZ(double yaw, double phi, double roll)
    {
        double a1 = yaw - Math.PI / 2;
        double a2 = -phi;
        double a3 = roll;
        Mat R = RzRxRz(a1, a2, a3);
        return R;
    }

    void setRotation(double yaw, double phi, double roll)
    {
        RM_inv = getRot_Euler_ZXZ(yaw, phi, roll).t();
        needMap = true;
    }

    void generateMap()
    {
        for (int i=0; i<viewHeight; i++)
        {
            for (int j=0; j<viewWidth; j++) {
                //BEGIN transform_yawphi_to_fisheye(viewWidth - i, j, fx, fy);
                double ix, iy, x, y, z, yaw, phi, fx, fy;

                ix = i - viewWidth / 2;
                iy = j;
                yaw = -ix * yaw_radiansPerPixel; // Modified sign to fix mirror-image problem.
                phi = +iy * phi_radiansPerPixel;

                // BEGIN rotate_yaw_phi(yaw, phi, yaw, phi);

                //  BEGIN sph_yaw_phi_r_to_xyz(yaw, phi, 1, x, y, z);
                double r = 1;
                z = r * Math.cos(phi);          // height of the point.
                double rxy = r * Math.sin(phi); // distance of the point from z axis;
                x = rxy * Math.cos(yaw);
                y = rxy * Math.sin(yaw);
                //  END sph_yaw_phi_r_to_xyz(yaw, phi, 1, x, y, z);

                Mat p1 = new Mat(1, 3, CV_32FC1);
                p1.put(0, 0, x);
                p1.put(0, 1, y);
                p1.put(0, 2, z);
                Mat p2 = new Mat();
                p2 = RM_inv.mul(p1);
                double[] d;
                d = p2.get(0, 0);
                x = d[0];
                d = p2.get(0, 1);
                y = d[0];
                d = p2.get(0, 2);
                z = d[0];

                //  END xyz_to_r_yaw_phi(x, y, z, r, yaw2, phi2);
                r = Math.sqrt(x * x + y * y + z * z);
                yaw = Math.atan2(y, x);
                if (r == 0) {
                    phi = 0;
                }
                else {
                    phi = Math.acos(z / r);
                }
                //  END xyz_to_r_yaw_phi(x, y, z, r, yaw2, phi2); Array

                // END rotate_yaw_phi(yaw, phi, yaw, phi);
                // BEGIN yaw_phi_to_ixy(yaw, phi, x, y);
                if (phi < 0) {
                    phi = -phi;
                    yaw += Math.PI;
                }
                // BEGIN yaw_phi_to_ixy(yaw, phi, x, y);
                if (phi > Math.PI / 2) {
                    //yaw += fisheyeBackRot;
                    //double r = phi_to_r(M_PI - phi);
                    r = (Math.PI - phi) * rmax / phiMax;
                    //double r = phi_to_r(M_PI - phi);        //mSrcYUV = new Mat();


                    ix = cx2 - cx - r * Math.sin(yaw); // * fisheyeAspect;
                    iy = r * Math.cos(yaw) + cy2 - cy;
               }
                else {
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
        needMap = false;
    }

    void stitch(Mat src, Mat dst)
    {
        if (needMap) {
            generateMap();
        }

        Scalar color = new Scalar(0, 100, 0);
        Imgproc.remap(src, dst, xMap, yMap, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, color);
    }

    void stitch(int width, int height, byte[] src, byte[] dst)
    {

        /*
        Create a YUV image from the I420
         */

        ySrc = Arrays.copyOf(src, height*width);
        int start = height * width;
        uSrc = Arrays.copyOfRange(src, start, start + (height * width)/ 4);
        start += (height * width) / 4;
        vSrc = Arrays.copyOfRange(src, start, start + (height * width)/ 4);

        if (mSrcY == null)
            mSrcY = new Mat(height, width, CV_8UC1);

        mSrcY.put(0, 0, ySrc);

        if (mSrcUsmall == null)
            mSrcUsmall = new Mat(height/2, width/2, CV_8UC1);

        mSrcUsmall.put(0, 0, uSrc);
        Imgproc.resize(mSrcUsmall, mSrcU, new Size(width, height));

        if (mSrcVsmall == null)
            mSrcVsmall = new Mat(height/2, width/2, CV_8UC1);

        mSrcVsmall.put(0, 0, vSrc);
        Imgproc.resize(mSrcVsmall, mSrcV, new Size(width, height));

        if (mSrcYUV == null)
            mSrcYUV = new Mat();

        List<Mat> srcChannels = Arrays.asList(mSrcY, mSrcU, mSrcV);
        Core.merge(srcChannels, mSrcYUV);

        /*
        Process the YUV image
         */
        stitch(mSrcYUV, mDstYUV);

        /*
         Create I420 image from the YUV
         */
        ArrayList<Mat> dstChanels = new ArrayList<Mat>();
        Core.split(mDstYUV, dstChanels);

        if (yDst == null)
            yDst = new byte[height*width];

        dstChanels.get(0).get(0, 0, yDst);

        if (uDst == null)
            uDst = new byte[height*width/4];

        Imgproc.resize(dstChanels.get(1), mDstUsmall, new Size(height/2, width/2));
        mDstUsmall.get(0, 0, uDst);

        if (vDst == null)
            vDst = new byte[height*width/4];

        Imgproc.resize(dstChanels.get(2), mDstVsmall, new Size(height/2, width/2));
        mDstVsmall.get(0, 0, vDst);

        start = 0;
        System.arraycopy(yDst, 0, dst, start, yDst.length);
        start += yDst.length;
        System.arraycopy(uDst, 0, dst, start, uDst.length);
        start += uDst.length;
        System.arraycopy(vDst, 0, dst, start, vDst.length);

        Logger logger = Logger.getAnonymousLogger();
        logger.log(Level.SEVERE, String.format("Lengths Y %d U %d V %d", yDst.length, uDst.length, vDst.length));
    }
}
