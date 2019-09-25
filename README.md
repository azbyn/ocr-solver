Ocr Solver
====================================
Application for solving math equations from a photo

To compile, download the latest OpenCV release for Android from here:
https://opencv.org/releases/
and run:
```bash
unzip opencv-4.x.x-android-sdk.zip
git clone https://github.com/azbyn/ocr-solver
cp -r OpenCV-android-sdk/sdk/native/libs/ ocr-solver/app/src/main/jniLibs/
cp -r OpenCV-android-sdk/sdk/native/jni/include/ ocr-solver/app/src/main/jniIncludes/
```

