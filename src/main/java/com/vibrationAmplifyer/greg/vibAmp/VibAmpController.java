package com.vibrationAmplifyer.greg.vibAmp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

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
import javafx.util.Duration;

public class VibAmpController implements Runnable{
	/** layer count of gussian pyramid */
	volatile int PyrLayers = 3;
	
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
		centerDFTMask = false,
		convertToRGB = false,
		overlayOrgionalImage = false;
	
	volatile double
		DFTMask_min = 0,
		DFTMask_max = 1,
		FpAmp		= 1,
		FpAtt		= 0;
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
		Image1,
		Image2,
		Image3,
		Image4,
		Image5;
	@FXML
	private Slider 
		targetHzScaler,
		DFTMaskSlider,
		DFTMin_slider,
		DFTMax_slider,
		FpAmplification_slider,
		FpAttenuation_slider;
	@FXML
	private Spinner<Integer> targetHzRaw_spinner;
	@FXML
	private Spinner<Double> 
		FpAmplification_spinner,	
		FpAttenuation_spinner;
	@FXML
	private TextField 
		captureSource_text;
	@FXML
	private Circle source_status_circle;
	@FXML
	private Label sourceInfo,
		o_dftMin_text,
		o_dftMax_text,
		o_FpAmp_text,
		o_FpAtt_text;
	
	public void mainLoop() {
		Mat 
			raw 		= new Mat(),
			src			= new Mat(),
			dftMag		= new Mat(),	//magnitude
			dftHardMask = null;
		
		ArrayList<ArrayList<Mat>> pyrLayers = new ArrayList<>(PyrLayers);
		List<Mat> matBuffer = new ArrayList<>(3);
		
		//get capture dimensions
		capture.read(raw);
		int addPixelRows = Core.getOptimalDFTSize(raw.rows());
		int addPixelCols = Core.getOptimalDFTSize(raw.cols());
		
		BiConsumer<Mat, ImageView> drawImg = (mat, img) -> {
			MatOfByte buffer = new MatOfByte();
			Imgcodecs.imencode(".png", mat, buffer); 
			Image display = new Image(new ByteArrayInputStream(buffer.toArray()));
			Platform.runLater(() -> img.setImage(display));
		};
		
		while(!Thread.currentThread().isInterrupted() && capture.isOpened() && capture.read(raw)) {
			raw.convertTo(raw, CvType.CV_32F);
			drawImg.accept(raw, Image1);
			
			Imgproc.cvtColor(raw, raw, Imgproc.COLOR_BGR2YUV);//YIQ alternative
			raw.copyTo(src);
			
			//blurring using gaussian pyramids
			int p = PyrLayers;
			if(pyrLayers.size() != p) {
				for(int i = pyrLayers.size(); i < p; i++)
					pyrLayers.addLast(new ArrayList<Mat>(3));
				for(int i = pyrLayers.size(); i > p; i--)
					pyrLayers.removeLast();
			}
			
			for(int i = 0; i < p; i++) {
				pyrLayers.get(i).add(src);
				
				Imgproc.pyrDown(src, src, new Size( src.cols()/2, src.rows()/2));
			}
					
			for(int i = 0; i < p; i++)
				Imgproc.pyrUp(src, src, new Size( src.cols()*2, src.rows()*2));
			
			Core.copyMakeBorder(src, src,
					0, addPixelRows - src.rows(),
					0, addPixelCols - src.cols(),
					Core.BORDER_CONSTANT,
					Scalar.all(0));
			
			//update mask
			if(maskChanged || dftHardMask == null || !dftHardMask.size().equals(src.size())) {
				dftHardMask = makeMask(src.size());
				maskChanged = false;
			}
			
			//DFT and masks for each layer
			for(int L = 0; L < pyrLayers.size(); L++) {
				var out = pyrLayers.get(L);
				Core.split(src, out);
				
				Mat 
					matY = out.get(0),
					matU = out.get(1),
					matV = out.get(2);
				
				//DFT for all layers.
				for(Mat dftify : List.of(matY, matU, matV)) {					
					//give imaginary layer
					Core.merge(List.of(dftify, Mat.zeros(dftify.size(), CvType.CV_32F)), dftify);
					
					//magic
					Core.dft(dftify, dftify);
					
					//mask frequencies
					Core.split(dftify, matBuffer);
					Core.magnitude(matBuffer.get(0), matBuffer.get(1), dftMag);
					Core.log(dftMag, dftMag);
					Core.normalize(dftMag, dftMag,0, 255, Core.NORM_MINMAX);
					dftMag.convertTo(dftMag, CvType.CV_8UC1);
					Core.inRange(dftMag, new Scalar(DFTMask_min * 255), new Scalar(DFTMask_max * 255), dftMag);
					dftify.setTo(new Scalar(0), dftMag);
					
					//mask area
					dftify.setTo(new Scalar(0), dftHardMask);
					
					//show spectrum of dft full scale image
					if(L == 0 && dftify == matY) {
						dftMag.setTo(new Scalar(0), dftHardMask);
						drawImg.accept(dftMag, Image3);
					}
//					drawImg.accept(dftMag, 
//						  dftify == matU ? Image3 
//						: dftify == matY ? Image5
//						: Image4);
					
					Core.idft(dftify, dftify);
					Core.split(dftify, matBuffer);
					Core.normalize(matBuffer.get(0), dftify, 0, 255, Core.NORM_MINMAX);
					//amplification and attenuation
					Core.multiply(dftify, new Scalar(FpAmp * (dftify == matY ? 1 : FpAtt)), dftify);
					
					//sharpen range
					Core.inRange(dftify, new Scalar(DFTMask_min * 255), new Scalar(DFTMask_max * 255), dftify);
					
					//show spectrum of idft full scale image
					if(L == 0 && dftify == matY)
						drawImg.accept(dftify, Image4);
				}	
				
			}
			
			if(overlayOrgionalImage) {
				Core.split(raw, matBuffer);
				int i = 0;
				for(var v : pyrLayers.get(0)) {
					v.convertTo(v, CvType.CV_32F);
					var m = matBuffer.get(i++);
					
					Core.add(m,v,v);
				}
			}
			
			Core.merge(pyrLayers.get(0), src);
			
			if(convertToRGB)
				Imgproc.cvtColor(src, src, Imgproc.COLOR_YUV2BGR);
			
			drawImg.accept(src, Image2);
		}
	}
	
	/**
	 * makes a geometric mask
	 * @param size - width&height of screen
	 * @return a mat of the mask  
	 */
	private Mat makeMask(Size size) {
		//limits
		final Mat mask;
		final Scalar fillVal;
		
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
		
		//little overlap. ocv handles that
		//probably overkill, bench marked, no conclusive results. sometimes went faster, sometimes worse.
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
		
		if(centerDFTMask) mirrorDTFMat(mask);
		
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
    	stopCapture();
    	
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

		try {
			workThread.interrupt(); //stop capture should take care of this but just in case.
			workThread.join(200L);
		} catch (InterruptedException e) {
			
		}
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
    public void toggleCapture() {
    	if(capture.isOpened())
    		stopCapture();
    	else
    		startCapture();
    }
    
    @FXML
    private void exportVideo() {
    	
    }
    
    //swaps diagonal quadrants. running this twice will return the original mat
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
    	
    	DFTMask_choiceBox.itemsProperty().set(FXCollections.observableArrayList("N", "S", "E", "W", "NE", "NW", "SE", "SW", "circle", "square"));
    	DFTMask_choiceBox.onActionProperty().addListener((ob,o,n) -> onDFTMask_choiceBox_change());
    	DFTMaskSlider.valueProperty().addListener((obs, newVal, oldVal) -> onDFTMask_slider_change());
    	DFTMask_choiceBox.getSelectionModel().select(0);
    	
    	DFTMax_slider.valueProperty().addListener((obs, newVal, oldVal) -> onDFTMaskMaxChange_slider());
    		DFTMax_slider.setValue(100.0);
    	DFTMin_slider.valueProperty().addListener((obs, newVal, oldVal) -> onDFTMaskMinChange_slider());
    		DFTMin_slider.setValue(0.0);
    	captureSource_radio.selectedToggleProperty().addListener(new ChangeListener<Toggle>(){
    	    public void changed(ObservableValue<? extends Toggle> ov,
    	            Toggle old_toggle, Toggle new_toggle) {
	    	    	startCapture();
    	    	}
    	    });
    	
    	targetHzRaw_spinner.valueProperty().addListener((observable, oldValue, newValue) -> {
    		PyrLayers = newValue;
        });
    	targetHzRaw_spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
    			1,						//min
    			Integer.MAX_VALUE,
    			PyrLayers));	//max
    	
    	FpAmplification_slider.valueProperty().addListener((obs, newVal, oldVal) -> onFpAmpChange());
    		FpAmplification_slider.setValue(FpAmp * 100);
    	FpAmplification_spinner.valueProperty().addListener((pbs, newVal, oldVal) -> onFpAmpChange());
    	FpAmplification_spinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(
    			0,						//min
    			Integer.MAX_VALUE,
    			FpAmp));
    	
    	FpAttenuation_slider.valueProperty().addListener((obs, newVal, oldVal) -> onFpAttChange());
    		FpAttenuation_slider.setValue(FpAtt * 100);
    	FpAttenuation_spinner.valueProperty().addListener((pbs, newVal, oldVal) -> onFpAttChange());
    	FpAttenuation_spinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(
    			0,						//min
    			Integer.MAX_VALUE,
    			FpAtt));
    	
    	source_status_circle.setFill(Color.ORANGE);
    	
    	startCapture();
    }
    
    @FXML
    private void toggleInvertDFTMask() {
    	invertDFTMask = !invertDFTMask;
    	maskChanged = true;
    }
    
    @FXML
    private void toggleCenterDFTMask() {
    	centerDFTMask = !centerDFTMask;
    	maskChanged = true;
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
    private void toggleRGBReversion() {
    	convertToRGB = !convertToRGB; 
    }
    
    @FXML 
    private void onDFTMask_slider_change() {
    	float normal = (float)(DFTMaskSlider.getValue() / 100f);
    	int idx = DFTMask_choiceBox.getSelectionModel().getSelectedIndex();
    	if(idx < maskParamNormals.length) {
    		maskParamNormals[idx] = normal;
    	}else switch(idx - maskParamNormals.length) {
    		case 0 -> {//circle
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
    
    private void onDFTMaskMaxChange_slider() {
    	DFTMask_max = DFTMax_slider.getValue() / 100;
    	o_dftMax_text.setText(DFTMask_max + "");
    	maskChanged = true;
    }
    
    private void onDFTMaskMinChange_slider() { 
    	DFTMask_min = DFTMin_slider.getValue() / 100;
    	o_dftMin_text.setText(DFTMask_min + "");
    	maskChanged = true;
    }
    
    @FXML
    private void onFpAmpChange() {
    	FpAmp = FpAmplification_slider.getValue() / 100;
    	FpAmp *= FpAmplification_spinner.valueProperty().getValue();
    	o_FpAmp_text.setText(String.format("%.3f", FpAmp));	
    }
    
    @FXML
    private void onFpAttChange() {
    	FpAtt = FpAttenuation_slider.getValue() / 100;
    	FpAtt *= FpAttenuation_spinner.valueProperty().getValue();
    	o_FpAtt_text.setText(String.format("%.3f", FpAtt));
    }
    
    @FXML
    private void toggleOverlayOrgionalImage() {
    	overlayOrgionalImage = !overlayOrgionalImage;
    }
    
    @Override public void run() {
		Platform.runLater(() -> {
			source_status_circle.setFill(Color.GREEN);
		});
		
		//camera warm up buffer time
		try { Thread.sleep(100);
		} catch (InterruptedException e) { JOptionPane.showMessageDialog(null, "video capture process appears to have shutdown early"); }
		
		mainLoop();
		
		Platform.runLater(() -> {
			source_status_circle.setFill(Color.RED);
		});
	}
}