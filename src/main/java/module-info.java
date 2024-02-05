module com.vibrationAmplifyer.greg.vibAmp {
    requires transitive javafx.controls;
    requires javafx.fxml;
	requires opencv;//it'll live
	requires java.desktop;

    opens com.vibrationAmplifyer.greg.vibAmp to javafx.fxml;
    exports com.vibrationAmplifyer.greg.vibAmp;
}
