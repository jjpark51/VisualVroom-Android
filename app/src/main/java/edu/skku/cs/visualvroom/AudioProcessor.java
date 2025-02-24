package edu.skku.cs.visualvroom;

import android.util.Log;

import org.jtransforms.fft.FloatFFT_1D;

import java.util.Arrays;

public class AudioProcessor {
    private static final String TAG = "AudioProcessor";

    // Audio processing constants (matching inference.py)
    private static final int SAMPLE_RATE = 16000;
    private static final int N_FFT = 402;
    private static final int HOP_LENGTH = 201;
    private static final int N_MFCC = 13;

    // Image dimensions (matching inference.py)
    private static final int SPEC_WIDTH = 241;
    private static final int SPEC_HEIGHT = 201;
    private static final int MFCC_WIDTH = 241;
    private static final int MFCC_HEIGHT = 13;
    private static final int FINAL_HEIGHT = 428;

    private final FloatFFT_1D fft;
    private final float[] hannWindow;

    public AudioProcessor() {
        this.fft = new FloatFFT_1D(N_FFT);
        this.hannWindow = createHannWindow(N_FFT);
    }

    private float[] createHannWindow(int size) {
        float[] window = new float[size];
        for (int i = 0; i < size; i++) {
            window[i] = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / (size - 1))));
        }
        return window;
    }

    public byte[] processAudioChannels(short[] leftChannel, short[] rightChannel) {
        try {
            // Convert shorts to float arrays (-1 to 1)
            float[] leftFloat = normalizeAudio(leftChannel);
            float[] rightFloat = normalizeAudio(rightChannel);

            // Generate spectrograms
            float[][] leftSpec = generateSpectrogram(leftFloat);
            float[][] rightSpec = generateSpectrogram(rightFloat);

            // Convert to dB scale
            leftSpec = amplitudeToDb(leftSpec);
            rightSpec = amplitudeToDb(rightSpec);

            // Generate MFCCs
            float[][] leftMFCC = generateMFCC(leftFloat);
            float[][] rightMFCC = generateMFCC(rightFloat);

            // Resize features to match inference.py dimensions
            leftSpec = resizeFeature(leftSpec, SPEC_WIDTH, SPEC_HEIGHT);
            rightSpec = resizeFeature(rightSpec, SPEC_WIDTH, SPEC_HEIGHT);
            leftMFCC = resizeFeature(leftMFCC, MFCC_WIDTH, MFCC_HEIGHT);
            rightMFCC = resizeFeature(rightMFCC, MFCC_WIDTH, MFCC_HEIGHT);

            // Combine all features into single image array
            return combineFeatures(leftMFCC, leftSpec, rightMFCC, rightSpec);
        } catch (Exception e) {
            Log.e(TAG, "Error processing audio channels: " + e.getMessage());
            throw e;
        }
    }

    private float[] normalizeAudio(short[] audio) {
        float[] normalized = new float[audio.length];
        float maxShort = 32768.0f;

        for (int i = 0; i < audio.length; i++) {
            normalized[i] = audio[i] / maxShort;
        }

        return normalized;
    }

    private float[][] generateSpectrogram(float[] audio) {
        int frames = 1 + (audio.length - N_FFT) / HOP_LENGTH;
        float[][] spectrogram = new float[frames][N_FFT / 2 + 1];
        float[] buffer = new float[N_FFT * 2]; // Real + Imaginary parts

        for (int frame = 0; frame < frames; frame++) {
            int start = frame * HOP_LENGTH;

            // Apply Hann window and prepare FFT buffer
            Arrays.fill(buffer, 0);
            for (int i = 0; i < N_FFT && (start + i) < audio.length; i++) {
                buffer[i] = audio[start + i] * hannWindow[i];
            }

            // Compute FFT
            fft.realForward(buffer);

            // Compute magnitude
            for (int i = 0; i < N_FFT / 2 + 1; i++) {
                if (i == 0 || i == N_FFT / 2) {
                    spectrogram[frame][i] = Math.abs(buffer[i]);
                } else {
                    float real = buffer[2 * i];
                    float imag = buffer[2 * i + 1];
                    spectrogram[frame][i] = (float) Math.sqrt(real * real + imag * imag);
                }
            }
        }

        return spectrogram;
    }

    private float[][] amplitudeToDb(float[][] spec) {
        float[][] db = new float[spec.length][spec[0].length];
        float maxVal = Float.MIN_VALUE;

        // Find maximum value
        for (float[] row : spec) {
            for (float val : row) {
                maxVal = Math.max(maxVal, val);
            }
        }

        // Convert to dB scale
        float ref = maxVal;
        for (int i = 0; i < spec.length; i++) {
            for (int j = 0; j < spec[0].length; j++) {
                float val = spec[i][j];
                if (val < 1e-10) val = 1e-10f;
                db[i][j] = (float) (20 * Math.log10(val / ref));
            }
        }

        return db;
    }

    private float[][] generateMFCC(float[] audio) {
        // Generate mel filterbank
        float[][] melFilters = createMelFilterbank();

        // Get spectrogram
        float[][] spec = generateSpectrogram(audio);

        // Apply mel filterbank
        float[][] melSpec = new float[spec.length][N_MFCC];
        for (int i = 0; i < spec.length; i++) {
            for (int j = 0; j < N_MFCC; j++) {
                float sum = 0;
                for (int k = 0; k < spec[0].length; k++) {
                    sum += spec[i][k] * melFilters[j][k];
                }
                melSpec[i][j] = sum;
            }
        }

        // Convert to dB scale and DCT
        return dct(amplitudeToDb(melSpec));
    }

    private float[][] createMelFilterbank() {
        // Create mel filterbank matrix (N_MFCC x (N_FFT/2 + 1))
        float[][] filters = new float[N_MFCC][N_FFT/2 + 1];

        // Convert Hz to mel scale
        float minMel = hzToMel(0);
        float maxMel = hzToMel(SAMPLE_RATE/2);

        // Create N_MFCC + 2 points evenly spaced in mel scale
        float[] melPoints = new float[N_MFCC + 2];
        for (int i = 0; i < melPoints.length; i++) {
            melPoints[i] = minMel + i * (maxMel - minMel) / (N_MFCC + 1);
        }

        // Convert back to Hz
        float[] hzPoints = new float[melPoints.length];
        for (int i = 0; i < melPoints.length; i++) {
            hzPoints[i] = melToHz(melPoints[i]);
        }

        // Convert to FFT bins
        int[] bins = new int[melPoints.length];
        for (int i = 0; i < melPoints.length; i++) {
            bins[i] = Math.round(hzPoints[i] * N_FFT / SAMPLE_RATE);
        }

        // Create triangular filters
        for (int i = 0; i < N_MFCC; i++) {
            for (int j = bins[i]; j < bins[i+2]; j++) {
                if (j < bins[i+1]) {
                    filters[i][j] = (j - bins[i]) / (float)(bins[i+1] - bins[i]);
                } else {
                    filters[i][j] = (bins[i+2] - j) / (float)(bins[i+2] - bins[i+1]);
                }
            }
        }

        return filters;
    }

    private float hzToMel(float hz) {
        return (float) (2595 * Math.log10(1 + hz/700));
    }

    private float melToHz(float mel) {
        return (float) (700 * (Math.pow(10, mel/2595) - 1));
    }

    private float[][] dct(float[][] melSpec) {
        float[][] dct = new float[melSpec.length][N_MFCC];

        for (int i = 0; i < melSpec.length; i++) {
            for (int j = 0; j < N_MFCC; j++) {
                float sum = 0;
                for (int k = 0; k < melSpec[0].length; k++) {
                    sum += melSpec[i][k] * Math.cos(Math.PI * j * (2 * k + 1) / (2 * melSpec[0].length));
                }
                dct[i][j] = sum;
            }
        }

        return dct;
    }

    private float[][] resizeFeature(float[][] feature, int targetWidth, int targetHeight) {
        float[][] resized = new float[targetHeight][targetWidth];

        float scaleX = (float) feature[0].length / targetWidth;
        float scaleY = (float) feature.length / targetHeight;

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                float srcX = x * scaleX;
                float srcY = y * scaleY;

                int x0 = (int) srcX;
                int x1 = Math.min(x0 + 1, feature[0].length - 1);
                int y0 = (int) srcY;
                int y1 = Math.min(y0 + 1, feature.length - 1);

                float xWeight = srcX - x0;
                float yWeight = srcY - y0;

                resized[y][x] =
                        feature[y0][x0] * (1 - xWeight) * (1 - yWeight) +
                                feature[y0][x1] * xWeight * (1 - yWeight) +
                                feature[y1][x0] * (1 - xWeight) * yWeight +
                                feature[y1][x1] * xWeight * yWeight;
            }
        }

        return resized;
    }

    private byte[] combineFeatures(float[][] leftMFCC, float[][] leftSpec,
                                   float[][] rightMFCC, float[][] rightSpec) {
        byte[] combined = new byte[SPEC_WIDTH * FINAL_HEIGHT];
        int index = 0;

        // Normalize all features to 0-255 range
        leftMFCC = normalizeFeature(leftMFCC);
        leftSpec = normalizeFeature(leftSpec);
        rightMFCC = normalizeFeature(rightMFCC);
        rightSpec = normalizeFeature(rightSpec);

        // Copy in exact order matching inference.py
        // 1. Top MFCC
        for (int y = 0; y < MFCC_HEIGHT; y++) {
            for (int x = 0; x < MFCC_WIDTH; x++) {
                combined[index++] = (byte) leftMFCC[y][x];
            }
        }

        // 2. Top spectrogram
        for (int y = 0; y < SPEC_HEIGHT; y++) {
            for (int x = 0; x < SPEC_WIDTH; x++) {
                combined[index++] = (byte) leftSpec[y][x];
            }
        }

        // 3. Bottom MFCC
        for (int y = 0; y < MFCC_HEIGHT; y++) {
            for (int x = 0; x < MFCC_WIDTH; x++) {
                combined[index++] = (byte) rightMFCC[y][x];
            }
        }

        // 4. Bottom spectrogram
        for (int y = 0; y < SPEC_HEIGHT; y++) {
            for (int x = 0; x < SPEC_WIDTH; x++) {
                combined[index++] = (byte) rightSpec[y][x];
            }
        }

        return combined;
    }

    private float[][] normalizeFeature(float[][] feature) {
        float[][] normalized = new float[feature.length][feature[0].length];
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;

        // Find min and max
        for (float[] row : feature) {
            for (float val : row) {
                min = Math.min(min, val);
                max = Math.max(max, val);
            }
        }

        // Normalize to 0-255
        float range = max - min;
        for (int i = 0; i < feature.length; i++) {
            for (int j = 0; j < feature[0].length; j++) {
                normalized[i][j] = 255 * (feature[i][j] - min) / range;
            }
        }

        return normalized;
    }
}