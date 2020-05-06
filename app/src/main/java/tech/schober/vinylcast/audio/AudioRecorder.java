package tech.schober.vinylcast.audio;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.IntDef;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.CopyOnWriteArrayList;

import tech.schober.vinylcast.utils.Helpers;

public class AudioRecorder implements AudioStreamProvider {

    private static final String TAG = "AudioRecorder";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({AUDIO_API_OPENSLES, AUDIO_API_AAUDIO})
    public @interface AudioApi {}
    public static final int AUDIO_API_OPENSLES = 1;
    public static final int AUDIO_API_AAUDIO = 2;

    protected int bufferSize;
    private CopyOnWriteArrayList<OutputStream> nativeAudioWriteStreams = new CopyOnWriteArrayList<>();

    public AudioRecorder(int bufferSize) {
        this.bufferSize = bufferSize;

        NativeAudioEngine.prepareRecording();
        Log.d(TAG, "Prepared to Record - sampleRate: " + NativeAudioEngine.getSampleRate() +", channel count: " + NativeAudioEngine.getChannelCount());
    }

    public void start() {
        Log.d(TAG, "start");

        // callback from NativeAudioEngine with audioData will end up on own thread
        NativeAudioEngine.setAudioDataListener(new NativeAudioEngineListener() {
            @Override
            public void onAudioData(byte[] audioData) {
                for (OutputStream writeStream : nativeAudioWriteStreams) {
                    try {
                        writeStream.write(audioData);
                        writeStream.flush();
                    } catch (IOException e) {
                        Log.w(TAG, "Exception writing to raw audio output stream. Removing from list of streams.", e);
                        nativeAudioWriteStreams.remove(writeStream);
                    }
                }

                if (nativeAudioWriteStreams.isEmpty()) {
                    Log.e(TAG, "No open write streams.");
                }
            }
        });

        NativeAudioEngine.startRecording();
    }

    public void stop() {
        Log.d(TAG, "stop");

        try {
            for (OutputStream writeStream : nativeAudioWriteStreams) {
                nativeAudioWriteStreams.remove(writeStream);
                writeStream.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception closing streams", e);
        }

        NativeAudioEngine.stopRecording();
    }

    @Override
    public InputStream getAudioInputStream() {
        Log.d(TAG, "getAudioInputStream");
        Pair<OutputStream, InputStream> audioStreams;
        try {
            audioStreams = Helpers.getPipedAudioStreams(bufferSize);
        } catch (IOException e) {
            Log.e(TAG, "Exception creating audio stream", e);
            return null;
        }
        nativeAudioWriteStreams.add(audioStreams.first);
        return audioStreams.second;
    }

    public static void setRecordingDeviceId(int recordingDeviceId) {
        NativeAudioEngine.setRecordingDeviceId(recordingDeviceId);
    }

    public static void setPlaybackDeviceId(int playbackDeviceId) {
        NativeAudioEngine.setPlaybackDeviceId(playbackDeviceId);
    }

    public static int getSampleRate() {
        return NativeAudioEngine.getSampleRate();
    }

    public static int getChannelCount() {
        return NativeAudioEngine.getChannelCount();
    }

    public static String getAudioApi() {
        switch(NativeAudioEngine.getAudioApi()) {
            case AUDIO_API_OPENSLES:
                return "OpenSL ES";
            case AUDIO_API_AAUDIO:
                return "AAudio";
            default:
                return "[not recording]";
        }
    }
}