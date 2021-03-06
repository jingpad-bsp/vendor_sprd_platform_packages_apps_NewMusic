LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := optional

LOCAL_JAVA_LIBRARIES := android.test.runner

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := NewMusicTests

LOCAL_INSTRUMENTATION_FOR := NewMusic

LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)

# Include all makefiles in subdirectories
include $(call all-makefiles-under,$(LOCAL_PATH))