cmake_minimum_required(VERSION 3.22.1)

project("llama-android")

set(CMAKE_CXX_STANDARD 17)

# FORCE maximum performance optimizations even in Debug mode
# Note: -fno-finite-math-only is REQUIRED when using -ffast-math or -Ofast with ggml
add_compile_options(-O3 -ffast-math -fno-finite-math-only -flto -fno-stack-protector -fomit-frame-pointer)
add_compile_options(-g0) # Disable debug symbols for speed

# Enable shared libs for backend DL support (REQUIRED for GGML_CPU_ALL_VARIANTS)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)

# llama.cpp options
set(LLAMA_BUILD_COMMON ON)
set(LLAMA_BUILD_SERVER OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_TESTS OFF)

# Enable OpenCL with custom dynamic loader
set(GGML_OPENCL ON)
set(GGML_VULKAN OFF)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)

# Point to downloaded OpenCL headers
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/opencl)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH "ARM" CACHE STRING "" FORCE)
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
    set(GGML_OPENMP ON CACHE BOOL "" FORCE)
    set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
endif()

# The "stub" is a dynamic loader for OpenCL
add_library(OpenCL_Loader STATIC stub.cpp)
target_link_libraries(OpenCL_Loader log)

# FORCE CMake to use our stub. This prevents finding system libraries that trigger namespace errors.
set(OpenCL_FOUND TRUE CACHE BOOL "Force OpenCL found" FORCE)
set(OpenCL_LIBRARY OpenCL_Loader CACHE STRING "OpenCL lib" FORCE)
set(OpenCL_LIBRARIES OpenCL_Loader CACHE STRING "OpenCL libs" FORCE)
set(OpenCL_INCLUDE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/opencl CACHE STRING "OpenCL include" FORCE)
set(OpenCL_INCLUDE_DIRS ${OpenCL_INCLUDE_DIR} CACHE STRING "OpenCL includes" FORCE)

# Modern CMake target for OpenCL
if(NOT TARGET OpenCL::OpenCL)
    add_library(OpenCL::OpenCL INTERFACE IMPORTED)
    target_link_libraries(OpenCL::OpenCL INTERFACE OpenCL_Loader)
    target_include_directories(OpenCL::OpenCL INTERFACE ${OpenCL_INCLUDE_DIR})
endif()

add_subdirectory(llama.cpp)

# Output library name should match what LocalLlmEngine expects
add_library(nanamy-llama SHARED
        llama-android.cpp)

target_include_directories(nanamy-llama PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/common
        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/src)

target_link_libraries(nanamy-llama
        llama
        ggml
        llama-common
        OpenCL_Loader
        android
        log)
