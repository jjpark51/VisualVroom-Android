package edu.skku.cs.visualvroom.dto;

public class PredictionResponse {
    private String vehicleType;
    private String direction;
    private double confidence;
    private boolean shouldNotify;

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public boolean isShouldNotify() { return shouldNotify; }
    public void setShouldNotify(boolean shouldNotify) { this.shouldNotify = shouldNotify; }
}
