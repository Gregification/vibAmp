package com.vibrationAmplifyer.greg.vibAmp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JOptionPane;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
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
	
	static final int	//keep in this order
		DFTMask_N 	= 0,
		DFTMask_S 	= 1,
		DFTMask_E 	= 2,
		DFTMask_W 	= 3,
		DFTMask_NE 	= 4,
		DFTMask_NW 	= 5,
		DFTMask_SE 	= 6,
		DFTMask_SW 	= 7;
	volatile boolean 
		maskChanged = true,
		centerDFTMask = false;
	
	volatile float 
		effHz = targetHz;
	volatile float[] maskParamNormals = new float[8];
	volatile boolean invertDFTMask = false;
	Thread workThread = new Thread(this);
	
	public VideoCapture capture;
	
	@FXML
	private ToggleGroup captureSource_radio;
	@FXML
	private ChoiceBox<String> DFTMask_choiceBox;
	@FXML
	private ImageView
		primaryImage,
		frequencyImage,
		contureImage,
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
			frame 		= new Mat(),
			freqImg 	= new Mat(),
			contureImg 	= new Mat(),
			freqMask 	= null;
		
//		capture.read(frame);
//		freqImg = new Mat(Core.getOptimalDFTSize(frame.rows()), Core.getOptimalDFTSize(frame.cols()), CvType.CV_32F);
		
		final List<Pair<Mat, ImageView>> displayMap = List.of(
				new Pair<>(frame, primaryImage),
				new Pair<>(contureImg, contureImage),
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
			
//			frame.copyTo(freqImg.submat(0, frame.rows(), 0, frame.cols()));
			//add extra dimension
			freqImg.convertTo(freqImg, CvType.CV_32F);
			Core.merge(List.of(freqImg, Mat.zeros(freqImg.size(), CvType.CV_32F)), freqImg);
			
			//get DFT
			{
				Core.dft(freqImg, freqImg);
				Core.split(freqImg, complexfreqImgLayers);
				
				//combine real and imaginary
				Core.magnitude(complexfreqImgLayers.get(0), complexfreqImgLayers.get(1), freqImg);
				
				//scale to something reasonable to display
				Core.add(Mat.ones(freqImg.size(), CvType.CV_32F), freqImg, freqImg);
				Core.log(freqImg, freqImg);
				Core.normalize(freqImg, freqImg, 0, 255, Core.NORM_MINMAX);
			}
			
			//mask DFT
			{
				if(centerDFTMask) mirrorDTFMat(freqImg);
					
				if(maskChanged || freqMask == null || !freqMask.size().equals(freqImg.size())) {
					freqMask = makeMask(freqImg.size());
					maskChanged = false;
				}
				freqImg.setTo(new Scalar(0), freqMask);
				
				if(centerDFTMask) mirrorDTFMat(freqImg);
			}
			
			//reverse DFT
			{
				Core.idft(freqImg, contureImg);
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
	}
	
	private Mat makeMask(Size size) {
		//limits
		Mat mask;
		Scalar fillVal;
		
		if(invertDFTMask) {
			mask = Mat.ones(size, CvType.CV_8U);
			fillVal = new Scalar(0);
		} else {
			mask = Mat.zeros(size, CvType.CV_8U);
			fillVal = new Scalar(1);
		}
		
		final double wi = size.width, hi = size.height, w2 = wi/2, h2 = hi/2;
		final double cc = w2*w2 + h2*h2, c = Math.sqrt(cc), thetaRad = Math.atan(h2/w2),//for first quadrant
				sin = Math.sin(thetaRad), cos = Math.cos(thetaRad);
		final double //length of diagonal
//			nel = Math.sqrt(cc * maskParamNormals[DFTMask_NE]),
//			nwl = Math.sqrt(cc * maskParamNormals[DFTMask_NW]),
//			sel = Math.sqrt(cc * maskParamNormals[DFTMask_SE]),
//			swl = Math.sqrt(cc * maskParamNormals[DFTMask_SW]);
			nel = c * maskParamNormals[DFTMask_NE],
			nwl = c * maskParamNormals[DFTMask_NW],
			sel = c * maskParamNormals[DFTMask_SE],
			swl = c * maskParamNormals[DFTMask_SW];
		final Point//ocv point, not java library
			n = new Point(w2, 	(1 - maskParamNormals[DFTMask_N]) * h2),
			s = new Point(w2, 	maskParamNormals[DFTMask_S] * h2 + h2),
			e = new Point(maskParamNormals[DFTMask_E] * w2 + w2, 	h2),
			w = new Point((1 - maskParamNormals[DFTMask_W]) * w2, 	h2),
			ne = new Point(cos*nel + w2, h2 - sin*nel),
			nw = new Point(w2 - cos*nwl, h2 - sin*nwl),
			se = new Point(cos*sel + w2, sin*sel + h2),
			sw = new Point(w2 - cos*swl, sin*swl + h2),
			cen= new Point(w2, h2);//center point
		
		//there is no overlap between areas so concurrency is a option
		try(ExecutorService service = Executors.newVirtualThreadPerTaskExecutor()){
			//each quadrant has upper and lower fill area
			service.execute(() -> Imgproc.fillPoly(mask, List.of(new MatOfPoint(cen, n, ne)), fillVal)); //1 up
			service.execute(() -> Imgproc.fillPoly(mask, List.of(new MatOfPoint(cen, e, ne)), fillVal)); //1 down
			service.execute(() -> Imgproc.fillPoly(mask, List.of(new MatOfPoint(cen, n, nw)), fillVal)); //2 up
			service.execute(() -> Imgproc.fillPoly(mask, List.of(new MatOfPoint(cen, w, nw)), fillVal)); //2 down
			service.execute(() -> Imgproc.fillPoly(mask, List.of(new MatOfPoint(cen, w, sw)), fillVal)); //3 up
			service.execute(() -> Imgproc.fillPoly(mask, List.of(new MatOfPoint(cen, s, sw)), fillVal)); //3 down
			service.execute(() -> Imgproc.fillPoly(mask, List.of(new MatOfPoint(cen, e, se)), fillVal)); //4 up
			service.execute(() -> Imgproc.fillPoly(mask, List.of(new MatOfPoint(cen, s, se)), fillVal)); //4 down
		}
		
		return mask;
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
    	//debug
//    	workThread = new Thread(()->{
//    		while(!Thread.currentThread().isInterrupted()) {
//    			Mat mask = makeMask(new Size(30,40));
//    			System.out.println("\n\n\n\n\n\n" + mask.dump().replace(",", "").replace(";", "").replace("0", ".").replace("1", "@"));
//    			
//    			try { Thread.sleep(250);
//				} catch (InterruptedException e) { e.printStackTrace(); }
//    		}
//    	});
    	
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
		workThread = new Thread(this);//let interrupt clean it-slef up. go gc go
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
    
    //swaps the quadrants. running this twice will return the original mat
    private void mirrorDTFMat(Mat mat) {
    	mat.submat(new Rect(0,0, mat.cols() & -2, mat.rows() & -2));
		int cx = mat.cols() / 2;
		int cy = mat.rows() / 2;

		Mat q0 = new Mat(mat, new Rect(0, 0, cx, cy));
		Mat q1 = new Mat(mat, new Rect(cx, 0, cx, cy));
		Mat q2 = new Mat(mat, new Rect(0, cy, cx, cy));
		Mat q3 = new Mat(mat, new Rect(cx, cy, cx, cy));

		Mat tmp = new Mat();
		q0.copyTo(tmp);
		q3.copyTo(q0);
		tmp.copyTo(q3);

		q1.copyTo(tmp);
		q2.copyTo(q1);
		tmp.copyTo(q2);
    }
    
    /**
     * init gui listeners and such
     */
    public void initialize() {
    	//prepare openCV
        
        capture = new VideoCapture();
        capture.release();
        
    	setHz(30);
    	
    	DFTMask_choiceBox.itemsProperty().set(FXCollections.observableArrayList("N", "S", "E", "W", "NE", "NW", "SE", "SW", "circle", "square"));
    	DFTMask_choiceBox.onActionProperty().addListener((ob,o,n) -> onDFTMask_choiceBox_change());
    	DFTMaskSlider.valueProperty().addListener((obs, newVal, oldVal) -> onDFTMask_slider_change());
    	DFTMask_choiceBox.getSelectionModel().select(0);
    	
    	captureSource_radio.selectedToggleProperty().addListener(new ChangeListener<Toggle>(){
    	    public void changed(ObservableValue<? extends Toggle> ov,
    	            Toggle old_toggle, Toggle new_toggle) {
	    	    	startCapture();
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
    private void toggleInvertDFTMask() {
    	invertDFTMask = !invertDFTMask;
    	maskChanged = true;
    }
    
    @FXML
    private void toggleCenterDFTMask() {
    	centerDFTMask = !centerDFTMask;
    }
    
    @FXML
    private void onDFTMask_choiceBox_change() {
    	int idx = DFTMask_choiceBox.getSelectionModel().getSelectedIndex();
    	if(idx < maskParamNormals.length) {
    		float normal = maskParamNormals[idx];
    		DFTMaskSlider.setValue(normal * 100);
    	}
    }
    
    @FXML 
    private void onDFTMask_slider_change() {
    	float normal = (float)(DFTMaskSlider.getValue() / 100f);
    	int idx = DFTMask_choiceBox.getSelectionModel().getSelectedIndex();
    	if(idx < maskParamNormals.length) {
    		maskParamNormals[idx] = normal;
    	}else switch(idx - maskParamNormals.length) {
    		case 0 -> {//circle
    				//cannot make a proper circle without calculating form actual aspect ratio. assuming is 1:1
    				double r = Math.sqrt(2) / 2; //ratio
    				
    				for(int i = 0; i < maskParamNormals.length; i++) maskParamNormals[i]= normal;
    				
    				for(var i : new int[] {DFTMask_NE,DFTMask_NW,DFTMask_SE,DFTMask_SW})
    					maskParamNormals[i] *= normal;
    			}
    		case 1 -> {//square
    				for(int i = 0; i < maskParamNormals.length; i++)
    					maskParamNormals[i] = normal;
    			}
    		default -> throw new UnsupportedOperationException("unknown mask option index:" + idx + " effective:" + (maskParamNormals.length - idx) + " name:" + DFTMask_choiceBox.getSelectionModel().getSelectedItem());
    	}
    	
    	maskChanged = true;
    }
}