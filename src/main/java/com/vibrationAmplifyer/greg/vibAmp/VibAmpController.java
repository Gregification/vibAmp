package com.vibrationAmplifyer.greg.vibAmp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Pair;

public class VibAmpController implements Runnable{
	public static final int //arbitrarly defined
		minTargetHz = 1,		
		maxTargetHz = 1000000;
	/**
	 * what the program is to believe the fps is, this is not necessarly hte fps of the video source
	 */
	int targetHz;
	
	volatile float 
		effHz = targetHz;
	volatile boolean useInverseDFTMask = false;
	Thread workThread = new Thread(this);
	
	public VideoCapture capture;
	
	@FXML
	private ToggleGroup 
		captureSource_radio,
		DFTMaskVert_radio,
		DFTMaskHorz_radio;
	@FXML
	private ImageView
		primaryImage,
		frequencyImage,
		finalImage;
	@FXML
	private Slider 
		targetHzScaler,
		DFTMaskSlider;
	@FXML
	private Spinner<Integer> targetHzRaw_spinner;
	@FXML
	private TextField 
		captureSource_text,
		o_effectiveHz_text;
	@FXML
	private Circle source_status_circle;
	@FXML
	private Label sourceInfo;
	
	@Override public void run() {
		Platform.runLater(() -> {
			source_status_circle.setFill(Color.GREEN);
		});
		
		//camera warm up buffer time
		try { Thread.sleep(100);
		} catch (InterruptedException e) { JOptionPane.showMessageDialog(null, "process appears to have shutdown early"); }
		
		Mat 
			frame = new Mat(),
			freqImg = new Mat();
		final List<Pair<Mat, ImageView>> displayMap = List.of(
				new Pair<>(frame, primaryImage),
				new Pair<>(freqImg, frequencyImage));
		
		List<Mat> complexfreqImgLayers = new ArrayList<>(2);
		
		while(!Thread.currentThread().isInterrupted() && capture.isOpened() && capture.read(frame)) {
			Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2GRAY);
			
			//apply DFT
			
			//pad image for better preformance
			int addPixelRows = Core.getOptimalDFTSize(frame.rows());
			int addPixelCols = Core.getOptimalDFTSize(frame.cols());
			Core.copyMakeBorder(frame, freqImg,
					0, addPixelRows - frame.rows(),
					0, addPixelCols - frame.cols(),
					Core.BORDER_CONSTANT,
					Scalar.all(0));
			
			//add extra dimension
			freqImg.convertTo(freqImg, CvType.CV_32F);
			Core.merge(List.of(freqImg, Mat.zeros(freqImg.size(), CvType.CV_32F)), freqImg);
			
			//apply and get result
			
			if(useInverseDFTMask) {
				Core.idft(freqImg, freqImg);
				Core.split(freqImg, complexfreqImgLayers);
				
				Core.normalize(complexfreqImgLayers.get(0), freqImg, 0, 255, Core.NORM_MINMAX);
			}else {
				Core.dft(freqImg, freqImg);
				Core.split(freqImg, complexfreqImgLayers);
				//combine real and imaginary
				Core.magnitude(complexfreqImgLayers.get(0), complexfreqImgLayers.get(1), freqImg);
				
				//scale to something reasonable to display
				Core.add(Mat.ones(freqImg.size(), CvType.CV_32F), freqImg, freqImg);
				Core.log(freqImg, freqImg);
				
				//rearrange quadrants
				freqImg.submat(new Rect(0,0, freqImg.cols() & -2, freqImg.rows() & -2));
				int cx = freqImg.cols() / 2;
				int cy = freqImg.rows() / 2;
	
				Mat q0 = new Mat(freqImg, new Rect(0, 0, cx, cy));
				Mat q1 = new Mat(freqImg, new Rect(cx, 0, cx, cy));
				Mat q2 = new Mat(freqImg, new Rect(0, cy, cx, cy));
				Mat q3 = new Mat(freqImg, new Rect(cx, cy, cx, cy));
	
				Mat tmp = new Mat();
				q0.copyTo(tmp);
				q3.copyTo(q0);
				tmp.copyTo(q3);
	
				q1.copyTo(tmp);
				q2.copyTo(q1);
				tmp.copyTo(q2);
				
				Core.normalize(freqImg, freqImg, 0, 255, Core.NORM_MINMAX);
			}

			//display images
			for(var pair : displayMap) {
				MatOfByte buffer = new MatOfByte();
				Imgcodecs.imencode(".png", pair.getKey(), buffer);
				Image display = new Image(new ByteArrayInputStream(buffer.toArray()));
				Platform.runLater(() -> {
					pair.getValue().setImage(display);
				});
			}			
		}
		
		Platform.runLater(() -> {
			source_status_circle.setFill(Color.RED);
		});
	}
	
    @FXML
    private void switchToMenu() throws IOException {
    	stopCapture();
        App.setRoot(Pages.primary.toString());
    }
    
    @FXML
    private void quit() {
    	stopCapture();
    	
    	System.exit(0);
    }
    
    @FXML
    private void startCapture() {
    	if(captureSource_radio.getSelectedToggle().toString().contains("camera")) { //not the best but this project isn't that complex	    	    		 
	   		 
    		int camNum;
				try { camNum = Integer.parseInt(captureSource_text.getText());
				} catch (NumberFormatException e) { camNum = 0; }
	   		sourceInfo.setText("camera#: " + camNum);
	   		
	   		capture.open(camNum);
	   	}
	   	else {
	   		var text = captureSource_text.getText();
	   		sourceInfo.setText("path: " + text);
	   		
	   		capture.open(text);
	   	}
	   	
	   	workThread.interrupt();
	   	workThread = new Thread(this);
	   	workThread.start();
    }
    
    /**
     * stops video capture if its running. 
     * @return true if capture is not running
     */
    @FXML
    public void stopCapture() {
    	workThread.interrupt();
    	capture.release();
    	capture = new VideoCapture();
    }
    
    @FXML
    private void exportVideo() {
    	
    }
    
    /**
     * init gui listeners and such
     */
    public void initialize() {
    	//prepare openCV
        
        capture = new VideoCapture();
        capture.release();
        
    	setHz(30);
    	
    	captureSource_radio.selectedToggleProperty().addListener(new ChangeListener<Toggle>(){
    	    public void changed(ObservableValue<? extends Toggle> ov,
    	            Toggle old_toggle, Toggle new_toggle) {
	    	    	startCapture();
    	    	}
    	    });
    	
    	DFTMaskVert_radio.selectedToggleProperty().addListener(new ChangeListener<Toggle>(){
    	    public void changed(ObservableValue<? extends Toggle> ov,
    	            Toggle old_toggle, Toggle new_toggle) {
	    	    	
    	    	}
    	    });
    	
    	targetHzRaw_spinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            setHz(newValue);
        });
    	targetHzRaw_spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
    			1,						//min
    			Integer.MAX_VALUE));	//max
    	
    	targetHzScaler.valueProperty().addListener((observable, oldValue, newValue) -> {
           effHz = newValue.floatValue() * getHz();
           updateEffectiveHz();
        });
    	
    	source_status_circle.setFill(Color.ORANGE);
    }
    
    public int getHz() {
    	return targetHz;
    }
    
    private void updateEffectiveHz() {
    	o_effectiveHz_text.setText(effHz + "");
    }
    
    @FXML
    public float setHz(int targetHz) {
    	this.targetHz = targetHz;
    	updateEffectiveHz();
    	
    	return getHz();
    }
    
    @FXML
    private void toggleInverseDFTMask() {
    	useInverseDFTMask = !useInverseDFTMask;
    }
}