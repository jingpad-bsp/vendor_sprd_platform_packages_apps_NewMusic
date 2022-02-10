LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := glide-music:libs/glide-music.jar
include $(BUILD_MULTI_PREBUILT)

include $(CLEAR_VARS)

#LOCAL_RESOURCE_DIR := packages/apps/DreamMusic/res
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
#LOCAL_RESOURCE_DIR += frameworks/support/design/res
#LOCAL_RESOURCE_DIR += frameworks/support/v7/appcompat/res


LOCAL_MODULE_TAGS := optional

LOCAL_PRIVILEGED_MODULE := true
LOCAL_USE_AAPT2 := true

LOCAL_STATIC_ANDROID_LIBRARIES := \
        $(ANDROID_SUPPORT_DESIGN_TARGETS) \
        android-support-v13 \

LOCAL_STATIC_JAVA_LIBRARIES := \
        android-support-v4 \
        android-support-v7-appcompat \
        glide-music
        #android-support-design \

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
src/com/android/music/IMediaPlaybackService.aidl

LOCAL_PACKAGE_NAME := NewMusic
LOCAL_OVERRIDES_PACKAGES := Music

# SPRD: remove
#LOCAL_SDK_VERSION := current
LOCAL_PRIVATE_PLATFORM_APIS := true


#LOCAL_PROGUARD_FLAG_FILES := proguard.flags
LOCAL_AAPT_FLAGS := --auto-add-overlay
#LOCAL_AAPT_FLAGS += --extra-packages android.support.design
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.appcompat

#LOCAL_PROGUARD_FLAG_FILES := ../../../../../../frameworks/support/design/proguard-rules.pro
LOCAL_PROGUARD_FLAG_FILES += proguard.flags

include $(BUILD_PACKAGE)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
