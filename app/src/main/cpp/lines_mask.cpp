#include "super_lines_removal.h"
#include "jni_utils.h"

#include <opencv2/imgproc.hpp>

JNIFUN(void, getLinesMask)(JNIEnv* env, jobject,
        jlong resultAddr, jint thickness,
        jlong midsAddr,
        jint width, jint height) {
    try {
        auto& result = addrToMat(resultAddr);
        auto* mids = SlineMids::fromAddr(midsAddr);
        /*resultMat = */
        cv::Mat(cv::Size(width, height), CV_8U).copyTo(result);
        const cv::Scalar col(255.0);

        for (auto m : mids->hori) {
            cv::line(result, cv::Point(0, m), cv::Point(width, m), col, thickness);
        }
        for (auto m : mids->vert) {
            cv::line(result, cv::Point(m, 0), cv::Point(m, height), col, thickness);
        }
    } catch (std::exception& e) {
        LOGE("%s", e.what());
        JNI_THROW(env, e.what());
    }
}