cmake_minimum_required(VERSION 3.4.1)

#set(CMAKE_VERBOSE_MAKEFILE on)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++17 -g")
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++17 -O3")

add_library(jni SHARED
        src/main/cpp/fmt/format.cc
        src/main/cpp/blobbing.cpp
        src/main/cpp/lines.cpp
        src/main/cpp/super_lines.cpp
        src/main/cpp/super_lines_removal.cpp
        src/main/cpp/lines_mask.cpp
        src/main/cpp/cv_utils.cpp
        src/main/cpp/misc.cpp
        src/main/cpp/jni_utils.cpp)

include_directories(src/main/cpp)

target_include_directories(jni PRIVATE "${CMAKE_SOURCE_DIR}/src/main/jniIncludes")

add_library(lib_opencv SHARED IMPORTED)

set_target_properties(lib_opencv PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libopencv_java4.so)

find_library(log-lib
             log)

target_link_libraries(jni
            #android
            ${log-lib}
            lib_opencv)
