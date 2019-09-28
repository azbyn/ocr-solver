#include "super_lines.h"

#include "jni_utils.h"

#include <string>
#include <fmt/format.h>
#include <opencv2/imgproc.hpp>

//#define TO_RAD (CV_PI / 180)
//#define TO_DEG (180 / CV_PI)

bool SuperLine::add(const Line& line, int slineSize) {
    bool res = std::abs(mid - line.mid) <= slineSize;
    //LOGD("add: %d, %d (%d)", mid, line.mid, res);
    if (res) {
        mid = ((mid * len*len) + (line.mid * line.len*line.len)) /
              (line.len*line.len + len*len);
        top = std::min(top, line.top);
        bot = std::max(bot, line.bot);
        len = bot - top;
    }
    return res;
}

std::string SuperLine::toString() const {
    //return fmt::format("({}, l {})", mid, len);
    return fmt::format("({})", mid);
}
//const char* SuperLine::c_str() const { return toString().c_str(); }
template <class T>
std::string vecToString(const std::vector<T>& vec) {
    std::string res = "{";
    for (auto& el : vec) {
        res += el.toString() + ", ";
    }
    res += "}";
    return res;
}

void addLine(int slineSize, std::vector<SuperLine>& slines, Line&& line) {
    if (line.len == 0) return;
    auto end = slines.end();
    auto index = end;
    int bestDiff = INT_MAX;
    //LOGD("before");
    //this also sorts the slines by '.mid'
    for (auto it = slines.begin(); it != end; ++it) {
        if (it->add(line, slineSize)) {
            //LOGD("GET ADDED to %s", it->toString().c_str());
            return;
        }
        auto diff = it->mid - line.mid;
        if (diff > 0 && diff < bestDiff) {
            bestDiff = diff;
            index = it;
        }
    }

    /*size_t sz = index - slines.begin();
    LOGD("GET ADDED @%ld", sz);*/

    slines.emplace(index, line);
    /*
    auto index = slines.size();
    auto bestdiff = INT_MAX;// int.max_value;
    auto mid = line.mid ;//(left + right) / 2;
    LOGD("adding %d in %ld", mid, slines.size());
    //logd("adding $linemid in ${slines.size}")
    for (size_t i = 0; i < slines.size(); ++i) {
        auto& sl = slines[i];
        if (sl.add(line, slineSize)) return;
        auto diff = sl.mid - mid;
        if (diff > 0 && diff < bestdiff) {
            bestdiff = diff;
            index = i;
        }
    }
    //logd("matches not found")
    slines.emplace(slines.begin() + index, line);// superline(mid, top, bot))
     */
}

enum class ShouldDraw { No, Yes };

template<ShouldDraw draw, SuperLine::Type type>
void iterateSlines(const std::vector<SuperLine>& slines, cv::Mat* output, int minLength,
        int& totalDiff, int& count) {
    const auto drawLine = [&] (const SuperLine& line) {
        //LOGD("draw line");
        constexpr int thickness = 5;
        const cv::Scalar col(255.0);

        if constexpr (type == SuperLine::Type::Hori) {
            cv::line(*output,
                     cv::Point(line.top, line.mid),
                     cv::Point(line.bot, line.mid),
                     col, thickness);
        }
        else {
            cv::line(*output,
                     cv::Point(line.mid, line.top),
                     cv::Point(line.mid, line.bot),
                     col, thickness);
        }
    };

    //LOGD("slines: %ld:\n%s", slines.size(), vecToString(slines).c_str());
    auto it = slines.begin();
    auto end = slines.end();
    int prev = -1;
    /*
    for (auto& l : slines) {
        if (l.len < minLength) continue;
        if (prev != -1) {
            //logd("diff = ${mid - prev}")
            totalDiff += l.mid - prev;
            ++count;
        }
        prev = l.mid;
        if constexpr (draw == ShouldDraw::Yes) drawLine(l);
    }
    LOGD("count %d", count);*/

    auto beg = slines.begin();
    while (it != end) {
        if (it->len > minLength) {
            prev = it->mid;
            LOGD("first: %d", it - beg);
            if constexpr (draw == ShouldDraw::Yes) drawLine(*it);
            ++it;
            break;
        }
        ++it;
    }
    LOGD("mid %d", it - beg);
    for (; it != end; ++it) {
        if (it->len > minLength) {
            //auto diff = it->mid - prev;
            //LOGD("second %d, dif: %d", it - beg, diff);
            ++count;
            totalDiff += it->mid - prev;
            prev = it->mid;
            if constexpr (draw == ShouldDraw::Yes) drawLine(*it);
        }
    }
}

JNIFUN(jint, superLinesGetDensity) (JNIEnv* env, jobject,
        jlong linesAddr, jlong outputAddr /* might be null*/,
        jint minLength, jint slineSize, jdouble rejectAngle) {
    const auto& lines = addrToMat(linesAddr);
    try {
        std::vector<SuperLine> vertSlines;
        std::vector<SuperLine> horiSlines;

        auto end = lines.end<cv::Vec4i>();
        rejectAngle *= CV_PI / 180;
        for (auto it = lines.begin<cv::Vec4i>(); it != end; ++it) {
            auto x1 = (*it)[0];
            auto y1 = (*it)[1];
            auto x2 = (*it)[2];
            auto y2 = (*it)[3];

            // the angle is guaranteed to be in the first quadrant
            double theta = std::atan2(abs(y2 - y1), abs(x2 - x1));
            //LOGD("mój angle %lf %lf", theta * TO_DEG, theta2 * TO_DEG);
            if (theta < rejectAngle) {
                addLine(slineSize, horiSlines, Line(y1, y2, x1, x2));
            } else if (theta > (CV_PI / 2 - rejectAngle)) {
                addLine(slineSize, vertSlines, Line(x1, x2, y1, y2));
            }
            //merge close super lines?
        }
        LOGD("vlines: %ld:\n%s", vertSlines.size(), vecToString(vertSlines).c_str());
        LOGD("hlines: %ld:\n%s", horiSlines.size(), vecToString(horiSlines).c_str());

        int totalDiff = 0;
        int count = 0;
        auto output = (cv::Mat*) outputAddr;
        using T = SuperLine::Type;
        using D = ShouldDraw;
        LOGD("output: %p", output);
        if (output == nullptr) {
            iterateSlines<D::No, T::Hori>(horiSlines, output, minLength, totalDiff, count);
            iterateSlines<D::No, T::Vert>(vertSlines, output, minLength, totalDiff, count);
        } else {
            //cv::Mat( ).copyTo(output);
            iterateSlines<D::Yes, T::Hori>(horiSlines, output, minLength, totalDiff, count);
            iterateSlines<D::Yes, T::Vert>(vertSlines, output, minLength, totalDiff, count);
        }
        //LOGD("count %d, diff %d", count, totalDiff);

        /*Do this?
            def reject_outliers(data, m=2):
                d = np.abs(data - np.median(data))
                mdev = np.median(d)
                s = d/(mdev if mdev else 1.)
                return np.array(data)[s<m]#  data[abs(data - np.mean(data)) < m * np.std(data)]
         */
        //logd("vert: $vertSlines")
        //logd("hori: $horiSlines")

        if (count == 0) {
            LOGW("super lines diff count == 0");
            return DESIRED_DENSITY;
        } else {
            return totalDiff / count;
        }
    } catch (std::exception& e) {
        LOGE("%s", e.what());
        JNI_THROW(env, e.what());
        return DESIRED_DENSITY;
    }
}
