#ifndef AZBYN_OCR_SUPER_LINES_H
#define AZBYN_OCR_SUPER_LINES_H

#include <vector>

struct Line {
    int mid, top, bot, len;
    constexpr Line(int mid, int top, int bot)
            : mid(mid), top(std::min(top, bot)), bot(std::max(bot,top)),
              len(this->bot - this->top) {}

    constexpr Line(int left, int right, int top, int bot)
            : Line((left+right)/2, top, bot) {}
};
struct SuperLine {
    enum class Type { Hori, Vert };
    int mid, top, bot, len;

    //constexpr int len() const { return bot - top; }
    //constexpr SuperLine(int mid, int top, int bot)
    //    : mid(mid), top(top), bot(bot), len(bot-top) {}
    constexpr SuperLine(const Line& l): mid(l.mid), top(l.top), bot(l.bot), len(l.len) {}

    bool add(const Line& line, int slineSize);
    /* {
        bool res = std::abs(mid - line.mid) <= slineSize;
        if (res) {
            //int len = this->len();
            if (line.len != 0) {
                //mid = ((mid * len) + (lineMid * lineLen)) / (lineLen + len)
                mid = ((mid * len*len) + (line.mid * line.len*line.len)) /
                      (line.len*line.len + len*len);
                top = std::min(top, line.top);
                bot = std::max(bot, line.bot);
                len = bot - top;
            }
        }
        return res;
    }*/
    std::string toString() const;
    //const char* c_str() const;
};
void addLine(int slineSize, std::vector<SuperLine>& slines, const Line& line);

#endif //AZBYN_OCR_SUPER_LINES_H
