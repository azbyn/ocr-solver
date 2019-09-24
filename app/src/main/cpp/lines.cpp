#include "jni_utils.h"

#include <opencv2/imgproc.hpp>

JNIFUN(void, linesExtract) (JNIEnv* env, jobject,
        jlong matAddr, jlong linesAddr, jlong outputAddr,
        jint thresh, jdouble length, jdouble rejectAngle) {
    try {
        const auto &baseMat = *(cv::Mat *) matAddr;
        auto &lines = *(cv::Mat *) linesAddr;
        constexpr double maxLineGap = 20.0;
        cv::HoughLinesP(baseMat, lines, 1.0, CV_PI / 180, thresh,
                        length, maxLineGap);
        if (outputAddr) {
            auto &output = *(cv::Mat *) outputAddr;
            constexpr int thickness = 2;
            auto end = lines.end<cv::Vec4i>();
            rejectAngle *= CV_PI / 180;
            double rejectAngleHori = (CV_PI / 2) - rejectAngle;
            output.setTo(cv::Scalar(0));
            for (auto it = lines.begin<cv::Vec4i>(); it != end; ++it) {
                cv::Point p1((*it)[0], (*it)[1]);
                cv::Point p2((*it)[2], (*it)[3]);
                double theta = atan2(abs(p2.y - p1.y), abs(p2.x - p1.x));
                if (theta < rejectAngle) {
                    cv::line(output, p1, p2, cv::Scalar(0, 0, 255), thickness);
                } else if (theta > rejectAngleHori) {
                    cv::line(output, p1, p2, cv::Scalar(0, 255, 0), thickness);
                }
            }
        }
    } catch (std::exception& e) {
        LOGE("%s", e.what());
        JNI_THROW(env, e.what());
    }
}
