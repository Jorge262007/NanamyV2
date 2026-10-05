/*******************************************************************************
 * Copyright (c) 2008-2026 The Khronos Group Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

#ifndef __CL_PLATFORM_H
#define __CL_PLATFORM_H

#include <CL/cl_version.h>

#ifdef __cplusplus
extern "C" {
#endif

#if !defined(CL_API_ENTRY)
    #define CL_API_ENTRY
#endif
#if !defined(CL_API_CALL)
    #if defined(_WIN32) && !defined(__aarch64__) && !defined(__arm64__)
        #define CL_API_CALL __stdcall
    #else
        #define CL_API_CALL
    #endif
#endif
#if !defined(CL_CALLBACK)
    #if defined(_WIN32) && !defined(__aarch64__) && !defined(__arm64__)
        #define CL_CALLBACK __stdcall
    #else
        #define CL_CALLBACK
    #endif
#endif

/*
 * Deprecation flags refer to the last version of the header in which the
 * feature was not deprecated.
 *
 * E.g. VERSION_1_1_DEPRECATED means the feature is present in 1.1 without
 * deprecation but is deprecated in versions later than 1.1.
 */

#ifndef CL_API_SUFFIX_USER
#define CL_API_SUFFIX_USER
#endif

#ifndef CL_API_PREFIX_USER
#define CL_API_PREFIX_USER
#endif

#define CL_API_SUFFIX_COMMON CL_API_SUFFIX_USER
#define CL_API_PREFIX_COMMON CL_API_PREFIX_USER

#define CL_API_SUFFIX__VERSION_1_0 CL_API_SUFFIX_COMMON
#define CL_API_SUFFIX__VERSION_1_1 CL_API_SUFFIX_COMMON
#define CL_API_SUFFIX__VERSION_1_2 CL_API_SUFFIX_COMMON
#define CL_API_SUFFIX__VERSION_2_0 CL_API_SUFFIX_COMMON
#define CL_API_SUFFIX__VERSION_2_1 CL_API_SUFFIX_COMMON
#define CL_API_SUFFIX__VERSION_2_2 CL_API_SUFFIX_COMMON
#define CL_API_SUFFIX__VERSION_3_0 CL_API_SUFFIX_COMMON
#define CL_API_SUFFIX__VERSION_3_1 CL_API_SUFFIX_COMMON
#define CL_API_SUFFIX__EXPERIMENTAL CL_API_SUFFIX_COMMON


#ifdef __GNUC__
  #define CL_API_SUFFIX_DEPRECATED __attribute__((deprecated))
  #define CL_API_PREFIX_DEPRECATED
#elif defined(_MSC_VER) && !defined(__clang__)
  #define CL_API_SUFFIX_DEPRECATED
  #define CL_API_PREFIX_DEPRECATED __declspec(deprecated)
#else
  #define CL_API_SUFFIX_DEPRECATED
  #define CL_API_PREFIX_DEPRECATED
#endif

#ifdef CL_USE_DEPRECATED_OPENCL_1_0_APIS
    #define CL_API_SUFFIX__VERSION_1_0_DEPRECATED CL_API_SUFFIX_COMMON
    #define CL_API_PREFIX__VERSION_1_0_DEPRECATED CL_API_PREFIX_COMMON
#else
    #define CL_API_SUFFIX__VERSION_1_0_DEPRECATED CL_API_SUFFIX_COMMON CL_API_SUFFIX_DEPRECATED
    #define CL_API_PREFIX__VERSION_1_0_DEPRECATED CL_API_PREFIX_COMMON CL_API_PREFIX_DEPRECATED
#endif

#ifdef CL_USE_DEPRECATED_OPENCL_1_1_APIS
    #define CL_API_SUFFIX__VERSION_1_1_DEPRECATED CL_API_SUFFIX_COMMON
    #define CL_API_PREFIX__VERSION_1_1_DEPRECATED CL_API_PREFIX_COMMON
#else
    #define CL_API_SUFFIX__VERSION_1_1_DEPRECATED CL_API_SUFFIX_COMMON CL_API_SUFFIX_DEPRECATED
    #define CL_API_PREFIX__VERSION_1_1_DEPRECATED CL_API_PREFIX_COMMON CL_API_PREFIX_DEPRECATED
#endif

#ifdef CL_USE_DEPRECATED_OPENCL_1_2_APIS
    #define CL_API_SUFFIX__VERSION_1_2_DEPRECATED CL_API_SUFFIX_COMMON
    #define CL_API_PREFIX__VERSION_1_2_DEPRECATED CL_API_PREFIX_COMMON
#else
    #define CL_API_SUFFIX__VERSION_1_2_DEPRECATED CL_API_SUFFIX_COMMON CL_API_SUFFIX_DEPRECATED
    #define CL_API_PREFIX__VERSION_1_2_DEPRECATED CL_API_PREFIX_COMMON CL_API_PREFIX_DEPRECATED
 #endif

#ifdef CL_USE_DEPRECATED_OPENCL_2_0_APIS
    #define CL_API_SUFFIX__VERSION_2_0_DEPRECATED CL_API_SUFFIX_COMMON
    #define CL_API_PREFIX__VERSION_2_0_DEPRECATED CL_API_PREFIX_COMMON
#else
    #define CL_API_SUFFIX__VERSION_2_0_DEPRECATED CL_API_SUFFIX_COMMON CL_API_SUFFIX_DEPRECATED
    #define CL_API_PREFIX__VERSION_2_0_DEPRECATED CL_API_PREFIX_COMMON CL_API_PREFIX_DEPRECATED
#endif

#ifdef CL_USE_DEPRECATED_OPENCL_2_1_APIS
    #define CL_API_SUFFIX__VERSION_2_1_DEPRECATED CL_API_SUFFIX_COMMON
    #define CL_API_PREFIX__VERSION_2_1_DEPRECATED CL_API_PREFIX_COMMON
#else
    #define CL_API_SUFFIX__VERSION_2_1_DEPRECATED CL_API_SUFFIX_COMMON CL_API_SUFFIX_DEPRECATED
    #define CL_API_PREFIX__VERSION_2_1_DEPRECATED CL_API_PREFIX_COMMON CL_API_PREFIX_DEPRECATED
#endif

#ifdef CL_USE_DEPRECATED_OPENCL_2_2_APIS
    #define CL_API_SUFFIX__VERSION_2_2_DEPRECATED CL_API_SUFFIX_COMMON
    #define CL_API_PREFIX__VERSION_2_2_DEPRECATED CL_API_PREFIX_COMMON
#else
    #define CL_API_SUFFIX__VERSION_2_2_DEPRECATED CL_API_SUFFIX_COMMON CL_API_SUFFIX_DEPRECATED
    #define CL_API_PREFIX__VERSION_2_2_DEPRECATED CL_API_PREFIX_COMMON CL_API_PREFIX_DEPRECATED
#endif

#if (defined (_WIN32) && defined(_MSC_VER))

#if defined(__clang__)
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wlanguage-extension-token"
#endif

/* intptr_t is used in cl.h and provided by stddef.h in Visual C++, but not in clang */
/* stdint.h was missing before Visual Studio 2010, include it for later versions and for clang */
#if defined(__clang__) || _MSC_VER >= 1600
    #include <stdint.h>
#endif

/* scalar types  */
typedef signed   __int8         cl_char;
typedef unsigned __int8         cl_uchar;
typedef signed   __int16        cl_short;
typedef unsigned __int16        cl_ushort;
typedef signed   __int32        cl_int;
typedef unsigned __int32        cl_uint;
typedef signed   __int64        cl_long;
typedef unsigned __int64        cl_ulong;

typedef unsigned __int16        cl_half;
typedef float                   cl_float;
typedef double                  cl_double;

#if defined(__clang__)
#pragma clang diagnostic pop
#endif

/* Macro names and corresponding values defined by OpenCL */
#define CL_CHAR_BIT         8
#define CL_SCHAR_MAX        127
#define CL_SCHAR_MIN        (-127-1)
#define CL_CHAR_MAX         CL_SCHAR_MAX
#define CL_CHAR_MIN         CL_SCHAR_MIN
#define CL_UCHAR_MAX        255
#define CL_SHRT_MAX         32767
#define CL_SHRT_MIN         (-32767-1)
#define CL_USHRT_MAX        65535
#define CL_INT_MAX          2147483647
#define CL_INT_MIN          (-2147483647-1)
#define CL_UINT_MAX         0xffffffffU
#define CL_LONG_MAX         ((cl_long) 0x7FFFFFFFFFFFFFFFLL)
#define CL_LONG_MIN         ((cl_long) -0x7FFFFFFFFFFFFFFFLL - 1LL)
#define CL_ULONG_MAX        ((cl_ulong) 0xFFFFFFFFFFFFFFFFULL)

#define CL_FLT_DIG          6
#define CL_FLT_MANT_DIG     24
#define CL_FLT_MAX_10_EXP   +38
#define CL_FLT_MAX_EXP      +128
#define CL_FLT_MIN_10_EXP   -37
#define CL_FLT_MIN_EXP      -125
#define CL_FLT_RADIX        2
#define CL_FLT_MAX          340282346638528859811704183484516925440.0f
#define CL_FLT_MIN          1.175494350822287507969e-38f
#define CL_FLT_EPSILON      1.1920928955078125e-7f

#define CL_HALF_DIG          3
#define CL_HALF_MANT_DIG     11
#define CL_HALF_MAX_10_EXP   +4
#define CL_HALF_MAX_EXP      +16
#define CL_HALF_MIN_10_EXP   -4
#define CL_HALF_MIN_EXP      -13
#define CL_HALF_RADIX        2
#define CL_HALF_MAX          65504.0f
#define CL_HALF_MIN          6.103515625e-05f
#define CL_HALF_EPSILON      9.765625e-04f

#define CL_DBL_DIG          15
#define CL_DBL_MANT_DIG     53
#define CL_DBL_MAX_10_EXP   +308
#define CL_DBL_MAX_EXP      +1024
#define CL_DBL_MIN_10_EXP   -307
#define CL_DBL_MIN_EXP      -1021
#define CL_DBL_RADIX        2
#define CL_DBL_MAX          1.7976931348623158e+308
#define CL_DBL_MIN          2.225073858507201383090e-308
#define CL_DBL_EPSILON      2.220446049250313080847e-16

#define CL_M_E              2.7182818284590452354
#define CL_M_LOG2E          1.4426950408889634074
#define CL_M_LOG10E         0.43429448190325182765
#define CL_M_LN2            0.69314718055994530942
#define CL_M_LN10           2.30258509299404568402
#define CL_M_PI             3.14159265358979323846
#define CL_M_PI_2           1.57079632679489661923
#define CL_M_PI_4           0.78539816339744830962
#define CL_M_1_PI           0.31830988618379067154
#define CL_M_2_PI           0.63661977236758134308
#define CL_M_2_SQRTPI       1.12837916709551257390
#define CL_M_SQRT2          1.41421356237309504880
#define CL_M_SQRT1_2        0.70710678118654752440

#define CL_M_E_F            2.718281828f
#define CL_M_LOG2E_F        1.442695041f
#define CL_M_LOG10E_F       0.434294482f
#define CL_M_LN2_F          0.693147181f
#define CL_M_LN10_F         2.302585093f
#define CL_M_PI_F           3.141592654f
#define CL_M_PI_2_F         1.570796327f
#define CL_M_PI_4_F         0.785398163f
#define CL_M_1_PI_F         0.318309886f
#define CL_M_2_PI_F         0.636619772f
#define CL_M_2_SQRTPI_F     1.128379167f
#define CL_M_SQRT2_F        1.414213562f
#define CL_M_SQRT1_2_F      0.707106781f

#define CL_NAN              (CL_INFINITY - CL_INFINITY)
#define CL_HUGE_VALF        ((cl_float) 1e50)
#define CL_HUGE_VAL         ((cl_double) 1e500)
#define CL_MAXFLOAT         CL_FLT_MAX
#define CL_INFINITY         CL_HUGE_VALF

#else

#include <stdint.h>

/* scalar types  */
typedef int8_t          cl_char;
typedef uint8_t         cl_uchar;
typedef int16_t         cl_short;
typedef uint16_t        cl_ushort;
typedef int32_t         cl_int;
typedef uint32_t        cl_uint;
typedef int64_t         cl_long;
typedef uint64_t        cl_ulong;

typedef uint16_t        cl_half;
typedef float           cl_float;
typedef double          cl_double;

/* Vector types */
typedef struct { cl_float s[2]; } cl_float2;
typedef struct { cl_float s[4]; } cl_float4;
typedef struct { cl_float s[8]; } cl_float8;
typedef struct { cl_float s[16]; } cl_float16;

typedef struct { cl_half s[2]; } cl_half2;
typedef struct { cl_half s[4]; } cl_half4;
typedef struct { cl_half s[8]; } cl_half8;
typedef struct { cl_half s[16]; } cl_half16;

typedef struct { cl_int s[2]; } cl_int2;
typedef struct { cl_int s[4]; } cl_int4;
typedef struct { cl_int s[8]; } cl_int8;
typedef struct { cl_int s[16]; } cl_int16;

/* Macro names and corresponding values defined by OpenCL */
#define CL_CHAR_BIT         8
#define CL_SCHAR_MAX        127
#define CL_SCHAR_MIN        (-127-1)
#define CL_CHAR_MAX         CL_SCHAR_MAX
#define CL_CHAR_MIN         CL_SCHAR_MIN
#define CL_UCHAR_MAX        255
#define CL_SHRT_MAX         32767
#define CL_SHRT_MIN         (-32767-1)
#define CL_USHRT_MAX        65535
#define CL_INT_MAX          2147483647
#define CL_INT_MIN          (-2147483647-1)
#define CL_UINT_MAX         0xffffffffU
#define CL_LONG_MAX         ((cl_long) 0x7FFFFFFFFFFFFFFFLL)
#define CL_LONG_MIN         ((cl_long) -0x7FFFFFFFFFFFFFFFLL - 1LL)
#define CL_ULONG_MAX        ((cl_ulong) 0xFFFFFFFFFFFFFFFFULL)

#define CL_FLT_DIG          6
#define CL_FLT_MANT_DIG     24
#define CL_FLT_MAX_10_EXP   +38
#define CL_FLT_MAX_EXP      +128
#define CL_FLT_MIN_10_EXP   -37
#define CL_FLT_MIN_EXP      -125
#define CL_FLT_RADIX        2
#define CL_FLT_MAX          340282346638528859811704183484516925440.0f
#define CL_FLT_MIN          1.175494350822287507969e-38f
#define CL_FLT_EPSILON      1.1920928955078125e-7f

#define CL_HALF_DIG          3
#define CL_HALF_MANT_DIG     11
#define CL_HALF_MAX_10_EXP   +4
#define CL_HALF_MAX_EXP      +16
#define CL_HALF_MIN_10_EXP   -4
#define CL_HALF_MIN_EXP      -13
#define CL_HALF_RADIX        2
#define CL_HALF_MAX          65504.0f
#define CL_HALF_MIN          6.103515625e-05f
#define CL_HALF_EPSILON      9.765625e-04f

#define CL_DBL_DIG          15
#define CL_DBL_MANT_DIG     53
#define CL_DBL_MAX_10_EXP   +308
#define CL_DBL_MAX_EXP      +1024
#define CL_DBL_MIN_10_EXP   -307
#define CL_DBL_MIN_EXP      -1021
#define CL_DBL_RADIX        2
#define CL_DBL_MAX          1.7976931348623158e+308
#define CL_DBL_MIN          2.225073858507201383090e-308
#define CL_DBL_EPSILON      2.220446049250313080847e-16

#define CL_M_E              2.7182818284590452354
#define CL_M_LOG2E          1.4426950408889634074
#define CL_M_LOG10E         0.43429448190325182765
#define CL_M_LN2            0.69314718055994530942
#define CL_M_LN10           2.30258509299404568402
#define CL_M_PI             3.14159265358979323846
#define CL_M_PI_2           1.57079632679489661923
#define CL_M_PI_4           0.78539816339744830962
#define CL_M_1_PI           0.31830988618379067154
#define CL_M_2_PI           0.63661977236758134308
#define CL_M_2_SQRTPI       1.12837916709551257390
#define CL_M_SQRT2          1.41421356237309504880
#define CL_M_SQRT1_2        0.70710678118654752440

#define CL_M_E_F            2.718281828f
#define CL_M_LOG2E_F        1.442695041f
#define CL_M_LOG10E_F       0.434294482f
#define CL_M_LN2_F          0.693147181f
#define CL_M_LN10_F         2.302585093f
#define CL_M_PI_F           3.141592654f
#define CL_M_PI_2_F         1.570796327f
#define CL_M_PI_4_F         0.785398163f
#define CL_M_1_PI_F         0.318309886f
#define CL_M_2_PI_F         0.636619772f
#define CL_M_2_SQRTPI_F     1.128379167f
#define CL_M_SQRT2_F        1.414213562f
#define CL_M_SQRT1_2_F      0.707106781f

#if defined( __GNUC__ )
   #define CL_HUGE_VALF     __builtin_huge_valf()
   #define CL_HUGE_VAL      __builtin_huge_val()
   #define CL_NAN           __builtin_nanf( "" )
#else
   #define CL_HUGE_VALF     ((cl_float) 1e50)
   #define CL_HUGE_VAL      ((cl_double) 1e500)
   float nanf( const char * );
   #define CL_NAN           nanf( "" )
#endif
#define CL_MAXFLOAT         CL_FLT_MAX
#define CL_INFINITY         CL_HUGE_VALF

#endif

#ifdef __cplusplus
}
#endif

#endif  /* __CL_PLATFORM_H  */
