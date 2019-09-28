#include "super_lines_removal.h"

#include "super_lines.h"
#include "jni_utils.h"

#include <opencv2/imgproc.hpp>

#include <stdexcept>

// int arithmetic is recommended
bool isSmall(int n) { return n <= DESIRED_DENSITY * 3 / 4; /*.75*/ }

//private fun isSuperSmall(n: Int) = n <= DESIRED_DENSITY / 3 // .33
bool isBig(int n) { return n >= DESIRED_DENSITY *3 / 2; /*1.5*/ }

bool addIfBig(int diff, int start, std::vector<int>& mids) {
    bool res = isBig(diff);
    if (res) {
        LOGD("diff %d, %d", diff, start);
        // equivalent to round for float
        int newLines = (diff + DESIRED_DENSITY/2) / DESIRED_DENSITY;
        LOGD("isBig; new %d", newLines);
        if (newLines > 10) {
            LOGW("WOT");
            return res;
        }
        //val distance = diff / newLines
        for (int j = 0; j <= newLines; ++j) {
            auto value = start + j * DESIRED_DENSITY;
            LOGD("insert %d: %d", j, value);
            mids.push_back(value);
        }
    }
    return res;
}

void addIfBigReversed(int diff, int end, int index, std::vector<int>& mids) {
    if (isBig(diff)) {
        int newLines = (diff + DESIRED_DENSITY/2) / DESIRED_DENSITY;
        LOGD("isBigReversed %d; new: %d", index, newLines);
        //val distance = diff / newLines
        for (int j = 1; j < newLines; ++j) {
            int value = end - j * DESIRED_DENSITY; //* distance
            LOGD("insert %d: %d", j, value);
            mids.insert(mids.begin() + index, value);
        }
    }
};

void evalSlines(const std::vector<SuperLine>& slines,
                std::vector<int>& mids,
                int minLength,
                int bottom) {
    mids.clear();//?
    // If there are no slines, there's nothing to do. Great!
    if (slines.empty()) {
        LOGW("Empty slines?");
        return;
    }

    int i = 0;
    int len = slines.size() - 1;
    const auto getCurr = [&](int& i) {
        // We find the first sline bigger than [minLength],
        // If we don't find any, we throw IndexOutOfBoundsException
        // indicating that it finished
        while (slines[i].len <= minLength) {
            // since len = size - 1, we check with '>', not '>='
            if (++i > len) throw std::out_of_range(nullptr);
        }
        return slines[i].mid;
    };

    int next = -1;
    // the exception simplifies a lot of things
    try {
        mids.push_back(getCurr(i));
        ++i;

        //this should be handled with slineSize

        /*val c = getCurr()
        // if the difference between lines is very small (ie lines are close together)
        // we evaluate that as a single line in the middle of the initial lines
        if (isSuperSmall(c - mids[0])) {
            logd("first super small")
            mids[0] = (c + mids[0]) / 2
            ++i
        }*/

        /*
        // If there's a big space without any lines, we add as many lines
        // as would fit in that space.
        // We can do this because we know that we should have a line every
        // [DESIRED_DENSITY] pixels.
        val c = getCurr()
        //if (addIfBig(diff = c - mids[0], start = mids[0], mids = mids)) ++i
        if (addIfBig(diff = c - mids[0], start = c, mids = mids, multiplier=-1)) ++i
        // we iterate over slines, adding to mid where necessary
         */

        while (i < len) {
            int curr = getCurr(i);
            // having references would have been useful
            int j = i + 1;
            while (slines[j].len <= minLength) {
                if (++j > len) throw std::out_of_range("");
            }
            next = getCurr(j);

            auto diffL = curr - mids.back();
            auto diffR = next - curr;
            LOGD("d_: %d, %d, %d", curr, next, mids.back());
            LOGD("diffs: %d, %d", diffL, diffR);

            // If there's a big space without any lines, we add as many lines
            // as would fit in that space.
            // We can do this because we know that we should have a line every
            // [DESIRED_DENSITY] pixels.
            if (addIfBig(diffR, curr, mids)){
                //addIfBig(diff = diffR, start = curr, mids = mids) -> Unit
                LOGD("big, skipping");
            }
            // having 2 close lines isn't that bad
            /*
            // if the difference between lines is very small (ie lines are close together)
            // we evaluate that as a single line in the middle of the initial lines
            isSuperSmall(diffR) -> {
                    logd("isSuperSmall")
                    mids.add((curr + next) / 2)
                    ++i
            }
            */
            // this is likely to occur if we have a fake line in the middle of
            // two normal lines
            else if (isSmall(diffL) && isSmall(diffR)) {
                LOGD("both small, skipping");
            }

            /*
        // we have the regular grid, then a fake line to the left/right of it
        isSmall(diffL) && !isSmall(diffR) -> {
            mids[mids.size-1] = curr
        }
        !isSmall(diffL) && isSmall(diffR) -> {
            mids.add(curr)
            ++i
        }*/
            else {
                // the line is good, so we add it to [mids]
                mids.push_back(curr);
            }
            ++i;
        }
    } catch (std::out_of_range&) {
        LOGD("caught");
    }

    // We skipped the last line in the loop, so we add it now
    if (next != -1)
        mids.push_back(next);
    //if there's enough space add lines at the beginning
    addIfBigReversed(mids[0], mids[0], 0, mids);

    //if there's enough space add lines at the end
    auto curr = mids[mids.size()-1];
    //if (addIfBig(diff = bottom - curr, start = curr, mids = mids))
    auto diff = bottom - curr;
    addIfBig(diff, curr, mids);
    if (mids.size() < 2) return;


    //mids.add(0,0)
    //mids.add(mids.size, bottom)
    // The top and bottom tend to get fake lines from paper edges and such
    /*
    if (isSmall(mids[1] - mids[0])) {
        logd("removed first")
        mids.removeAt(0)
    }
    val last = mids.size -1
    if (isSmall(mids[last] - mids[last-1])) {
        logd("removed last")
        mids.removeAt(last)
    }
*/
    /*LOGD("lines: $mids");
    var s = "diffs: "
    for (j in 1 until mids.size) {
        s += "${mids[j] - mids[j-1]}, "
    }
    logd(s)
    */
}

enum class ShouldDraw { No, Yes };

template<ShouldDraw draw, SuperLine::Type type>
void drawLines(const std::vector<SuperLine>& slines,
        std::vector<int>& mids,
        cv::Mat* output,
        const cv::Size& size,
        double scale,
        int minLength) {

    int bottom = (type == SuperLine::Type::Vert) ? size.width : size.height;

    if constexpr (draw == ShouldDraw::Yes) {
        int lineMaxLength = (type == SuperLine::Type::Vert) ? size.height: size.width;

        const auto drawLine = [&] (int m, const cv::Scalar& col, int thickness) {
            if constexpr (type == SuperLine::Type::Vert) {
                cv::line(*output,
                         cv::Point(m, 0),
                         cv::Point(m, lineMaxLength),
                         col, thickness);
            } else {
                cv::line(*output,
                         cv::Point(0, m),
                         cv::Point(lineMaxLength, m),
                         col, thickness);
            }
        };

        evalSlines(slines, mids, minLength, bottom);

        const cv::Scalar col1(255.0);
        const cv::Scalar col2(127.0);
        const int thickness1 = 5;// int(5 * scale);
        const int thickness2 = 3;// int(3 * scale);

        for (auto m : mids) {
            drawLine(m, col1, thickness1);
        }
        for (auto& sl : slines) {
            drawLine(sl.mid, col2, thickness2);
        }
    } else {
        evalSlines(slines, mids, minLength, bottom);
    }
}

//superLinesRemoval(
// linesAddr=lines.nativeObj,
// rejectAngle=inViewModel.rejectAngle,
// scale=getViewModel<ThresholdFragment.VM>().inverseScale,
// )
JNIFUN(void, superLinesRemoval) (JNIEnv* env, jobject,
        jlong linesAddr, jlong outputAddr /* might be null */,
        jlong midsAddr,
        jint width, jint height,
        jint minLength, jint slineSize,
        jdouble rejectAngle, jdouble scale) {
    const auto& lines = addrToMat(linesAddr);
    cv::Size size(width, height);
    SlineMids& mids = *SlineMids::fromAddr(midsAddr);
    try {
        std::vector<SuperLine> vertSlines;
        std::vector<SuperLine> horiSlines;

        auto end = lines.end<cv::Vec4i>();
        rejectAngle *= CV_PI / 180;
        for (auto it = lines.begin<cv::Vec4i>(); it != end; ++it) {
            double x1 = (*it)[0]*scale;
            double y1 = (*it)[1]*scale;
            double x2 = (*it)[2]*scale;
            double y2 = (*it)[3]*scale;

            // the angle is guaranteed to be in the first quadrant
            double theta = std::atan2(abs(y2 - y1), abs(x2 - x1));

            if (theta < rejectAngle) {
                addLine(slineSize, horiSlines, Line((int) y1, (int) y2, (int) x1, (int) x2));
            } else if (theta > (CV_PI / 2 - rejectAngle)) {
                addLine(slineSize, vertSlines, Line((int) x1, (int) x2, (int) y1, (int) y2));
            }
        }
        auto output = (cv::Mat*) outputAddr;
        using T = SuperLine::Type;
        using D = ShouldDraw;
        LOGD("output: %p", output);
        if (output == nullptr) {
            drawLines<D::No, T::Hori>(horiSlines, mids.hori, output, size, scale, minLength);
            drawLines<D::No, T::Vert>(vertSlines, mids.vert, output, size, scale, minLength);
        } else {
            cv::Mat(size, CV_8U).copyTo(*output);
            drawLines<D::Yes, T::Hori>(horiSlines, mids.hori, output, size, scale, minLength);
            drawLines<D::Yes, T::Vert>(vertSlines, mids.vert, output, size, scale, minLength);
        }
    } catch (std::exception& e) {
        LOGE("%s", e.what());
        JNI_THROW(env, e.what());
    }
}
JNIFUN(jlong, newSlineMids) (JNIEnv*, jobject) {
    return (jlong) new SlineMids();
}
JNIFUN(jlong, delSlineMids) (JNIEnv*, jobject, jlong addr) {
    SlineMids* mids = SlineMids::fromAddr(addr);
    delete mids;
}
