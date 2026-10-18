cmake_minimum_required(VERSION 3.22.1)

project("nvplayer_native" C CXX)

# YTDLP execution wrapper for SDK 29+ bypass
add_executable(ytdl_wrapper ytdl_wrapper.c)
set_target_properties(ytdl_wrapper PROPERTIES OUTPUT_NAME "ytdl")
set_target_properties(ytdl_wrapper PROPERTIES PREFIX "lib" SUFFIX ".so")

if (ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    target_link_options(ytdl_wrapper PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
endif()
