package com.vibrationAmplifyer.greg.vibAmp;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javafx.fxml.FXML;

public class PrimaryController {
    
    @FXML
    public void switchToApp() throws IOException {
    	App.setRoot(Pages.operatingUI.toString());
    }
    
    @FXML
    public void openAbout() {
    	if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
    	    try {
				Desktop.getDesktop().browse(new URI("https://github.com/Gregification/vibAmp"));
			} catch (IOException | URISyntaxException e) {
				e.printStackTrace();
			}
    	}
    }
}
