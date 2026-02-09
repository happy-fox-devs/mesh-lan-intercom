#include <jni.h>
#include <string>
#include <android/log.h>
#include "AudioEngine.h"

static AudioEngine *audioEngine = nullptr;

extern "C" JNIEXPORT jstring JNICALL
Java_com_meshintercom_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++ (Oboe Integrated)";
    return env->NewStringUTF(hello.c_str());
}

static JavaVM *gJvm = nullptr;
static jobject gMainActivityObject = nullptr;

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    gJvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_meshintercom_MainActivity_nativeInitJni(JNIEnv *env, jobject thiz) {
    if (gMainActivityObject != nullptr) {
        env->DeleteGlobalRef(gMainActivityObject);
    }
    gMainActivityObject = env->NewGlobalRef(thiz);
}

// Callback called from Audio Thread (C++)
void onAudioEncoded(const uint8_t *data, int32_t size) {
    if (gJvm == nullptr || gMainActivityObject == nullptr) return;

    JNIEnv *env;
    int getEnvStat = gJvm->GetEnv((void **) &env, JNI_VERSION_1_6);

    bool didAttach = false;
    if (getEnvStat == JNI_EDETACHED) {
        if (gJvm->AttachCurrentThread(&env, nullptr) != 0) return;
        didAttach = true;
    }

    // Call Kotlin method: onNativeAudioData(ByteArray)
    jclass clazz = env->GetObjectClass(gMainActivityObject);
    jmethodID methodId = env->GetMethodID(clazz, "onNativeAudioData", "([B)V");

    if (methodId != nullptr) {
        jbyteArray retArray = env->NewByteArray(size);
        env->SetByteArrayRegion(retArray, 0, size, (jbyte *) data);
        env->CallVoidMethod(gMainActivityObject, methodId, retArray);
        env->DeleteLocalRef(retArray);
    }

    if (didAttach) {
        gJvm->DetachCurrentThread();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_meshintercom_MainActivity_nativeStartAudio(JNIEnv *env, jobject thiz) {
    if (audioEngine == nullptr) {
        audioEngine = new AudioEngine();
        audioEngine->setAudioCallback(onAudioEncoded);
    }
    audioEngine->start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_meshintercom_MainActivity_nativeInjectAudioPacket(JNIEnv *env, jobject thiz,
                                                           jbyteArray data) {
    if (audioEngine == nullptr) return;

    jbyte *bufferPtr = env->GetByteArrayElements(data, nullptr);
    jsize length = env->GetArrayLength(data);

    audioEngine->injectAudioPacket((uint8_t *) bufferPtr, length);

    env->ReleaseByteArrayElements(data, bufferPtr, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_meshintercom_MainActivity_nativeStopAudio(JNIEnv *env, jobject thiz) {
    if (audioEngine != nullptr) {
        audioEngine->stop();
        delete audioEngine;
        audioEngine = nullptr;
    }
}
