package com.webrtc;

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
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.Toast;

import com.webrtc.jni.WebRtcUtils;

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

    private static final int SAMPLERATE_32K = 32000;
    private static final int SAMPLERATE_16K = 16000;
    private static final int SAMPLERATE_8K = 8000;

    private static final String AUDIO_FILE_AST_8K = "record/recorded_audio.pcm";
    private static final String AUDIO_FILE_AST_16k = "record/recorded_audio_16k.pcm";
    private static final String AUDIO_FILE_AST_32k = "record/recorded_audio_32k.pcm";

    /**
     * 原始音频文件路径
     */
    private static final String AUDIO_FILE_PATH_8k = Environment.getExternalStorageDirectory().getPath() +
            "/recorded_audio.pcm";
    private static final String AUDIO_FILE_PATH_16k = Environment.getExternalStorageDirectory().getPath() +
            "/recorded_audio_16k.pcm";
    private static final String AUDIO_FILE_PATH_32K = Environment.getExternalStorageDirectory().getPath() +
            "/recorded_audio_32k.pcm";

    /**
     * 处理过的音频文件路径
     */
    private static final String AUDIO_PROCESS_FILE_PATH_8k = Environment.getExternalStorageDirectory().getPath() +
            "/recorded_audio_process.pcm";
    private static final String AUDIO_PROCESS_FILE_PATH_16k = Environment.getExternalStorageDirectory().getPath() +
            "/recorded_audio_process_16k.pcm";
    private static final String AUDIO_PROCESS_FILE_PATH_32k = Environment.getExternalStorageDirectory().getPath() +
            "/recorded_audio_process_32k.pcm";

    private boolean isInitialized;
    private int mMinBufferSize;
    private AudioTrack mAudioTrack;
    private File mFile;
    private File mProcessFile;

    private String AUDIO_FILE_PATH;
    private String AUDIO_PROCESS_FILE_PATH;
    private String srcPath;

    private boolean process32KData;

    private int mSampleRate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSampleRate = SAMPLERATE_8K;

        initAudioRecord();
        AUDIO_FILE_PATH = AUDIO_FILE_PATH_8k;
        AUDIO_PROCESS_FILE_PATH = AUDIO_PROCESS_FILE_PATH_8k;
        srcPath = AUDIO_FILE_AST_8K;

        if (VERSION.SDK_INT >= VERSION_CODES.M && PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(getApplicationContext(), permission.WRITE_EXTERNAL_STORAGE)) {
            requestPermissions(new String[]{permission.WRITE_EXTERNAL_STORAGE}, 1000);
        } else {
            initAudio();
        }
        setup();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
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
        if (TextUtils.isEmpty(srcPath) || TextUtils.isEmpty(AUDIO_FILE_PATH) || TextUtils.isEmpty(AUDIO_PROCESS_FILE_PATH)) {
            return;
        }
        Log.e("sws", "srcPath==" + srcPath);

        mProcessFile = new File(AUDIO_PROCESS_FILE_PATH);

        mFile = new File(AUDIO_FILE_PATH);

        if (!mFile.exists() || mFile.length() <= 0) {
            Log.e("sws", " init file-----------");
            AssetManager assets = getAssets();
            try {
                InputStream inputStream = assets.open(srcPath);
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
        stopPlay();
        mMinBufferSize = AudioRecord.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, mMinBufferSize, AudioTrack.MODE_STREAM);
    }

    private int selectId = -1;

    private void setup() {
        selectId = R.id.rb_8k;
        RadioGroup radioGroup = findViewById(R.id.rg);
        int checkedRadioButtonId = radioGroup.getCheckedRadioButtonId();
        switchDataSrc(checkedRadioButtonId);
        radioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.e("sws", "checkedId ==" + checkedId);
                switchDataSrc(checkedId);
            }
        });

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

                process();
            }
        });
    }

    private void switchDataSrc(int rbId) {
        if (selectId == rbId) {
            return;
        }
        isInitialized = false;
        selectId = rbId;
        process32KData = false;
        switch (rbId) {
            case R.id.rb_8k:
                mSampleRate = SAMPLERATE_8K;
                AUDIO_FILE_PATH = AUDIO_FILE_PATH_8k;
                AUDIO_PROCESS_FILE_PATH = AUDIO_PROCESS_FILE_PATH_8k;
                srcPath = AUDIO_FILE_AST_8K;

                initAudioRecord();
                initAudio();
                break;
            case R.id.rb_16k:
                mSampleRate = SAMPLERATE_16K;
                AUDIO_FILE_PATH = AUDIO_FILE_PATH_16k;
                AUDIO_PROCESS_FILE_PATH = AUDIO_PROCESS_FILE_PATH_16k;
                srcPath = AUDIO_FILE_AST_16k;

                initAudioRecord();
                initAudio();

                break;
            case R.id.rb_32k:
                process32KData = true;
                mSampleRate = SAMPLERATE_32K;
                AUDIO_FILE_PATH = AUDIO_FILE_PATH_32K;
                AUDIO_PROCESS_FILE_PATH = AUDIO_PROCESS_FILE_PATH_32k;
                srcPath = AUDIO_FILE_AST_32k;
                initAudioRecord();
                initAudio();
                break;
        }
    }

    private void process() {
        WebRtcUtils.webRtcAgcInit(0, 255, mSampleRate);
        WebRtcUtils.webRtcNsInit(mSampleRate);
        try {
            FileInputStream ins = new FileInputStream(mFile);
            File outFile = new File(AUDIO_PROCESS_FILE_PATH);
            FileOutputStream out = new FileOutputStream(outFile);

            byte[] buf;
            if (process32KData) {
                buf = new byte[640];
            } else {
                buf = new byte[320];
            }

            while (ins.read(buf) != -1) {
                short[] shortData = new short[buf.length >> 1];

                short[] processData = new short[buf.length >> 1];
                ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData);

                if (process32KData) {
                    short[] nsProcessData = WebRtcUtils.webRtcNsProcess32k(shortData.length, shortData);
                    WebRtcUtils.webRtcAgcProcess32k(nsProcessData, processData, nsProcessData.length);
                } else {
                    short[] nsProcessData = WebRtcUtils.webRtcNsProcess(shortData.length, shortData);
                    WebRtcUtils.webRtcAgcProcess(nsProcessData, processData, nsProcessData.length);
                }

                out.write(shortsToBytes(processData));
            }
            Toast.makeText(getApplicationContext(), "完成", Toast.LENGTH_SHORT).show();
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
        stopPlay();
    }

    private void stopPlay() {
        isPlaying = false;
        if (mAudioTrack != null) {
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }
}
