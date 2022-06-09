#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/time.h>
#include <unistd.h>

#include <android/log.h>
#include <jni.h>

#include "abi.h"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "libisemu", __VA_ARGS__)

struct fault_spec {
    void* nop_beg;
    void* nop_end;
    void* fault_beg;
    void* fault_hit;
    void* fault_end;
};

#define MAX_SIGNAL_HANDLERS 4

struct fault_context {
    void* code;  // Pointer to code that causes the fault.
    int hits;  // Changed upon alignment fault.
    unsigned char scratchpad[16];  // Provides empty spaces for load/store test.
    int handler_count;
    int signum[MAX_SIGNAL_HANDLERS];  // Saved signal handler signum.
    struct sigaction old_sa[MAX_SIGNAL_HANDLERS];  // Saved signal handler.
};

static struct fault_spec spec;
static struct fault_context context;

//jstring
//Java_kr_ac_kaist_isemu_Main_getABI(JNIEnv* env, jobject thiz)
//{
//    return (*env)->NewStringUTF(env, ABI);
//}
jstring
Java_com_lccao_androidemulatordetector_JNIWrapper_getABI(JNIEnv
* env,
jobject thiz
) {
    return (*env)->NewStringUTF(env, ABI);
}

static int install_signal_handler(int signum,
        void (*handler)(int, siginfo_t*, void*)) {
    if (context.handler_count == MAX_SIGNAL_HANDLERS)
        return -1;
    struct sigaction sa;
    sa.sa_sigaction = handler;
    sa.sa_flags = SA_SIGINFO;

    context.signum[context.handler_count] = signum;
    int result = sigaction(signum, &sa, &context.old_sa[context.handler_count]);
    if (result == 0)
        context.handler_count ++;

    return result;
}

static void restore_signal_handler() {
    int i;
    for (i = context.handler_count; i >= 0; i --)
        sigaction(context.signum[i], &context.old_sa[i], NULL);
}

void flush_icache(void* addr, size_t len) {
    __builtin___clear_cache(addr, (void*)((uintptr_t)addr + len));

#if ABI_aremeabi_v7a
    cacheflush((long)addr, (long)len, 0);
#endif
}

static int trigger_fault() {
    long page_size = sysconf(_SC_PAGESIZE);
    void* page = mmap(NULL, page_size, PROT_READ | PROT_WRITE | PROT_EXEC,
            MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (page == MAP_FAILED) {
        LOGD("mmap failed!");
        return -1;
    }

    void* beg = spec.fault_beg;
    void* end = spec.fault_end;
    size_t len = ((uintptr_t)end) - ((uintptr_t)beg);
    LOGD("Copying to %p from %p (%zu bytes)", page, beg, len);
    memcpy(page, beg, len);
    flush_icache(page, len);

    context.code = page;
    context.hits = 0;

    void (*entry)(unsigned char*) = (void (*)(unsigned char*))page;
    int i;
    char buf[100];
    unsigned char* ptr = (unsigned char*)page;
    for (i=0; i<32; i++) {
        sprintf(buf+2*i, "%02x", (unsigned int)ptr[i]);
    }
    unsigned char* scratchpad = &context.scratchpad[0];
    LOGD("Dump: %s", buf);
    LOGD("scratchpad %p", scratchpad);
    scratchpad[1] = 'x';
    LOGD("jumping to %p", page);
    entry(scratchpad);
    return 0;
}

static void patch_faulting_instruction() {
    size_t len = ((uintptr_t)spec.nop_end)
               - ((uintptr_t)spec.nop_beg);
    size_t ofs = ((uintptr_t)spec.fault_hit)
               - ((uintptr_t)spec.fault_beg);
    void* dest = (void*)((uintptr_t)context.code + ofs);
    LOGD("copy to %p from %p size %zu\n", dest, spec.nop_beg, len);
    memcpy(dest, spec.nop_beg, len);
    flush_icache(dest, len);
}

#if ABI_aremeabi_v7a || ABI_arm64_v8a || ABI_x86

static int addr_in_scratchpad(void* ptr) {
    return (&context.scratchpad[0] <= (unsigned char*)ptr)
        && ((unsigned char*)ptr < &context.scratchpad[16]);
}

static unsigned long get_pc(void* uc_) {
    struct ucontext* uc = (struct ucontext*)uc_;
#if ABI_aremeabi_v7a
    return uc->uc_mcontext.arm_pc;
#elif ABI_arm64_v8a
    return uc->uc_mcontext.pc;
#endif
}

static int pc_in_page(unsigned long pc) {
    unsigned long page_size = sysconf(_SC_PAGESIZE);
    unsigned long code_begin = (unsigned long)(context.code);
    unsigned long code_end = code_begin + page_size;
    return (code_begin <= pc && pc < code_end);
}

static void dump_signal(siginfo_t* si, void* uc_) {
    unsigned long pc = get_pc(uc_);
    LOGD("SIGNAL signum=%d errno=%d code=%d pc=%p addr=%p",
            si->si_signo, si->si_errno, si->si_code, (void*)pc, si->si_addr);
    LOGD("code at pc: %08x", *(uint32_t*)pc);
}

void sigbus_handler(int sig, siginfo_t* si, void* uc_) {
    unsigned long pc = get_pc(uc_);
    dump_signal(si, uc_);

    if (si->si_code == BUS_ADRALN && addr_in_scratchpad(si->si_addr)) {
        LOGD("Expected alignment fault (BUS_ADRALN). Patching instruction.");
        patch_faulting_instruction();
        context.hits = 1;
    }
    else if (si->si_code == SI_KERNEL && pc_in_page(pc)) {
        // Some kernels notify alignment fault via si_code == SI_KERNEL.
        // This happens in some Galaxy S3 devices running Android 4.3 ~ 4.4.2.
        LOGD("Expected alignment fault (SI_KERNEL). Patching instruction.");
        patch_faulting_instruction();
        context.hits = 1;
    }
    else {
        LOGD("Unexpected SIGBUS");
    }
}

void sigsegv_handler(int sig, siginfo_t* si, void* uc_) {
    struct ucontext* uc = (struct ucontext*)uc_;
    dump_signal(si, uc_);

    if (addr_in_scratchpad(si->si_addr)) {
        LOGD("Segfault in the scratchpad. Patching instruction.");
        patch_faulting_instruction();
        context.hits = 1;
    }
    else {
        LOGD("Unexpected SIGSEGV");
    }
}

static int execute_check_emu() {
    if (install_signal_handler(SIGBUS, sigbus_handler) < 0)
        return -2;
    if (install_signal_handler(SIGSEGV, sigsegv_handler) < 0) {
        restore_signal_handler();
        return -2;
    }

    if (trigger_fault() != 0) {
        restore_signal_handler();
        return -2;
    }

    restore_signal_handler();

    return (context.hits == 0);
}

#endif // ABI_aremeabi_v7a || ABI_arm64_v8a

#if ABI_aremeabi_v7a

#include "stubs_armeabi_v7a.h"

static int check_emu_armeabi_v7a() {
    memset(&spec, 0, sizeof(spec));
    memset(&context, 0, sizeof(context));
    spec.nop_beg = &nop_armeabi_v7a_beg;
    spec.nop_end = &nop_armeabi_v7a_end;
    spec.fault_beg = &fault_armeabi_v7a_ldm_beg;
    spec.fault_hit = &fault_armeabi_v7a_ldm_hit;
    spec.fault_end = &fault_armeabi_v7a_ldm_end;

    return execute_check_emu();
}

#endif // ABI_aremeabi_v7a

#if ABI_arm64_v8a

#include "stubs_arm64_v8a.h"

static int check_emu_arm64_v8a() {
    memset(&spec, 0, sizeof(spec));
    memset(&context, 0, sizeof(context));
    spec.nop_beg = &nop_arm64_v8a_beg;
    spec.nop_end = &nop_arm64_v8a_end;
    spec.fault_beg = &fault_arm64_v8a_ldxr_beg;
    spec.fault_hit = &fault_arm64_v8a_ldxr_hit;
    spec.fault_end = &fault_arm64_v8a_ldxr_end;

    return execute_check_emu();
}
#endif // ABI_arm64_v8a

#if ABI_x86

#include "stubs_x86.h"

static int check_emu_x86() {

    memset(&spec, 0, sizeof(spec));
    memset(&context, 0, sizeof(context));
    spec.nop_beg = &nop_x86_beg;
    spec.nop_end = &nop_x86_end;
    spec.fault_beg = &fault_x86_beg;
    spec.fault_hit = &fault_x86_hit;
    spec.fault_end = &fault_x86_end;

    return execute_check_emu();
    // trigger unaligned vectorization
//    asm ("mov %rsp , %rax \n"
//         "inc %rax \n"
//         "movntps %xmm0,(% rax )") ;
//    return -1;

//    asm ("mov %esp , %eax \n"
//         "inc %eax \n"
//         "movntps %xmm0,(% eax )") ;
//    return -1;
}

#endif ABI_x86

// Return values:
//  -2 -> internal error
//  -1 -> unsupported architecture
//  0 -> real hardware
//  1 -> emulator
jint
Java_com_ac_kaist_isemu_Main_isemu(JNIEnv* env, jobject thiz)
{
#if ABI_aremeabi_v7a
    return check_emu_armeabi_v7a();
#elif ABI_arm64_v8a
    return check_emu_arm64_v8a();
#elif ABI_x86
    return check_emu_x86();
#else
    return -1;
#endif
}
jint
Java_com_lccao_androidemulatordetector_JNIWrapper_isemu(JNIEnv
* env,
jobject thiz
) {
return Java_com_ac_kaist_isemu_Main_isemu(env, thiz);
}
