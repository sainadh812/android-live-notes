#include <jni.h>
#include <mutex>
#include <string>
#include <unordered_map>

namespace {
struct StreamHandle {
    std::mutex mutex;
    std::string committed;
    std::string tentative;
    std::string last_error;
};

std::mutex g_handles_mutex;
std::unordered_map<jlong, StreamHandle*> g_handles;
jlong g_next_handle = 1;

jstring make_json(JNIEnv* env, const std::string& committed, const std::string& tentative, const std::string& error) {
    std::string json = std::string("{\"committed\":\"") + committed +
                       "\",\"tentative\":\"" + tentative +
                       "\",\"error\":\"" + error + "\"}";
    return env->NewStringUTF(json.c_str());
}
}

extern "C" {
JNIEXPORT jlong JNICALL
Java_com_sainadh_livenotes_stt_NemotronNativeBridge_nativeInit(
        JNIEnv* env,
        jobject /*thiz*/,
        jstring /*modelPath*/,
        jstring /*languageTag*/) {
    auto* handle = new StreamHandle();
    std::lock_guard<std::mutex> guard(g_handles_mutex);
    const jlong id = g_next_handle++;
    g_handles[id] = handle;
    return id;
}

JNIEXPORT jstring JNICALL
Java_com_sainadh_livenotes_stt_NemotronNativeBridge_nativeFeedPcm(
        JNIEnv* env,
        jobject /*thiz*/,
        jlong handle,
        jbyteArray /*pcmBytes*/,
        jint /*byteCount*/) {
    std::lock_guard<std::mutex> guard(g_handles_mutex);
    auto it = g_handles.find(handle);
    if (it == g_handles.end()) {
        return make_json(env, "", "", "Native Nemotron handle is closed");
    }
    return make_json(env, it->second->committed, it->second->tentative, "");
}

JNIEXPORT jstring JNICALL
Java_com_sainadh_livenotes_stt_NemotronNativeBridge_nativeFinalizeStream(
        JNIEnv* env,
        jobject /*thiz*/,
        jlong handle) {
    std::lock_guard<std::mutex> guard(g_handles_mutex);
    auto it = g_handles.find(handle);
    if (it == g_handles.end()) {
        return make_json(env, "", "", "Native Nemotron handle is closed");
    }
    return make_json(env, it->second->committed, "", it->second->last_error);
}

JNIEXPORT void JNICALL
Java_com_sainadh_livenotes_stt_NemotronNativeBridge_nativeRestartStream(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jlong handle) {
    std::lock_guard<std::mutex> guard(g_handles_mutex);
    auto it = g_handles.find(handle);
    if (it == g_handles.end()) return;
    std::lock_guard<std::mutex> handle_guard(it->second->mutex);
    it->second->committed.clear();
    it->second->tentative.clear();
    it->second->last_error.clear();
}

JNIEXPORT void JNICALL
Java_com_sainadh_livenotes_stt_NemotronNativeBridge_nativeRelease(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jlong handle) {
    std::lock_guard<std::mutex> guard(g_handles_mutex);
    auto it = g_handles.find(handle);
    if (it == g_handles.end()) return;
    delete it->second;
    g_handles.erase(it);
}
}
