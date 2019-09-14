#ifndef AZBYN_OCR_JNI_UTILS_H
#define AZBYN_OCR_JNI_UTILS_H

#pragma clang diagnostic push
#pragma ide diagnostic ignored "OCUnusedMacroInspection"
#pragma ide diagnostic ignored "cppcoreguidelines-macro-usage"
#pragma ide diagnostic ignored "OCUnusedGlobalDeclarationInspection"

#include <fmt/core.h>

#include <jni.h>
#include <android/log.h>
#include <opencv2/core/mat.hpp>

constexpr char LOG_TAG[] = "azbyn-ocr";

#define LOG(PRIORITY, PRIORITY_STR, FORMAT, ...) \
    __android_log_print(PRIORITY, LOG_TAG, \
    "c++ " PRIORITY_STR " @%s: " FORMAT, __func__, ##__VA_ARGS__)
//"c++ " PRIORITY_STR " @%s: " FORMAT,  __PRETTY_FUNCTION__, ##__VA_ARGS__)

#define LOGE(...) LOG(ANDROID_LOG_ERROR, "E", __VA_ARGS__)
#define LOGW(...) LOG(ANDROID_LOG_WARN,  "W", __VA_ARGS__)
#define LOGD(...) LOG(ANDROID_LOG_DEBUG, "D", __VA_ARGS__)
#define LOGI(...) LOG(ANDROID_LOG_INFO,  "I", __VA_ARGS__)

#define JNIFUN(t, name) extern "C" JNIEXPORT t JNICALL Java_com_azbyn_ocr_JniImpl_ ## name

#define CLASSDATA(name) \
    static name& instance(JNIEnv* env) { \
        static name c(env); \
        /*LOGI("instance %s %p", #name, &c);*/ \
        return c;\
    } \
    static void createOrDel(JNIEnv* env, Mode mode) {\
        auto& i = instance(env);\
        if (mode == Mode::Delete) i.clean(env);\
    } \
    name(const name&) = delete;\
    void operator=(const name &) = delete;\
    name(name&&) = delete;\
    void operator=(name&&) = delete;

#define JNI_THROW(env, msg) Exception::throwNew(env, msg, constexpr_file_name(__FILE__), __LINE__)

constexpr const char* constexpr_file_name(const char* str) {
    constexpr struct F {
        constexpr F() {}
        constexpr const char* str_end(const char *str) const {
            return *str ? str_end(str + 1) : str;
        };

        constexpr bool str_slant(const char *str) const {
            return *str == '/' ? true : (*str ? str_slant(str + 1) : false);
        };
        constexpr const char* r_slant(const char* str) const {
            return *str == '/' ? (str + 1) : r_slant(str - 1);
        };
    } f;
    return f.str_slant(str) ? f.r_slant(f.str_end(str)) : str;
}

inline cv::Mat& addrToMat(jlong addr) {
    return *(cv::Mat*) addr;
}
struct BaseClassData {
    enum class Mode {
        Create, Delete
    };
    jclass clazz;
    BaseClassData() = delete;
    BaseClassData(BaseClassData const&) = delete;
    void operator=(BaseClassData const&) = delete;

    BaseClassData(BaseClassData&&) = delete;
    void operator=(BaseClassData&&) = delete;

    void clean(JNIEnv* env);
protected:
    BaseClassData(JNIEnv* env, const char* name);
    jmethodID getMethod(JNIEnv* env, const char* name, const char* sig);
    jfieldID getField(JNIEnv* env, const char* name, const char* sig);

    template <typename T>
    jfieldID getField(JNIEnv* env, const char* name) { return getField(env, name, T::signature); }
    template <>
    jfieldID getField<jlong>(JNIEnv* env, const char* name) { return getField(env, name, "J"); }
    template <>
    jfieldID getField<jfloat>(JNIEnv* env, const char* name) { return getField(env, name, "F"); }
    /*
Z - boolean
B - byte
C - char
S - short
I - int
J - long
F - float
D - double
L fully-qualified-class ;
[ type - type[]
( arg-types ) ret-type
     * */
};

namespace Exception {
struct ClassData : public BaseClassData {
    CLASSDATA(ClassData)
    ClassData(JNIEnv *env) : BaseClassData(env, "java/lang/Exception") {}
};

inline void throwNew(JNIEnv *env, const char *_msg, const char *file, size_t line) {
    std::string msg = fmt::format("{}@{}: '{}'", file, line, _msg);
    env->ThrowNew(ClassData::instance(env).clazz, msg.c_str());
}
}
/*
struct PointF {
    struct ClassData : public BaseClassData {
        CLASSDATA(ClassData)
        jfieldID x, y;

        ClassData(JNIEnv *env) : BaseClassData(env, "android/graphics/PointF") {
            x = getField(env, "x", "F");
            y = getField(env, "y", "F");
        }
    };

    JNIEnv* env;
    jobject thiz;

    PointF(JNIEnv* env, jobject thiz): env(env), thiz(thiz) {}
    jfloat x() const {
        return env->GetFloatField(thiz, ClassData::instance(env).x);
    }
    void setX(jfloat val) {
        env->SetFloatField(thiz, ClassData::instance(env).x, val);
    }

    jfloat y() const {
        return env->GetFloatField(thiz, ClassData::instance(env).y);
    }
    void setY(jfloat val) {
        env->SetFloatField(thiz, ClassData::instance(env).y, val);
    }
};
*/

struct MatClassData : public BaseClassData {
    CLASSDATA(MatClassData)
    jmethodID ctor;
    jfieldID nativeObj;
    MatClassData(JNIEnv* env) : BaseClassData(env, "org/opencv/core/Mat") {
        //J stands for long, natürlich
        ctor = getMethod(env, "<init>", "(J)V");
        nativeObj = getField(env, "nativeObj", "J");
    }
    jobject create(JNIEnv* env, const cv::Mat& val) {
        cv::Mat* mat = new cv::Mat();
        val.copyTo(*mat);
        jlong addr = (jlong) mat;
        return env->NewObject(clazz, ctor, addr);
    }
};

struct ArrayList {
    struct ClassData : public BaseClassData {
        CLASSDATA(ClassData)
        jmethodID size, add, get, clear;
        ClassData(JNIEnv* env) : BaseClassData(env, "java/util/ArrayList") {
            size = getMethod(env, "size", "()I");
            get = getMethod(env, "get", "(I)Ljava/lang/Object;");
            add = getMethod(env, "add", "(Ljava/lang/Object;)Z");
            clear = getMethod(env, "clear", "()V");
        }
    };
    /*thread_local*/ JNIEnv* env;
    jobject thiz;

    ArrayList(JNIEnv* env, jobject thiz): env(env), thiz(thiz) {}
    jobject get(jint i) const {
        return env->CallObjectMethod(thiz, ClassData::instance(env).get, i);
    }
    cv::Mat& getMat(jint i) const {
        jobject local = get(i);
        jlong addr = env->GetLongField(local, MatClassData::instance(env).nativeObj);
        env->DeleteLocalRef(local);
        return addrToMat(addr);
    }
    bool add(jobject el) {
        return env->CallBooleanMethod(thiz, ClassData::instance(env).add, el);
    }
    bool add(const cv::Mat& mat) {
        jobject local = MatClassData::instance(env).create(env, mat);
        auto r = add(local);
        env->DeleteLocalRef(local);
        return r;
    }
    jint size() const {
        return env->CallIntMethod(thiz, ClassData::instance(env).size);
    }
    void clear() {
        env->CallVoidMethod(thiz, ClassData::instance(env).clear);
    }
};
#undef CLASSDATA
#pragma clang diagnostic pop
#endif //AZBYN_OCR_JNI_UTILS_H
