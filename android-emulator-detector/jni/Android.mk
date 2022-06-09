LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := isemu

LOCAL_SRC_FILES := isemu.c
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
	LOCAL_SRC_FILES += stubs_armeabi_v7a.s
endif
ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
	LOCAL_SRC_FILES += stubs_arm64_v8a.s
endif

LOCAL_LDLIBS := -llog -lc
include $(BUILD_SHARED_LIBRARY)
