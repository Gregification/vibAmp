package com.vibrationAmplifyer.greg.vibAmp;

import java.io.IOException;

import org.opencv.core.Core;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * vibAmp JavaFX root
 */
public class App extends Application {
    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        scene = new Scene(loadFXML("primary"), 640, 480);
        stage.setScene(scene);
        stage.show();
    }
    
    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        
        Parent ret = fxmlLoader.load();
        
        return ret;
    }

    public static void main(String[] args) {
    	//link OpenCV libraries
    	System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    	
    	//prepare JFX and module    	
        launch();
    }

}