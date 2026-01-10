#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_metadon_music_AudioEngine_getEngineStatus(JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF("C++ Engine: Ready for DSP");
}