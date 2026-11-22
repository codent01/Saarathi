cmake_minimum_required(VERSION 3.22.1)
project("saarathi_llama" CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Never ship unoptimized inference kernels, whatever build type the IDE picks.
if(NOT CMAKE_BUILD_TYPE OR CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_BUILD_TYPE Release)
endif()
set(CMAKE_C_FLAGS_DEBUG "-O2 -g")
set(CMAKE_CXX_FLAGS_DEBUG "-O2 -g")

# llama.cpp is vendored as a shallow git checkout at a pinned tag (see tools/fetch_llama.ps1).
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)
if(NOT EXISTS ${LLAMA_DIR}/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp sources not found at ${LLAMA_DIR}. Run tools/fetch_llama.ps1 first.")
endif()

# Keep the build lean: CPU backend only, no examples/tests/server.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    # Phones with 6 GB+ RAM virtually all have ARMv8.2 cores with dot-product and fp16 support.
    # The Kotlin side refuses to load a model on CPUs that lack "asimddp" so this never SIGILLs silently.
    set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}   -march=armv8.2-a+dotprod+fp16")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+dotprod+fp16")
endif()

add_subdirectory(${LLAMA_DIR} llama_build EXCLUDE_FROM_ALL)

add_library(saarathi_llama SHARED llama_jni.cpp)

target_include_directories(saarathi_llama PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/ggml/include)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(saarathi_llama PRIVATE llama ggml ${log-lib} ${android-lib})
target_compile_options(saarathi_llama PRIVATE -O2 -fvisibility=hidden)
