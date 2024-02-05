module com.vibrationAmplifyer.greg.vibAmp {
    requires javafx.controls;
    requires javafx.fxml;
	requires opencv;

    opens com.vibrationAmplifyer.greg.vibAmp to javafx.fxml;
    exports com.vibrationAmplifyer.greg.vibAmp;
}
