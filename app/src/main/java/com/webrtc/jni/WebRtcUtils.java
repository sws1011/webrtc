package com.webrtc.jni;

/**
 * Created by shiwenshui 2018/4/20 17:40
 */
public class WebRtcUtils {

    static {
        System.loadLibrary("WebRtcAudio");
    }

    public static native void webRtcNsInit(int freq);

    public static native short[] webRtcNsProcess(int len, short[] proData);
    public static native int webRtcNsFree();

    public static native void webRtcAgcInit(long minVolume, long maxVolume, long freq);
    public static native void webRtcAgcFree();
    public static native void webRtcAgcProcess(short[] srcData, short[] desData, int srcLen);
}
