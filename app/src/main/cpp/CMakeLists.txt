cmake_minimum_required(VERSION 3.22.1)
project("music_engine")

add_library(music_engine SHARED native-lib.cpp)

find_library(log-lib log)
target_link_libraries(music_engine ${log-lib})