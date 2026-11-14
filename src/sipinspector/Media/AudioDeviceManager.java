/*
 *  SIP Inspector - Live Audio Support v3
 *  Audio Device Management
 */
package sipinspector.Media;

import javax.sound.sampled.*;
import java.util.*;
import sipinspector.GlobalSettings;

/**
 * Manages audio input/output devices for live audio support.
 * Enumerates available microphones and speakers, provides device selection
 * and configuration for 8kHz mono 16-bit audio.
 * 
 * @author SIP Simulator v3
 */
public class AudioDeviceManager {
    
    private static AudioDevice selectedInputDevice = null;
    private static AudioDevice selectedOutputDevice = null;
    private static List<AudioDevice> inputDevices = new ArrayList<>();
    private static List<AudioDevice> outputDevices = new ArrayList<>();
    
    // Audio format: 8kHz, mono, 16-bit signed
    private static final float SAMPLE_RATE = 8000f;
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = true;
    
    /**
     * Audio device information holder
     */
    public static class AudioDevice {
        public String name;
        public Mixer.Info mixerInfo;
        public boolean isInput;
        
        public AudioDevice(String name, Mixer.Info mixerInfo, boolean isInput) {
            this.name = name;
            this.mixerInfo = mixerInfo;
            this.isInput = isInput;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    /**
     * Initialize and enumerate all available audio devices
     */
    public static void initialize() {
        inputDevices.clear();
        outputDevices.clear();
        
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        
        for (Mixer.Info mixerInfo : mixers) {
            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                String name = mixerInfo.getName();
                
                // Check for input lines (microphones)
                Line.Info inputLineInfo = new Line.Info(TargetDataLine.class);
                if (mixer.isLineSupported(inputLineInfo)) {
                    inputDevices.add(new AudioDevice(name, mixerInfo, true));
                }
                
                // Check for output lines (speakers)
                Line.Info outputLineInfo = new Line.Info(SourceDataLine.class);
                if (mixer.isLineSupported(outputLineInfo)) {
                    outputDevices.add(new AudioDevice(name, mixerInfo, false));
                }
            } catch (Exception e) {
                // Skip devices that cause errors
            }
        }
        
        // Select defaults
        if (!inputDevices.isEmpty()) {
            selectedInputDevice = inputDevices.get(0);
        }
        if (!outputDevices.isEmpty()) {
            selectedOutputDevice = outputDevices.get(0);
        }
    }

    /**
     * Initialize devices and apply persisted selection from GlobalSettings if present.
     */
    public static void initializeFromGlobalSettings() {
        initialize();
        String micName = GlobalSettings.getSelectedMicDevice();
        String spkName = GlobalSettings.getSelectedSpeakerDevice();
        if (micName != null && micName.trim().length() > 0) {
            AudioDevice found = findByName(inputDevices, micName.trim());
            if (found != null) {
                selectedInputDevice = found;
            }
        }
        if (spkName != null && spkName.trim().length() > 0) {
            AudioDevice found = findByName(outputDevices, spkName.trim());
            if (found != null) {
                selectedOutputDevice = found;
            }
        }
    }
    
    /**
     * Get list of available input devices (microphones)
     * 
     * @return List of available input devices
     */
    public static List<AudioDevice> getInputDevices() {
        if (inputDevices.isEmpty()) {
            initialize();
        }
        return new ArrayList<>(inputDevices);
    }
    
    /**
     * Get list of available output devices (speakers)
     * 
     * @return List of available output devices
     */
    public static List<AudioDevice> getOutputDevices() {
        if (outputDevices.isEmpty()) {
            initialize();
        }
        return new ArrayList<>(outputDevices);
    }
    
    /**
     * Set the input device (microphone)
     * 
     * @param device Audio device to use for input
     */
    public static void setInputDevice(AudioDevice device) {
        selectedInputDevice = device;
        if (device != null) {
            GlobalSettings.setSelectedMicDevice(device.name);
            GlobalSettings.saveSettings();
        }
    }
    
    /**
     * Get currently selected input device
     * 
     * @return Selected input device
     */
    public static AudioDevice getInputDevice() {
        if (selectedInputDevice == null && !inputDevices.isEmpty()) {
            selectedInputDevice = inputDevices.get(0);
        }
        return selectedInputDevice;
    }
    
    /**
     * Set the output device (speakers)
     * 
     * @param device Audio device to use for output
     */
    public static void setOutputDevice(AudioDevice device) {
        selectedOutputDevice = device;
        if (device != null) {
            GlobalSettings.setSelectedSpeakerDevice(device.name);
            GlobalSettings.saveSettings();
        }
    }
    
    /**
     * Get currently selected output device
     * 
     * @return Selected output device
     */
    public static AudioDevice getOutputDevice() {
        if (selectedOutputDevice == null && !outputDevices.isEmpty()) {
            selectedOutputDevice = outputDevices.get(0);
        }
        return selectedOutputDevice;
    }
    
    /**
     * Get the audio format used for live audio (8kHz, mono, 16-bit)
     * 
     * @return AudioFormat for live audio
     */
    public static AudioFormat getAudioFormat() {
        return getAudioFormat((int) SAMPLE_RATE);
    }

    /**
     * Get an audio format at a specific sample rate (mono, 16-bit, big-endian).
     * Live audio uses 8000 Hz for G.711 (PCMA/PCMU) and 16000 Hz for G.722.
     */
    public static AudioFormat getAudioFormat(int sampleRateHz) {
        return new AudioFormat(
            sampleRateHz,
            BITS_PER_SAMPLE,  // 16-bit
            CHANNELS,         // mono
            SIGNED,           // signed
            BIG_ENDIAN        // big-endian byte order
        );
    }
    
    /**
     * Get sample rate for live audio
     * 
     * @return Sample rate (8000 Hz)
     */
    public static float getSampleRate() {
        return SAMPLE_RATE;
    }
    
    /**
     * Get channels for live audio
     * 
     * @return Number of channels (1 for mono)
     */
    public static int getChannels() {
        return CHANNELS;
    }
    
    /**
     * Get bits per sample for live audio
     * 
     * @return Bits per sample (16)
     */
    public static int getBitsPerSample() {
        return BITS_PER_SAMPLE;
    }
    
    /**
     * Get buffer size for audio line (in bytes).
     *
     * NOTE: AudioFormat.getFrameSize() for 16-bit mono is the SAMPLE-frame size
     * = 2 bytes, NOT a 20ms RTP packet (320 bytes). The old code did
     * frameSize*160 = 320 bytes = a single 20ms frame of slack, so any scheduler
     * hiccup > 20ms (routine on Windows) underran the speaker / overran the mic,
     * producing choppy/clicking audio. Size the buffer from the actual byte rate
     * instead: ~200ms of audio, which absorbs normal jitter without adding
     * noticeable latency.
     *
     * @return Buffer size in bytes
     */
    public static int getBufferSize() {
        return getBufferSize((int) SAMPLE_RATE);
    }

    /** Line buffer (~200 ms) sized from the actual byte rate at the given sample rate. */
    public static int getBufferSize(int sampleRateHz) {
        int bytesPerSecond = sampleRateHz * CHANNELS * (BITS_PER_SAMPLE / 8);
        return bytesPerSecond / 5; // ~200ms of audio
    }
    
    /**
     * Open a microphone input line from the selected input device
     * 
     * @return TargetDataLine for microphone input
     * @throws LineUnavailableException if line cannot be opened
     */
    public static TargetDataLine openMicrophoneInput() throws LineUnavailableException {
        return openMicrophoneInput((int) SAMPLE_RATE);
    }

    /**
     * Open a microphone input line at a specific sample rate (16000 for G.722).
     */
    public static TargetDataLine openMicrophoneInput(int sampleRateHz) throws LineUnavailableException {
        AudioFormat format = getAudioFormat(sampleRateHz);
        AudioDevice device = getInputDevice();

        if (device != null && device.mixerInfo != null) {
            try {
                Mixer mixer = AudioSystem.getMixer(device.mixerInfo);
                Line.Info[] lineInfos = mixer.getTargetLineInfo();
                for (Line.Info info : lineInfos) {
                    if (info.getLineClass() == TargetDataLine.class) {
                        TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                        line.open(format, getBufferSize(sampleRateHz));
                        return line;
                    }
                }
            } catch (Exception e) {
                // Fall back to AudioSystem default TargetDataLine
            }
        }

        // Fallback to standard AudioSystem default input line
        DataLine.Info fallbackInfo = new DataLine.Info(TargetDataLine.class, format);
        if (AudioSystem.isLineSupported(fallbackInfo)) {
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(fallbackInfo);
            line.open(format, getBufferSize(sampleRateHz));
            return line;
        }

        throw new LineUnavailableException("Microphone input line not available for " + sampleRateHz + "Hz");
    }
    
    /**
     * Open a speaker output line from the selected output device
     * 
     * @return SourceDataLine for speaker output
     * @throws LineUnavailableException if line cannot be opened
     */
    public static SourceDataLine openSpeakerOutput() throws LineUnavailableException {
        return openSpeakerOutput((int) SAMPLE_RATE);
    }

    /**
     * Open a speaker output line at a specific sample rate (16000 for G.722).
     */
    public static SourceDataLine openSpeakerOutput(int sampleRateHz) throws LineUnavailableException {
        AudioFormat format = getAudioFormat(sampleRateHz);
        AudioDevice device = getOutputDevice();

        if (device != null && device.mixerInfo != null) {
            try {
                Mixer mixer = AudioSystem.getMixer(device.mixerInfo);
                Line.Info[] lineInfos = mixer.getSourceLineInfo();
                for (Line.Info info : lineInfos) {
                    if (info.getLineClass() == SourceDataLine.class) {
                        SourceDataLine line = (SourceDataLine) mixer.getLine(info);
                        line.open(format, getBufferSize(sampleRateHz));
                        return line;
                    }
                }
            } catch (Exception e) {
                // Fall back to AudioSystem default SourceDataLine
            }
        }

        // Fallback to standard AudioSystem default output line
        DataLine.Info fallbackInfo = new DataLine.Info(SourceDataLine.class, format);
        if (AudioSystem.isLineSupported(fallbackInfo)) {
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(fallbackInfo);
            line.open(format, getBufferSize(sampleRateHz));
            return line;
        }

        throw new LineUnavailableException("Speaker output line not available for " + sampleRateHz + "Hz");
    }

    private static AudioDevice findByName(List<AudioDevice> list, String name) {
        for (AudioDevice d : list) {
            if (d != null && d.name != null && d.name.equals(name)) {
                return d;
            }
        }
        return null;
    }

    /**
     * Select active microphone input device by friendly name.
     */
    public static boolean selectInputDeviceByName(String name) {
        if (name == null) return false;
        AudioDevice d = findByName(getInputDevices(), name);
        if (d != null) {
            setInputDevice(d);
            return true;
        }
        return false;
    }

    /**
     * Select active speaker output device by friendly name.
     */
    public static boolean selectOutputDeviceByName(String name) {
        if (name == null) return false;
        AudioDevice d = findByName(getOutputDevices(), name);
        if (d != null) {
            setOutputDevice(d);
            return true;
        }
        return false;
    }
    
    /**
     * Ensure devices are initialized
     */
    static {
        initialize();
    }
}
