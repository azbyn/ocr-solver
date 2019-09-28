#include "jni_utils.h"

#include <vector>
#include <stdexcept>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

#pragma clang diagnostic push
#pragma ide diagnostic ignored "OCDFAInspection"
#pragma ide diagnostic ignored "misc-non-private-member-variables-in-classes"
#pragma ide diagnostic ignored "OCUnusedGlobalDeclarationInspection"

class BlobberData {
    struct Rect {
        jint l, t, r, b;
        constexpr Rect(jint l, jint t, jint r, jint b): l(l), t(t), r(r), b(b) {}
        cv::Rect toCvRect(jint startX) const { return cv::Rect(l+startX, t, r-l+1, b-t+1); }

        //constexpr void expandDown(int y) { if (b < y) b = y; }
        //constexpr void expandLeft(int x) { if (l > x) l = x; }
        //constexpr void expandRight(int x){ if (r < x) r = x; }
    };
    cv::Mat buff;
    jint startX;
    Rect roi;

    uchar* buffLine = nullptr;
public:
    explicit BlobberData()
            : buff(DESIRED_DENSITY*2, DESIRED_DENSITY*2, CV_8U),
            startX(DESIRED_DENSITY),
            roi(0,0,0,0) { }

    cv::Rect getGlobalBounds(int imgX, int imgY) const {
        auto cvRect = roi.toCvRect(0);
        cvRect.x += imgX;// - startX;
        cvRect.y += imgY;
        return cvRect;
    }
    void resetBuffer() {
        buff.setTo(cv::Scalar(0.0));
        roi = Rect(0, 0, 0, 0);
    }
    void startLine(int buffX, int buffY) {
        if (roi.l > buffX) roi.l = buffX;
        if (roi.b < buffY) roi.b = buffY;
        buffX += startX;
        if (buffX < 0) {
            int padding = std::max(DESIRED_DENSITY, -buffX);
            startX += padding;
            //buffX += padding;
            //LOGD("add left %d %d %d", buffX, padding, startX);
            copyMakeBorder(buff, buff, 0, 0, padding, 0, cv::BORDER_CONSTANT);
        }
        //buffLine = getLine(buffY);

        if (buffY >= buff.rows) {
            //LOGD("ADD BUFF ROW (%d)", buffY);
            copyMakeBorder(buff, buff, 0, DESIRED_DENSITY, 0, 0, cv::BORDER_CONSTANT);
        } else if (buffY < 0) {
            LOGE("y is negative? (%d)", buffY);
            throw std::runtime_error("y is negative?");
        }
        buffLine = buff.ptr(buffY);
    }
    void setBuffAt(int buffX, int buffY) {
        //buffX += startX;
        //int buffX = startX + cur.x - imgX;
        if (buffX + startX >= buff.cols) {
            //LOGD("add right");
            copyMakeBorder(buff, buff, 0, 0, 0, DESIRED_DENSITY, cv::BORDER_CONSTANT);
            buffLine = buff.ptr(buffY);
        }
        if (roi.r < buffX) roi.r = buffX;
        buffLine[buffX+startX] = 255;
    }
    cv::Mat getCropped() const { return buff(roi.toCvRect(startX)); }
};

//height = rows
//width = cols
class Blobber {
    //using cv::Point;
    cv::Mat mask;
    //const cv::Mat& img;
    ArrayList& result;
    BlobberData data;
    std::vector<cv::Rect> boundsArr;

public:
    Blobber(const cv::Mat& mask, ArrayList& result)
            : result(result), data() {
        mask.copyTo(this->mask);
    }

    const std::vector<cv::Rect>& getBounds() const { return boundsArr; }
    void blobbing() {
        //LOGD("blobbin' %d %d", img.cols, img.rows);
        // we iterate mask from left to right, top to bottom until we find a non-zero pixel
        // then we add all the neighbouring pixels (like a flood fill) to buff
        for (int y = 0; y < mask.rows; ++y) {
            uchar* p = mask.ptr(y);
            for (int x = 0; x < mask.cols; ++x, ++p) {
                if (*p != 0) {
                    extractBlob(x, y);
                    auto cropped = data.getCropped();
                    auto bounds = data.getGlobalBounds(x, y);
                    result.add(cropped);
                    boundsArr.push_back(bounds);
                }
            }
        }
    }
private:
    void extractBlob(int imgX, int imgY) {
        // every non-zero pixel we find we set to 0 in img (so that we don't find it again)
        // and we add it to our buffer
        //LOGD("extract blob %d %d", imgX, imgY);
        data.resetBuffer();

        // scanline search with stack
        // as seen here: https://lodev.org/cgtutor/floodfill.html
        std::vector<cv::Point> stack;
        stack.emplace_back(imgX, imgY);
        cv::Point cur;
        bool spanAbove, spanBelow;
        int width = mask.cols;
        int height = mask.rows;
        int blobSize = 0;
        while (!stack.empty()) {
            //LOGD("stack sz = %ld", stack.size());
            cur = stack.back();
            //LOGD("pt %d, %d", cur.x, cur.y);
            stack.pop_back();

            int buffY = cur.y - imgY;
            uchar* line = mask.ptr(cur.y);
            while (cur.x >= 0 && line[cur.x] != 0) --cur.x;
            ++cur.x;

            if (line[cur.x] == 0) continue;
            spanAbove = spanBelow = false;
            data.startLine(cur.x - imgX, buffY);

            uchar* lineAbove = cur.y > 0 ? mask.ptr(cur.y-1) : nullptr;
            uchar* lineBelow = cur.y < height - 1 ? mask.ptr(cur.y+1): nullptr;
            for (; cur.x < width && line[cur.x] != 0; ++cur.x) {
                line[cur.x] = 0;
                data.setBuffAt(cur.x - imgX, buffY);
                ++blobSize;
                if (!spanAbove && lineAbove && lineAbove[cur.x] != 0) {
                    stack.emplace_back(cur.x, cur.y-1);
                    spanAbove = true;
                    //LOGD("TOP sz = %ld", stack.size());
                } else if (spanAbove && lineAbove && lineAbove[cur.x] == 0) {
                    spanAbove = false;
                    //LOGD("!TOP sz = %ld", stack.size());
                }

                if (!spanBelow && lineBelow && lineBelow[cur.x] != 0 ) {
                    stack.emplace_back(cur.x, cur.y+1);
                    spanBelow = true;
                    //LOGD("BOT sz = %ld", stack.size());
                } else if (spanBelow && lineBelow && lineBelow[cur.x] == 0) {
                    spanBelow = false;
                    //LOGD("!BOT sz = %ld", stack.size());
                }
                //imshow("frame", img);
            }
        }
        //LOGD("\tEND found blob of size %d", blobSize);
        //LOGD("(%d, %d, %d, %d)", blobRoi.l, blobRoi.t, blobRoi.r, blobRoi.b);
        //blobRoi.log();
    }
};
JNIFUN(jintArray, blobbing) (JNIEnv* env, jobject /*thiz*/,
                             jlong maskAddr,
                             jobject resultList) {
    const auto& mask = *(cv::Mat*)maskAddr;
    try {
        ArrayList result(env, resultList);

        Blobber b(mask, result);
        b.blobbing();
        auto& bounds = b.getBounds();
        static_assert(sizeof(cv::Rect) == 4 * sizeof(jint));
        jintArray arr = env->NewIntArray(bounds.size()*4);
        env->SetIntArrayRegion(arr, 0, bounds.size()*4, (const jint*) bounds.data());

        return arr;
    } catch (std::exception& e) {
        JNI_THROW(env, e.what());
    }
    return nullptr;
}

void bitwiseAndBlobImpl(const cv::Mat& img, cv::Mat& mat, const cv::Rect& roi,
                        const cv::Mat& dilateKer, const cv::Mat& erodeKer) {
    cv::dilate(mat, mat, dilateKer);
    cv::erode(mat, mat, erodeKer);
    cv::bitwise_and(mat, img(roi), mat);
    cv::threshold(mat, mat, 0.0, 255.0, cv::THRESH_TOZERO | cv::THRESH_OTSU);
}
JNIFUN(void, bitwiseAndSingleBlob) (JNIEnv* env, jobject,
                                    jint x, jint y, jint w, jint h,
                                    jlong blobAddr, jlong imgAddr,
                                    jint dilateVal, jint erodeVal) {
    auto& blob = addrToMat(blobAddr);
    const auto& img = addrToMat(imgAddr);
    try {
        bitwiseAndBlobImpl(img, blob, cv::Rect(x, y, w, h),
                cv::Mat::ones(dilateVal, dilateVal, CV_8U),
                cv::Mat::ones(erodeVal, erodeVal, CV_8U));
    } catch (std::exception& e) {
        JNI_THROW(env, e.what());
    }
}
JNIFUN(void, bitwiseAndBlobs) (JNIEnv* env, jobject,
        jintArray boundsArr, jobject blobsList, jlong imgAddr,
        jint dilateVal, jint erodeVal) {
    const auto& img = addrToMat(imgAddr);
    try {
        ArrayList blobs(env, blobsList);
        auto dilateKer = cv::Mat::ones(dilateVal, dilateVal, CV_8U);
        auto erodeKer = cv::Mat::ones(erodeVal, erodeVal, CV_8U);
        jint* bounds = env->GetIntArrayElements(boundsArr, nullptr);
        for (int i = 0, sz = blobs.size(); i < sz; ++i) {
            cv::Mat& mat = blobs.getMat(i);
            cv::Rect roi(bounds[i*4], bounds[i*4+1], bounds[i*4+2], bounds[i*4+3]);

            bitwiseAndBlobImpl(img, mat, roi, dilateKer, erodeKer);
        }
    } catch (std::exception& e) {
        JNI_THROW(env, e.what());
    }
}
#pragma clang diagnostic pop
