/*#include "jni_utils.h"

using Point3 = std::array<jfloat, 3>;
class PointF {
    JNIEnv* env;
    jfloatArray thiz;
    jfloat* val;
public:
    PointF(JNIEnv* env, jfloatArray thiz)
        : env(env), thiz(thiz), val(env->GetFloatArrayElements(thiz, nullptr)) {}

    PointF(const PointF&) = delete;
    void operator=(const PointF&) = delete;
    PointF(PointF&&) = delete;
    void operator=(PointF&&) = delete;

    ~PointF() {
        env->ReleaseFloatArrayElements(thiz, val, 0);
    }
    jfloat x() const {return val[0];}
    jfloat y() const {return val[1];}

    void setX(jfloat v) { val[0] = v;}
    void setY(jfloat v) { val[1] = v;}
};

class Matrix {
    // 3x3
    JNIEnv* env;
    jfloatArray thiz;
    jfloat* val;
public:
    constexpr explicit Matrix(JNIEnv* env, jfloatArray thiz)
        : env(env), thiz(thiz), val(env->GetFloatArrayElements(thiz, nullptr)) {}

    Matrix(const Matrix&) = delete;
    void operator=(const Matrix&) = delete;
    Matrix(Matrix&&) = delete;
    void operator=(Matrix&&) = delete;

    ~Matrix() {
        // JNI_ABORT: Don't copy the changes, If it doesn't work replace with 0
        env->ReleaseFloatArrayElements(thiz, val, JNI_ABORT);
    }

    void multiplyInplace(PointF& p) const {
        const Point3 vec = { p.x(), p.y(), 1 };
        Point3 res = { 0, 0, 0 };
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                res[i] += val[i*3+j] * vec[j];
            }
        }
        p.setX(res[0]);
        p.setY(res[1]);
        LOGD("(%.1f %.1f)", res[0], res[1]);
    }

    constexpr jfloat smallDet() const {
        //0 1 2
        //3 4 5
        //6 7 8
        return val[0] * val[4] - val[1] * val[3];
    }
    void inverseMultiply(PointF& p) const {
        //const Point3 vec = { p.x(), p.y(), 1 };
        auto delta = smallDet();
        auto scaleX = val[0];
        auto scaleY = val[4];
        auto tx = val[2];
        auto ty = val[5];
        auto t1 = p.x() + tx * scaleX;// * alpha;
        auto t2 = p.y() + ty * scaleY;// * beta;
        //LOGD("b (%.1f %.1f)", p.x(), p.y());
        LOGD("b (%.1f %.1f %.1f)", tx, ty, scaleX);// p.x(), p.y());
        LOGD("ż (%.1f %.1f)", p.x(), p.y());
        p.setX(t1);
        p.setY(t2);// (t1-t2) / delta);
        LOGD("a (%.1f %.1f)", p.x(), p.y());
        //p.setY((t1-t2) / delta);
        //LOGD("0? (%f %f %f %f)", val[0], val[1], val[3], val[4]);
        / *
        Point3 res = { 0, 0, 0 };
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                res[i] += val[i*3+j] * vec[j];
            }
        }
        LOGD("0? (%f %f %f)", val[6], val[7], val[8]);
        p.setX(res[0]);
        p.setY(res[1]);
         * /
    }
};

JNIFUN(void, matrixScreenToDrawable)(JNIEnv* env, jobject, jfloatArray mat, jfloatArray vec) {
    Matrix m(env, mat);
    PointF p(env, vec);
    m.inverseMultiply(p);
}
/ *
JNIFUN(void, matrixDrawableToScreen)(JNIEnv* env, jobject, jfloatArray mat, PointF_t vec) {
    Matrix m(env->GetFloatArrayElements(mat, nullptr));
    PointF p(env, vec);
    m.preMultiplyMk2(p);
    m.preMultiplyInplace(p);
}*/
