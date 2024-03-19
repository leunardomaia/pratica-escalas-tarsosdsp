package com.tc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;

public class PitchDetectorCLI implements PitchDetectionHandler {

    private AudioDispatcher dispatcher;
    private Mixer currentMixer;
    private PitchEstimationAlgorithm algo;

    private static final float PRECISION = 0.03f;
    private static final Map<String, Float> noteFrequencies = new HashMap<>();

    static {
        noteFrequencies.put("C2", 65.41f);
        noteFrequencies.put("D2", 73.42f);
        noteFrequencies.put("E2", 82.41f);
        noteFrequencies.put("F2", 87.31f);
        noteFrequencies.put("G2", 98.00f);
        noteFrequencies.put("A2", 110.00f);
        noteFrequencies.put("B2", 123.47f);
        noteFrequencies.put("C3", 130.81f);
        noteFrequencies.put("D3", 146.83f);
        noteFrequencies.put("E3", 164.81f);
        noteFrequencies.put("F3", 174.61f);
        noteFrequencies.put("G3", 196.00f);
        noteFrequencies.put("A3", 220.00f);
        noteFrequencies.put("B3", 246.94f);
        noteFrequencies.put("C4", 261.63f);
        noteFrequencies.put("D4", 293.66f);
        noteFrequencies.put("E4", 329.63f);
        noteFrequencies.put("F4", 349.23f);
        noteFrequencies.put("G4", 392.00f);
        noteFrequencies.put("A4", 440.00f);
        noteFrequencies.put("B4", 493.88f);
        noteFrequencies.put("C5", 523.25f);
        noteFrequencies.put("D5", 587.33f);
        noteFrequencies.put("E5", 659.25f);
        noteFrequencies.put("F5", 698.46f);
        noteFrequencies.put("G5", 783.99f);
        noteFrequencies.put("A5", 880.00f);
        noteFrequencies.put("B5", 987.77f);
        noteFrequencies.put("C6", 1046.50f);
    }

    // public PitchDetectorCLI() {
    // algo = PitchEstimationAlgorithm.FFT_PITCH;
    // }

    public static void main(String[] args) {
        PitchDetectorCLI pitchDetectorCLI = new PitchDetectorCLI();
        pitchDetectorCLI.start();
    }

    private void start() {
        try {
            // Select Mixer
            Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
            System.out.println("Available Mixers:");
            for (int i = 0; i < mixerInfo.length; i++) {
                System.out.println(i + ": " + mixerInfo[i].getName());
            }
            int selectedMixerIndex = getUserInput("Select a Mixer by index: ");
            Mixer.Info selectedMixerInfo = mixerInfo[selectedMixerIndex];

            // Select Pitch Estimation Algorithm
            System.out.println("Available Pitch Estimation Algorithms:");
            PitchEstimationAlgorithm[] algorithms = PitchEstimationAlgorithm.values();
            for (int i = 0; i < algorithms.length; i++) {
                System.out.println(i + ": " + algorithms[i]);
            }
            int selectedAlgorithmIndex = getUserInput("Select a Pitch Estimation Algorithm by index: ");
            algo = algorithms[selectedAlgorithmIndex];
            System.out.println("Using " + algo + " for pitch detection.");

            setNewMixer(selectedMixerInfo);

        } catch (LineUnavailableException | IOException | UnsupportedAudioFileException e) {
            e.printStackTrace();
        }
    }

    private void setNewMixer(Mixer.Info mixerInfo)
            throws LineUnavailableException, IOException, UnsupportedAudioFileException {
        if (dispatcher != null) {
            dispatcher.stop();
        }
        currentMixer = AudioSystem.getMixer(mixerInfo);

        float sampleRate = 48000;
        int bufferSize = 16384;
        int overlap = 0;

        System.out.println("Started listening with " + mixerInfo.getName());

        final AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, true);
        final DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine line = (TargetDataLine) currentMixer.getLine(dataLineInfo);
        final int numberOfSamples = bufferSize;
        line.open(format, numberOfSamples);
        line.start();
        final AudioInputStream stream = new AudioInputStream(line);

        JVMAudioInputStream audioStream = new JVMAudioInputStream(stream);
        dispatcher = new AudioDispatcher(audioStream, bufferSize, overlap);
        dispatcher.addAudioProcessor(new PitchProcessor(algo, sampleRate, bufferSize, this));

        new Thread(dispatcher, "Audio dispatching").start();
    }

    private int getUserInput(String message) {
        int userInput = -1;
        while (userInput < 0) {
            try {
                System.out.print(message);
                userInput = Integer.parseInt(System.console().readLine());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }
        return userInput;
    }

    @Override
    public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
        if (pitchDetectionResult.getPitch() != -1 && audioEvent.getRMS() * 100 > 1.0) {
            double timeStamp = audioEvent.getTimeStamp();
            float pitch = pitchDetectionResult.getPitch();
            float probability = pitchDetectionResult.getProbability();
            double rms = audioEvent.getRMS() * 100;

            String detectedNote = detectNote(pitch);

            String message = String.format("Pitch detected at %.2fs: %.2fHz ( %.2f probability, RMS: %.5f )",
                    timeStamp, pitch, probability, rms);
            System.out.println(detectedNote + ". " + message);
        }
    }

    public static String detectNote(float pitch) {
        for (Map.Entry<String, Float> entry : noteFrequencies.entrySet()) {
            Float entryValue = entry.getValue();
            if (Math.abs(pitch - entryValue) < entryValue * PRECISION) {
                return "Right note: " + entry.getKey();
            }
        }

        return "Wrong note!";
    }

}