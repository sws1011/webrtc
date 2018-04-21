#include <jni.h>
#include <string>
#include "_android_log_print.h"
#include "noise_suppression.h"
#include "analog_agc.h"


#ifdef __cplusplus
extern "C" {
#endif

//音量增益
void *agcHandle = NULL;


JNIEXPORT void JNICALL
Java_com_sws_jni_WebRtcUtils_webRtcAgcProcess(JNIEnv *env, jclass type, jshortArray srcData_,
                                          jshortArray desData_, jint srcLen) {

    jshort *srcData = env->GetShortArrayElements(srcData_, NULL);
    jshort *desData = env->GetShortArrayElements(desData_, NULL);

    jsize src_len = env->GetArrayLength(srcData_);
    int frameSize = 160;

    int micLevelIn = 0;
    int micLevelOut = 0;
//    LOGD("src_len=== %d", src_len);
    int16_t echo = 1;                                 //增益放大是否考虑回声影响
    uint8_t saturationWarning;

    int iFrame;
    int nFrame = src_len / frameSize; //帧数
    int leftLen = src_len % frameSize; //最后一帧的大小
    int onceLen = frameSize;
    nFrame = (leftLen > 0) ? nFrame + 1 : nFrame;

    short *agcIn = (short *) malloc(frameSize * sizeof(short));
    short *agcOut = (short *) malloc(frameSize * sizeof(short));

    for (iFrame = 0; iFrame < nFrame; iFrame++) {

        if (iFrame == nFrame - 1 && leftLen != 0) {
            onceLen = leftLen;
        }
//        LOGE("WebRtcAgc_Process onceLen ==%d", onceLen);
        memcpy(agcIn, srcData + iFrame * frameSize, onceLen * sizeof(short));

        int state = WebRtcAgc_Process(agcHandle, agcIn, NULL, 160, agcOut, NULL,
                                      micLevelIn, &micLevelOut, echo, &saturationWarning);
        if (state != 0) {
            LOGE("WebRtcAgc_Process error");
            break;
        }
        if (saturationWarning != 0) {
            LOGE("[AgcProc]: saturationWarning occured");
        }
        memcpy(desData + iFrame * frameSize, agcOut + iFrame * frameSize, onceLen * sizeof(short));
        micLevelIn = micLevelOut;
    }

    free(agcIn);
    free(agcOut);
    env->ReleaseShortArrayElements(srcData_, srcData, 0);
    env->ReleaseShortArrayElements(desData_, desData, 0);
}

JNIEXPORT void JNICALL
Java_com_sws_jni_WebRtcUtils_webRtcAgcFree(JNIEnv *env, jclass type) {
    if (agcHandle != NULL) {
        int free = WebRtcAgc_Free(agcHandle);
        if (free == -1) {
            LOGE("WebRtcAgc_Free error");
        }
        agcHandle = NULL;
    }
}

JNIEXPORT void JNICALL
Java_com_sws_jni_WebRtcUtils_webRtcAgcInit(JNIEnv *env, jclass type, jlong minVolume, jlong maxVolume,
                                       jlong freq) {
    int agc = WebRtcAgc_Create(&agcHandle);
    if (agc == 0) {
        int16_t agcMode = kAgcModeFixedDigital;
        int agcInit = WebRtcAgc_Init(agcHandle, (int32_t) minVolume, (int32_t) maxVolume,  agcMode,
                                     (uint32_t) freq);
        if (agcInit == 0) {
            WebRtcAgc_config_t agcConfig;
            agcConfig.compressionGaindB = 20;
            agcConfig.limiterEnable = 1;
            agcConfig.targetLevelDbfs = 3;

            int initConfig = WebRtcAgc_set_config(agcHandle, agcConfig);
            if (initConfig == -1) {
                LOGE("WebRtcAgc_set_config error");
            }
        } else {
            LOGE("WebRtcAgc_Init error");
        }
    } else {
        LOGE("WebRtcAgc_Create error");
    }
}

// 降噪处理句柄
NsHandle *pNs_inst = NULL;

JNIEXPORT jshortArray JNICALL
Java_com_sws_jni_WebRtcUtils_webRtcNsProcess(JNIEnv *env, jclass type, jint len, jshortArray proData_) {

    jshort *proData = env->GetShortArrayElements(proData_, NULL);
    int dataLen = env->GetArrayLength(proData_);

    if (pNs_inst) {
        //默认处理的是8k 16位采样率
        for (int i = 0; i < dataLen; i += sizeof(short) * 160) {
            if (dataLen - i >= sizeof(short) * 160) {
                short shBufferIn[160] = {0};
                short shBufferOut[160] = {0};
                memcpy(shBufferIn, (proData + i), 160 * sizeof(short));
                if (0 != WebRtcNs_Process(pNs_inst, shBufferIn, NULL, shBufferOut, NULL)) {
                    LOGE("Noise_Suppression WebRtcNs_Process err! \n");
                }
                memcpy(proData + i, shBufferOut, 160 * sizeof(short));
                LOGD("Noise_Suppression WebRtcNs_Process");
            }
        }
    } else {
        LOGD("pNs_inst null==");
    }

    env->ReleaseShortArrayElements(proData_, proData, 0);

    return proData_;
}

JNIEXPORT jint JNICALL
Java_com_sws_jni_WebRtcUtils_webRtcNsFree(JNIEnv *env, jclass type) {
    int _result = -1;
    if (pNs_inst) {
        _result = WebRtcNs_Free(pNs_inst);
        pNs_inst = NULL;
        LOGD("Noise_Suppression webRtcNsFree");
    }
    return _result;
}

JNIEXPORT void JNICALL
Java_com_sws_jni_WebRtcUtils_webRtcNsInit(JNIEnv *env, jclass type, jint freq) {

    //创建降噪句柄
    int val = WebRtcNs_Create(&pNs_inst);
    LOGD("WebRtcNs_Create ==");
    if (val == 0) {
        //初始化 采样率 8k 16k 32k
        WebRtcNs_Init(pNs_inst, freq);
        WebRtcNs_set_policy(pNs_inst, 2);
    } else {
        LOGD("WebRtcNs_Create fail");
    }
}

#ifdef __cplusplus
}
#endif
