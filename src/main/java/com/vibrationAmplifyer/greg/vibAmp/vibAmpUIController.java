package com.vibrationAmplifyer.greg.vibAmp;

import java.io.IOException;

import org.opencv.videoio.VideoCapture;

import javafx.fxml.FXML;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;

public class vibAmpUIController {
	/**
	 * fps ranges users can set to
	 */
	public static int 
		minFps = 1,
		maxFps = 5000;
	
	public Thread host;
	public VideoCapture capture;
	
	@FXML
	private ToggleGroup radio_captureSource;
	@FXML
	private ImageView primaryImage;
	@FXML
	private Spinner<Integer> fps_spinner;
	
    @FXML
    private void switchToMenu() throws IOException {
        App.setRoot(Pages.primary.toString());
    }
    
    @FXML
    private void quit() {
    	System.exit(0);
    }
    
    @FXML
    private void startCapture() {
    	
    }
    
    /**
     * stops video capture if its running. 
     * @return true if capture is not running
     */
    @FXML
    public void stopCapture() {
    	
    }
    
    @FXML
    private void importVideo() {
    	
    }
    
    @FXML
    private void exportVideo() {
    	
    }
    
    private int getFps() {
    	return Math.clamp((long)fps_spinner.getValue(), 1, 2);
    }
    
    /**
     * init gui listeners and such
     */
    public void setup() {
    	
    }
}