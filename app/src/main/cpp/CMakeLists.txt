cmake_minimum_required(VERSION 3.22.1)
project("talksync")

set(CMAKE_CXX_STANDARD 17)

add_library(sentencepiece SHARED IMPORTED)
set_target_properties(sentencepiece PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libsentencepiece.so)

add_library(talksync SHARED
    SentencePieceProcessorInterface.cpp)

target_include_directories(talksync PRIVATE
    ${CMAKE_SOURCE_DIR}/sentencepiece/include)

target_link_libraries(talksync
    sentencepiece
    android
    log)
