package com.vibrationAmplifyer.greg.vibAmp;

import java.io.IOException;
import javafx.fxml.FXML;

public class SecondaryController {

    @FXML
    private void switchToPrimary() throws IOException {
        App.setRoot(Pages.primary.toString());
    }
}