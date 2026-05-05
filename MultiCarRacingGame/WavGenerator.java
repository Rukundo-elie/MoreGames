import java.io.*;
import java.util.Random;

public class WavGenerator {
    public static void main(String[] args) throws Exception {
        generateMusic();
        generateCrash();
        generateBoost();
        System.out.println("Generated all sounds in src/");
    }

    static void writeWav(String filename, byte[] buffer, int sampleRate) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(filename);
             DataOutputStream dos = new DataOutputStream(fos)) {
            dos.writeBytes("RIFF");
            dos.writeInt(Integer.reverseBytes(36 + buffer.length));
            dos.writeBytes("WAVE");
            dos.writeBytes("fmt ");
            dos.writeInt(Integer.reverseBytes(16));
            dos.writeShort(Short.reverseBytes((short) 1)); // PCM
            dos.writeShort(Short.reverseBytes((short) 1)); // Channels
            dos.writeInt(Integer.reverseBytes(sampleRate)); // Sample rate
            dos.writeInt(Integer.reverseBytes(sampleRate)); // Byte rate
            dos.writeShort(Short.reverseBytes((short) 1)); // Block align
            dos.writeShort(Short.reverseBytes((short) 8)); // Bits per sample
            dos.writeBytes("data");
            dos.writeInt(Integer.reverseBytes(buffer.length));
            dos.write(buffer);
        }
    }

    static void generateMusic() throws Exception {
        int sampleRate = 22050;
        int numSamples = (int) (8.0 * sampleRate);
        byte[] buffer = new byte[numSamples];
        int[] notes = {33, 33, 45, 33, 33, 43, 33, 45};
        double tempo = 8.0; 
        for (int i = 0; i < numSamples; i++) {
            double time = i / (double) sampleRate;
            int noteIdx = (int)(time * tempo) % notes.length;
            double freq = 440.0 * Math.pow(2.0, (notes[noteIdx] - 69) / 12.0);
            double period = sampleRate / freq;
            double val = (i % period) < (period / 2.0) ? 1.0 : -1.0;
            double noteTime = time * tempo - Math.floor(time * tempo);
            double volume = 0.25 * Math.max(0, 1.0 - noteTime * 1.5);
            int sampleVal = (int)(val * volume * 127 + 128);
            buffer[i] = (byte) sampleVal;
        }
        writeWav("src/music.wav", buffer, sampleRate);
    }

    static void generateCrash() throws Exception {
        int sampleRate = 22050;
        int numSamples = (int) (1.5 * sampleRate);
        byte[] buffer = new byte[numSamples];
        Random rand = new Random();
        for (int i = 0; i < numSamples; i++) {
            double time = i / (double) sampleRate;
            double volume = Math.max(0, 1.0 - time / 1.5); 
            double noise = rand.nextDouble() * 2 - 1; 
            int sampleVal = (int)(noise * volume * 100 + 128);
            buffer[i] = (byte) sampleVal;
        }
        writeWav("src/crash.wav", buffer, sampleRate);
    }

    static void generateBoost() throws Exception {
        int sampleRate = 22050;
        int numSamples = (int) (1.0 * sampleRate);
        byte[] buffer = new byte[numSamples];
        for (int i = 0; i < numSamples; i++) {
            double time = i / (double) sampleRate;
            double freq = 400.0 + time * 800.0; 
            double val = Math.sin(2 * Math.PI * time * freq);
            double volume = Math.max(0, 1.0 - time);
            int sampleVal = (int)(val * volume * 80 + 128);
            buffer[i] = (byte) sampleVal;
        }
        writeWav("src/boost.wav", buffer, sampleRate);
    }
}
