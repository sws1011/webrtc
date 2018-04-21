package com.sws;

import android.Manifest.permission;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import com.sws.jni.WebRtcUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by shiwenshui 2018/4/20 17:54
 */
public class WebRtcActivity extends AppCompatActivity {

    /**
     * 原始音频文件路径
     */
    private static final String AUDIO_FILE_PATH = Environment.getExternalStorageDirectory().getPath() + "/recorded_audio.pcm";

    /**
     * 处理过的音频文件路径
     */
    private static final String AUDIO_PROCESS_FILE_PATH = Environment.getExternalStorageDirectory().getPath() + "/recorded_audio_process.pcm";

    private boolean isInitialized;
    private int mMinBufferSize;
    private AudioTrack mAudioTrack;
    private File mFile;
    private File mProcessFile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initAudioRecord();
        if (VERSION.SDK_INT >= VERSION_CODES.M && PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(getApplicationContext(), permission.WRITE_EXTERNAL_STORAGE)) {
            requestPermissions(new String[]{permission.WRITE_EXTERNAL_STORAGE}, 1000);
        } else {
            initAudio();
        }
        setup();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1000) {
            for (int grant : grantResults) {
                if (grant == PackageManager.PERMISSION_GRANTED) {
                    initAudio();
                    break;
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    }

    private void initAudio() {
        mProcessFile = new File(AUDIO_PROCESS_FILE_PATH);

        mFile = new File(AUDIO_FILE_PATH);

        if (!mFile.exists() || mFile.length() <= 0) {
            Log.e("sws", " init file-----------");
            AssetManager assets = getAssets();
            try {
                InputStream inputStream = assets.open("record/recorded_audio.pcm");
                FileOutputStream fileOutputStream = new FileOutputStream(mFile);
                byte[] buf = new byte[1024 * 1024];
                int len;
                while ((len = inputStream.read(buf)) != -1) {
                    fileOutputStream.write(buf, 0, len);
                }
                inputStream.close();
                fileOutputStream.close();
                isInitialized = true;
                Log.e("sws", " init file end-----------");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Log.e("sws", "-----------");
            isInitialized = true;
        }
    }

    private void initAudioRecord() {
        if (mAudioTrack == null) {
            mMinBufferSize = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, mMinBufferSize, AudioTrack.MODE_STREAM);
        }
    }

    private void setup() {
        Button playingBtn = findViewById(R.id.playing);
        Button playingAgcNsBtn = findViewById(R.id.playing_process);
        Button agcNsProcessBtn = findViewById(R.id.agc_ns);

        playingBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isInitialized || !mFile.exists()) {
                    Toast.makeText(WebRtcActivity.this, "文件读写失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                playing(false);
            }
        });

        playingAgcNsBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isInitialized && !mProcessFile.exists() || mProcessFile.length() <= 0) {
                    Toast.makeText(WebRtcActivity.this, "文件读写失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                playing(true);
            }
        });

        agcNsProcessBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isInitialized || !mFile.exists()) {
                    Toast.makeText(WebRtcActivity.this, "文件读写失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mProcessFile.exists() && mProcessFile.length() > 0) {
                    Toast.makeText(getApplicationContext(), "完成",Toast.LENGTH_SHORT).show();
                    return;
                }
                process();
            }
        });
    }

    private void process() {
        WebRtcUtils.webRtcAgcInit(0, 255, 8000);
        WebRtcUtils.webRtcNsInit(8000);
        try {
            FileInputStream ins = new FileInputStream(mFile);
            File outFile = new File(AUDIO_PROCESS_FILE_PATH);
            FileOutputStream out = new FileOutputStream(outFile);

            byte[] buf = new byte[320];

            while (ins.read(buf) != -1) {
                short[] shortData = new short[buf.length >> 1];

                short[] processData = new short[buf.length >> 1];
                ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData);

                short[] nsProcessData = WebRtcUtils.webRtcNsProcess(shortData.length, shortData);
                WebRtcUtils.webRtcAgcProcess(nsProcessData, processData, nsProcessData.length);

                out.write(shortsToBytes(processData));
            }
            Toast.makeText(getApplicationContext(), "完成",Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.e("sws", "ns end======");

        WebRtcUtils.webRtcNsFree();
        WebRtcUtils.webRtcAgcFree();
    }

    private byte[] shortsToBytes(short[] data) {
        byte[] buffer = new byte[data.length * 2];
        int shortIndex, byteIndex;
        shortIndex = byteIndex = 0;
        for (; shortIndex != data.length; ) {
            buffer[byteIndex] = (byte) (data[shortIndex] & 0x00FF);
            buffer[byteIndex + 1] = (byte) ((data[shortIndex] & 0xFF00) >> 8);
            ++shortIndex;
            byteIndex += 2;
        }
        return buffer;
    }

    private boolean isPlaying;

    private void playing(final boolean isPlayingProcess) {
        if (isPlaying) {
            isPlaying = false;
            mAudioTrack.stop();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream ins = null;
                try {
                    File file = mFile;
                    if (isPlayingProcess) {
                        file = mProcessFile;
                    }

                    isPlaying = true;

                    ins = new FileInputStream(file);
                    mAudioTrack.play();
                    byte[] buf = new byte[mMinBufferSize];
                    int len;
                    while ((len = ins.read(buf)) != -1 && mAudioTrack != null && isPlaying) {
                        mAudioTrack.write(buf, 0, len);
                    }
                    if (mAudioTrack == null) {
                        return;
                    }
                    mAudioTrack.stop();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (ins != null) {
                        try {
                            ins.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isPlaying = false;
        if (mAudioTrack != null) {
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }
}
