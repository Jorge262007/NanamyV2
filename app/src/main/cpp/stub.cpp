#include <dlfcn.h>
#include <stdio.h>
#include <android/log.h>

#define TAG "OCL_LOADER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static void* ocl_handle = nullptr;

extern "C" void* get_ocl_func(const char* name) {
    if (!ocl_handle) {
        const char* libs[] = {
            "libOpenCL.so",
            "libGLES_mali.so",
            "libPVROCL.so",
            "libOpenCL-pixel.so",
            "libGLES_tesla.so",
            "libOpenCL-qti.so",
            "libadj_ocl.so",
            "libOpenCL-mali.so",
            "libOpenCL-mesa.so"
        };
        for (auto lib : libs) {
            // Try loading without path first (let system find it in public namespace)
            ocl_handle = dlopen(lib, RTLD_NOW);
            if (ocl_handle) {
                LOGI("OpenCL Loader: Success, loaded %s", lib);
                break;
            }
        }
    }
    if (!ocl_handle) {
        static bool logged_fail = false;
        if (!logged_fail) {
            LOGE("OpenCL Loader: CRITICAL - No OpenCL library found on this device!");
            logged_fail = true;
        }
        return nullptr;
    }
    void* symbol = dlsym(ocl_handle, name);
    if (!symbol) {
        LOGE("OpenCL Loader: Symbol %s NOT found in library", name);
    }
    return symbol;
}

#define OCL_W(name, ret, sig, args, err_ret) \
    extern "C" ret name sig { \
        typedef ret (*func_ptr_t) sig; \
        static func_ptr_t func_ptr = (func_ptr_t)get_ocl_func(#name); \
        if (func_ptr) return func_ptr args; \
        return (ret)err_ret; \
    }

#define CL_ERR -1

OCL_W(clGetPlatformIDs, int, (unsigned int a, void* b, unsigned int* c), (a, b, c), CL_ERR)
OCL_W(clGetDeviceIDs, int, (void* a, unsigned long long b, unsigned int c, void* d, unsigned int* e), (a, b, c, d, e), CL_ERR)
OCL_W(clCreateContext, void*, (void* a, unsigned int b, void* c, void* d, void* e, int* f), (a, b, c, d, e, f), nullptr)
OCL_W(clCreateCommandQueue, void*, (void* a, void* b, unsigned long long c, int* d), (a, b, c, d), nullptr)
OCL_W(clCreateBuffer, void*, (void* a, unsigned long long b, unsigned long long c, void* d, int* e), (a, b, c, d, e), nullptr)
OCL_W(clCreateProgramWithSource, void*, (void* a, unsigned int b, const char** c, const unsigned long* d, int* e), (a, b, c, d, e), nullptr)
OCL_W(clBuildProgram, int, (void* a, unsigned int b, void* c, const char* d, void* e, void* f), (a, b, c, d, e, f), CL_ERR)
OCL_W(clCreateKernel, void*, (void* a, const char* b, int* c), (a, b, c), nullptr)
OCL_W(clSetKernelArg, int, (void* a, unsigned int b, unsigned long c, const void* d), (a, b, c, d), CL_ERR)
OCL_W(clEnqueueNDRangeKernel, int, (void* a, void* b, unsigned int c, const unsigned long* d, const unsigned long* e, const unsigned long* f, unsigned int g, void* h, void* i), (a, b, c, d, e, f, g, h, i), CL_ERR)
OCL_W(clFinish, int, (void* a), (a), CL_ERR)
OCL_W(clReleaseKernel, int, (void* a), (a), CL_ERR)
OCL_W(clReleaseProgram, int, (void* a), (a), CL_ERR)
OCL_W(clReleaseCommandQueue, int, (void* a), (a), CL_ERR)
OCL_W(clReleaseContext, int, (void* a), (a), CL_ERR)
OCL_W(clReleaseMemObject, int, (void* a), (a), CL_ERR)
OCL_W(clEnqueueReadBuffer, int, (void* a, void* b, unsigned int c, unsigned long d, unsigned long e, void* f, unsigned int g, void* h, void* i), (a, b, c, d, e, f, g, h, i), CL_ERR)
OCL_W(clEnqueueWriteBuffer, int, (void* a, void* b, unsigned int c, unsigned long d, unsigned long e, const void* f, unsigned int g, void* h, void* i), (a, b, c, d, e, f, g, h, i), CL_ERR)
OCL_W(clGetProgramBuildInfo, int, (void* a, void* b, unsigned int c, unsigned long d, void* e, unsigned long* f), (a, b, c, d, e, f), CL_ERR)
OCL_W(clGetDeviceInfo, int, (void* a, unsigned int b, unsigned long c, void* d, unsigned long* e), (a, b, c, d, e), CL_ERR)
OCL_W(clGetEventInfo, int, (void* a, unsigned int b, unsigned long c, void* d, unsigned long* e), (a, b, c, d, e), CL_ERR)
OCL_W(clReleaseEvent, int, (void* a), (a), CL_ERR)
OCL_W(clWaitForEvents, int, (unsigned int a, void* b), (a, b), CL_ERR)
OCL_W(clGetPlatformInfo, int, (void* a, unsigned int b, unsigned long c, void* d, unsigned long* e), (a, b, c, d, e), CL_ERR)
OCL_W(clCreateProgramWithBinary, void*, (void* a, unsigned int b, void* c, const unsigned long* d, const unsigned char** e, int* f, int* g), (a, b, c, d, e, f, g), nullptr)
OCL_W(clGetProgramInfo, int, (void* a, unsigned int b, unsigned long c, void* d, unsigned long* e), (a, b, c, d, e), CL_ERR)
OCL_W(clGetKernelWorkGroupInfo, int, (void* a, void* b, unsigned int c, unsigned long d, void* e, unsigned long* f), (a, b, c, d, e, f), CL_ERR)
OCL_W(clCreateImage, void*, (void* a, unsigned long long b, void* c, void* d, void* e, int* f), (a, b, c, d, e, f), nullptr)
OCL_W(clCreateSubBuffer, void*, (void* a, unsigned long long b, unsigned int c, const void* d, int* e), (a, b, c, d, e), nullptr)
OCL_W(clEnqueueBarrierWithWaitList, int, (void* a, unsigned int b, void* c, void* d), (a, b, c, d), CL_ERR)
OCL_W(clEnqueueCopyBuffer, int, (void* a, void* b, void* c, unsigned long d, unsigned long e, unsigned long f, unsigned int g, void* h, void* i), (a, b, c, d, e, f, g, h, i), CL_ERR)
OCL_W(clEnqueueMarkerWithWaitList, int, (void* a, unsigned int b, void* c, void* d), (a, b, c, d), CL_ERR)
OCL_W(clFlush, int, (void* a), (a), CL_ERR)
OCL_W(clCreateBufferWithProperties, void*, (void* a, void* b, unsigned long long c, void* d, int* e), (a, b, c, d, e), nullptr)
OCL_W(clEnqueueFillBuffer, int, (void* a, void* b, const void* c, unsigned long d, unsigned long e, unsigned long f, unsigned int g, void* h, void* i), (a, b, c, d, e, f, g, h, i), CL_ERR)
OCL_W(clEnqueueMapBuffer, void*, (void* a, void* b, unsigned int c, unsigned long long d, unsigned long e, unsigned long f, unsigned int g, void* h, void* i, int* j), (a, b, c, d, e, f, g, h, i, j), nullptr)
OCL_W(clEnqueueUnmapMemObject, int, (void* a, void* b, void* c, unsigned int d, void* e, void* f), (a, b, c, d, e, f), CL_ERR)
OCL_W(clGetKernelInfo, int, (void* a, unsigned int b, unsigned long c, void* d, unsigned long* e), (a, b, c, d, e), CL_ERR)
OCL_W(clGetMemObjectInfo, int, (void* a, unsigned int b, unsigned long c, void* d, unsigned long* e), (a, b, c, d, e), CL_ERR)
OCL_W(clRetainMemObject, int, (void* a), (a), CL_ERR)
OCL_W(clRetainContext, int, (void* a), (a), CL_ERR)
OCL_W(clRetainCommandQueue, int, (void* a), (a), CL_ERR)
OCL_W(clRetainKernel, int, (void* a), (a), CL_ERR)
OCL_W(clRetainProgram, int, (void* a), (a), CL_ERR)
OCL_W(clCreateSampler, void*, (void* a, unsigned int b, unsigned int c, unsigned int d, int* e), (a, b, c, d, e), nullptr)
OCL_W(clReleaseSampler, int, (void* a), (a), CL_ERR)
OCL_W(clEnqueueReadImage, int, (void* a, void* b, unsigned int c, const unsigned long* d, const unsigned long* e, unsigned long f, unsigned long g, void* h, unsigned int i, void* j, void* k), (a, b, c, d, e, f, g, h, i, j, k), CL_ERR)
OCL_W(clEnqueueWriteImage, int, (void* a, void* b, unsigned int c, const unsigned long* d, const unsigned long* e, unsigned long f, unsigned long g, const void* h, unsigned int i, void* j, void* k), (a, b, c, d, e, f, g, h, i, j, k), CL_ERR)
OCL_W(clGetImageInfo, int, (void* a, unsigned int b, unsigned long c, void* d, unsigned long* e), (a, b, c, d, e), CL_ERR)
OCL_W(clCreateUserEvent, void*, (void* a, int* b), (a, b), nullptr)
OCL_W(clSetUserEventStatus, int, (void* a, int b), (a, b), CL_ERR)
