package com.janusmobile.WebRTCModule;

import android.util.Log;

import org.opencv.android.OpenCVLoader;

/**
     Convert dual fisheye image to equirectangular.
 */

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import static org.opencv.core.CvType.CV_32FC1;
import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.core.CvType.CV_8UC3;

public class Stitcher {

    private static final String TAG = "Stitcher";

    private Mat xMap;
    private Mat yMap;

    private int viewWidth = 1280;
    private int viewHeight = 720;

    private double rmax;
    private double phiMax;
    //private double yawMax;

    private double cx, cy, cx2, cy2;

    private double yaw_radiansPerPixel;
    private double phi_radiansPerPixel;

    private Mat RM_inv;

    private boolean needMap = true;

    Mat mSrcYUV = null;
    Mat mSrcRGB = null;
    Mat mDstYUV = null;
    Mat mDstRGB = null;

    public Stitcher(double cx, double cy, double cx2, double cy2, double rmax, int viewHeight, int viewWidth) {

        //System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        if (OpenCVLoader.initDebug()) {
            // do some opencv stuff
            Log.d(TAG, "OpenCV Loaded");
        }


        this.cx = cx;
        this.cy = cy;
        this.cx2 = cx2;
        this.cy2 = cy2;
        this.rmax = rmax;

        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;

        phiMax = Math.PI / 2.0;
        //yawMax = Math.PI;
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
        Mat R = new Mat(3, 3, CV_32FC1);
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
        Mat R = new Mat(3, 3, CV_32FC1);
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
        Mat R = new Mat(3, 3, CV_32FC1);
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
                /*
                Mat p1 = new Mat(1, 3, CV_32FC1);
                p1.put(0, 0, x);
                p1.put(1, 0, y);
                p1.put(2, 0, z);
                Mat p2 = RM_inv.mul(p1);
                double[] d;
                d = p2.get(0, 0);
                x = d[0];
                d = p2.get(0, 1);
                y = d[0];
                d = p2.get(0, 2);
                z = d[0];
*/
                //  END xyz_to_r_yaw_phi(x, y, z, r, yaw2, phi2);
                r = Math.sqrt(x * x + y * y + z * z);
                yaw = Math.atan2(y, x);
                if (r == 0) {
                    phi = 0;
                }
                else {
                    phi = Math.acos(z / r);
                }
                //  END xyz_to_r_yaw_phi(x, y, z, r, yaw2, phi2);

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
                    //double r = phi_to_r(M_PI - phi);

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

    void stitch(int height, int width, byte[] src, byte[] dst)
    {

        if (mSrcYUV == null) {
            mSrcYUV = new Mat(height + height / 2, width, CV_8UC1);
            mSrcRGB = new Mat(height, width, CV_8UC3);
            mDstRGB = new Mat(viewHeight, viewWidth, CV_8UC3);
            mDstYUV = new Mat(viewHeight + viewHeight / 2, viewWidth, CV_8UC1);
        }

        mSrcYUV.put(0, 0, src);
        Imgproc.cvtColor(mSrcYUV, mSrcRGB, Imgproc.COLOR_YUV2RGB_I420);
        stitch(mSrcRGB, mDstRGB);
        Imgproc.cvtColor(mDstRGB, mDstYUV, Imgproc.COLOR_RGB2YUV_I420);

//        System.arraycopy(mDstYUV.dataAddr(), 0, dst, 0, (height + height / 2) * width);
        mDstYUV.get(0, 0, dst);
    }
}
