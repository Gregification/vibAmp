package com.vibrationAmplifyer.greg.vibAmp;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import nu.pattern.OpenCV;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
    	OpenCV.loadLocally();;
        Mat mat = Mat.eye(3, 3, CvType.CV_8UC1);
        System.out.println("mat = " + mat.dump());
    }
}
