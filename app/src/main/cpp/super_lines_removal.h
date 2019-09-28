#ifndef AZBYN_OCR_SUPER_LINES_REMOVAL_H
#define AZBYN_OCR_SUPER_LINES_REMOVAL_H

#include <vector>
#include <jni.h>

struct SlineMids {
    std::vector<int> hori;
    std::vector<int> vert;
    static SlineMids* fromAddr(jlong addr) { return (SlineMids*) addr;  }
};

#endif //AZBYN_OCR_SUPER_LINES_REMOVAL_H
