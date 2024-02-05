package com.vibrationAmplifyer.greg.vibAmp;

public enum Pages {
	primary,
	operatingUI	("vibAmpUI")
	;
	
	public final String sceneRoot;
	
	private Pages() { 
		this.sceneRoot = this.name();
	}
	private Pages(String sceneRoot) {
		this.sceneRoot = sceneRoot;
	}
	
	@Override public String toString() {
		return sceneRoot;
	}
}
