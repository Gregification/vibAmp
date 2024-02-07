package com.vibrationAmplifyer.greg.vibAmp;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.swing.JOptionPane;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public class VibAmpController implements Runnable{
	public static final int //arbitrarly defined
		minTargetHz = 1,		
		maxTargetHz = 1000000;
	/**
	 * what the program is to believe the fps is, this is not necessarly hte fps of the video source
	 */
	int targetHz;
	
	Thread workThread = new Thread(this);
	
	public VideoCapture capture;
	
	@FXML
	private ToggleGroup captureSource_radio;
	@FXML
	private ImageView primaryImage;
	@FXML
	private Spinner<Integer> fps_spinner;
	@FXML
	private TextField captureSource_text;
	@FXML
	private Circle source_status_circle;
	@FXML
	private Label sourceInfo;
	
	@Override public void run() {
		Mat frame = new Mat();
		MatOfByte buffer = new MatOfByte();
		
		Platform.runLater(() -> {
			source_status_circle.setFill(Color.GREEN);
		});
		
		//camera warm up buffer
		try { Thread.sleep(100);
		} catch (InterruptedException e) { 
			JOptionPane.showMessageDialog(null, "process appears to have shutdown early. this may be a issue with the hardware");
		}
		
		System.out.println("staring process");
		while(!Thread.currentThread().isInterrupted() && capture.isOpened() && capture.read(frame)) {
			
			Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2GRAY);
			
			//display image
			Imgcodecs.imencode(".png", frame, buffer);
			Image display = new Image(new ByteArrayInputStream(buffer.toArray()));
			Platform.runLater(() -> {
				primaryImage.setImage(display);
			});
		}
		System.out.println("process has ended");
		
		Platform.runLater(() -> {
			source_status_circle.setFill(Color.RED);
		});
	}
	
    @FXML
    private void switchToMenu() throws IOException {
        App.setRoot(Pages.primary.toString());
    }
    
    @FXML
    private void quit() {
    	if(capture != null) capture.release();
    	
    	System.exit(0);
    }
    
    @FXML
    private void startCapture() {
    	System.out.println("start capture : start");
    	
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
	   	
	   	if(capture.isOpened()) {
//	   		int sourceFps = (int)capture.get(Videoio.CAP_PROP_FPS);
	   		
//	   		System.out.println("start capture : capture is opened, fps:" + sourceFps);
//	   		setTargetHz(sourceFps);
	   	}
	   	System.out.println("start capture : end");
	   	
	   	if(!workThread.isAlive())
	   		workThread.start();
    }
    
    /**
     * stops video capture if its running. 
     * @return true if capture is not running
     */
    @FXML
    public void stopCapture() {
    	workThread.interrupt();
    	if(capture != null) capture.release();
    }
    
    @FXML
    private void exportVideo() {
    	
    }
    
    /**
     * init gui listeners and such
     */
    public void setup() {
    	//prepare openCV
        
        capture = new VideoCapture();
        capture.release();
        
    	setTargetHz(30);
    	
    	captureSource_radio.selectedToggleProperty().addListener(new ChangeListener<Toggle>(){
    	    public void changed(ObservableValue<? extends Toggle> ov,
    	            Toggle old_toggle, Toggle new_toggle) {
	    	    	startCapture();
    	    	}
    	    });
    	
    	source_status_circle.setFill(Color.ORANGE);
    }
    
    public int getTargetHz() {
    	return targetHz;
    }
    
    public float setTargetHz(int targetHz) {
    	this.targetHz = targetHz;
    	
    	return getTargetHz();
    }
}