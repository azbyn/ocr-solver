#include "jni_utils.h"

#include <stdexcept>

static void checkException(JNIEnv* env) {
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        throw std::logic_error("method not found not found?");
    }
}
BaseClassData::BaseClassData(JNIEnv *env, const char *name) {
    auto local = env->FindClass(name);
    if (local == nullptr)
        throw std::runtime_error("local == null");
    this->clazz = (jclass) env->NewGlobalRef(local);
    //LOGI("created %s %p, clazz %p", name, this, clazz);
    env->DeleteLocalRef(local);
}

void BaseClassData::clean(JNIEnv* env) {
    //LOGI("cleaned class %p", this);
    env->DeleteGlobalRef(clazz);
}

jmethodID BaseClassData::getMethod(JNIEnv *env, const char *name, const char *sig) {
    auto local = env->GetMethodID(clazz, name, sig);
    checkException(env);
    return local;
}

jfieldID BaseClassData::getField(JNIEnv *env, const char *name, const char *sig) {
    auto local = env->GetFieldID(clazz, name, sig);
    checkException(env);
    return local;
}

void lazyClassDatas(JNIEnv* env, BaseClassData::Mode mode) {
    Exception::ClassData::createOrDel(env, mode);
    MatClassData::createOrDel(env, mode);
    ArrayList::ClassData::createOrDel(env, mode);
    //PointF::ClassData::createOrDel(env, mode);
}
// According to http://docs.oracle.com/javase/1.5.0/docs/guide/jni/spec/invocation.html#JNI_OnLoad
// The VM calls JNI_OnLoad when the native library is loaded (for example, through System.loadLibrary).
extern "C" jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    } else {
        try {
            lazyClassDatas(env, BaseClassData::Mode::Create);
        } catch (std::exception& e) {
            LOGE("error: %s ", e.what());
            return JNI_ERR;
        }
    }
    return JNI_VERSION_1_6;
}
// According to http://docs.oracle.com/javase/1.5.0/docs/guide/jni/spec/invocation.html#JNI_OnUnload
// The VM calls JNI_OnUnload when the class loader containing the native library is garbage collected.
extern "C" void JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv* env;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        // Something is wrong but nothing we can do about this :(
        return;
    } else {
        lazyClassDatas(env, BaseClassData::Mode::Delete);
    }
}
