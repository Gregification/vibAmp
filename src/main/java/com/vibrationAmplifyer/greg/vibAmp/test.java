package com.vibrationAmplifyer.greg.vibAmp;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.util.Pair;

public class test{
	public void run() {
		Platform.runLater(() -> {
			source_status_circle.setFill(Color.GREEN);
		});
		
		//camera warm up buffer time
		try { Thread.sleep(100);
		} catch (InterruptedException e) { JOptionPane.showMessageDialog(null, "process appears to have shutdown early"); }
		
		Mat 
			frame 		= new Mat(),
			freqImg 	= new Mat(),
			freqMagImg 	= new Mat(),
			contureImg 	= new Mat(),
			imagiImg	= new Mat(),
			freqMask 	= null;
		
		capture.read(frame);
		int addPixelRows = Core.getOptimalDFTSize(frame.rows());
		int addPixelCols = Core.getOptimalDFTSize(frame.cols());
//		freqImg = new Mat(Core.getOptimalDFTSize(frame.rows()), Core.getOptimalDFTSize(frame.cols()), CvType.CV_32F);
		
		final List<Pair<Mat, ImageView>> displayMap = List.of(
				new Pair<>(frame, primaryImage),
				new Pair<>(freqMagImg, frequencyImage),
				new Pair<>(imagiImg, imaginaryImg),
				new Pair<>(contureImg, contureImage));
		
		List<Mat> complexfreqImgLayers = new ArrayList<>(2);
		
		while(!Thread.currentThread().isInterrupted() && capture.isOpened() && capture.read(frame)) {
			Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2GRAY);
			
			//pad & copy image 
			Core.copyMakeBorder(frame, freqImg,
					0, addPixelRows - frame.rows(),
					0, addPixelCols - frame.cols(),
					Core.BORDER_CONSTANT,
					Scalar.all(0));
			
			//DFT
			{
				//add extra dimension
				freqImg.convertTo(freqImg, CvType.CV_32F);
				Core.merge(List.of(freqImg, Mat.zeros(freqImg.size(), CvType.CV_32F)), freqImg);
				
				Core.dft(freqImg, freqImg, Core.DFT_COMPLEX_OUTPUT);
				
				Core.split(freqImg, complexfreqImgLayers);
			
			}
			
			//get display of mask
			{
				//combine real and imaginary
				Core.magnitude(complexfreqImgLayers.get(0), complexfreqImgLayers.get(1), freqMagImg);
				
				//scale down
				Core.add(Mat.ones(freqImg.size(), CvType.CV_32F), freqMagImg, freqMagImg);
				Core.log(freqMagImg, freqMagImg);
//				Core.multiply(freqImg, new Scalar(20), freqMagImg);
				Core.normalize(freqMagImg, freqMagImg, 0, 255, Core.NORM_MINMAX);
			}
			
			//apply mask to actual and display images
			{
				if(maskChanged || freqMask == null || !freqMask.size().equals(freqImg.size())) {
					freqMask = makeMask(freqImg.size());
					maskChanged = false;
				}
				
				for(Mat mat : new Mat[]{freqMagImg, freqImg}) {
					if(centerDFTMask) mirrorDTFMat(mat);
					
					mat.setTo(new Scalar(0), freqMask);
					
					if(centerDFTMask) mirrorDTFMat(mat);
					
					Imgproc.threshold(mat, mat, DFTMask_min, DFTMask_max * 255, Imgproc.THRESH_TRUNC);
				}
			}
			
			//inverse DFT
			{
				Core.idft(freqImg, freqImg, Core.DFT_COMPLEX_INPUT);
				Core.split(freqImg, complexfreqImgLayers);
				Core.normalize(complexfreqImgLayers.get(0), contureImg, 0, 255, Core.NORM_MINMAX);
				Core.normalize(complexfreqImgLayers.get(1), imagiImg, 0, 255, Core.NORM_MINMAX);
			}
			
			//display images
			for(var pair : displayMap) {
				MatOfByte buffer = new MatOfByte();
				Imgcodecs.imencode(".png", pair.getKey(), buffer); 
				Image display = new Image(new ByteArrayInputStream(buffer.toArray()));
				Platform.runLater(() -> pair.getValue().setImage(display));
			}
			
			//testing
			try { Thread.sleep(50);//20fps
			} catch (InterruptedException e) {}
		}
		
		Platform.runLater(() -> {
			source_status_circle.setFill(Color.RED);
		});
//	}
}
