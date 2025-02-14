package edu.skku.cs.visualvroom.dto;

public class AudioData {
    private byte[] leftChannel;
    private byte[] rightChannel;
    private byte[] rearChannel;
    private int sampleRate;

    public AudioData(byte[] leftChannel, byte[] rightChannel, byte[] rearChannel, int sampleRate) {
        this.leftChannel = leftChannel;
        this.rightChannel = rightChannel;
        this.rearChannel = rearChannel;
        this.sampleRate = sampleRate;
    }

    // Getters and setters
    public byte[] getLeftChannel() { return leftChannel; }
    public void setLeftChannel(byte[] leftChannel) { this.leftChannel = leftChannel; }
    public byte[] getRightChannel() { return rightChannel; }
    public void setRightChannel(byte[] rightChannel) { this.rightChannel = rightChannel; }
    public byte[] getRearChannel() { return rearChannel; }
    public void setRearChannel(byte[] rearChannel) { this.rearChannel = rearChannel; }
    public int getSampleRate() { return sampleRate; }
    public void setSampleRate(int sampleRate) { this.sampleRate = sampleRate; }

}
