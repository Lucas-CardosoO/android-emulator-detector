#ifndef ABI_H
#define ABI_H

// Defines a string constant ABI, and
// boolean values ABI_ARMv7a.

#if defined(__arm__)
  #if defined(__ARM_ARCH_7A__)
    #define ABI_aremeabi_v7a 1
    #if defined(__ARM_NEON__)
      #if defined(__ARM_PCS_VFP)
        #define ABI "armeabi-v7a/NEON (hard-float)"
      #else
        #define ABI "armeabi-v7a/NEON"
      #endif
    #else
      #if defined(__ARM_PCS_VFP)
        #define ABI "armeabi-v7a (hard-float)"
      #else
        #define ABI "armeabi-v7a"
      #endif
    #endif
  #else
   #define ABI "armeabi"
  #endif
#elif defined(__i386__)
#define ABI "x86"
#elif defined(__x86_64__)
#define ABI "x86_64"
#elif defined(__mips64)  /* mips64el-* toolchain defines __mips__ too */
#define ABI "mips64"
#elif defined(__mips__)
#define ABI "mips"
#elif defined(__aarch64__)
  #define ABI_arm64_v8a 1
#define ABI "arm64-v8a"
#else
#define ABI "unknown"
#endif

#endif /* ABI_H */
