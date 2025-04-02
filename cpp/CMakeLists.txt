cmake_minimum_required(VERSION 3.30)
project(JVM2CPP VERSION 1.0 LANGUAGES CXX)

# Set C++ standard to C++20
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED True)

# Include necessary modules for downloading external dependencies
include(FetchContent)

# Fetch GLFW (download if not available)
FetchContent_Declare(
    glfw
    GIT_REPOSITORY https://github.com/glfw/glfw.git
    GIT_TAG        3.4  # Or the latest stable version
)
FetchContent_MakeAvailable(glfw)

# Add glad library
#  glad/include/glad/gl.h
add_library(glad STATIC ${PROJECT_SOURCE_DIR}/glad/src/gl.c)
target_include_directories(glad PUBLIC ${PROJECT_SOURCE_DIR}/glad/include)

# Add stb image library
#  stbi/include/stbi.h
add_library(stbi STATIC ${PROJECT_SOURCE_DIR}/stbi/stbi.c)
target_include_directories(stbi PUBLIC ${PROJECT_SOURCE_DIR}/stbi/include)

# Required, why ever
if (MSVC)
    add_compile_options(/bigobj)
endif()

# Instruct MSCV to print timing stats
if (MSVC)
	add_compile_options(/MP /Bt)
	add_link_options(/time)
endif()

# Create the executable
add_executable(JVM2CPP jvm2wasm-base.cpp jvm2wasm-funcTable.cpp
    jvm2wasm-part0.cpp jvm2wasm-part1.cpp jvm2wasm-part2.cpp jvm2wasm-part3.cpp jvm2wasm-part4.cpp
    jvm2wasm-part5.cpp jvm2wasm-part6.cpp jvm2wasm-part7.cpp jvm2wasm-part8.cpp jvm2wasm-part9.cpp
    jvm2wasm-part10.cpp jvm2wasm-part11.cpp jvm2wasm-part12.cpp jvm2wasm-part13.cpp jvm2wasm-part14.cpp
    jvm2wasm-part15.cpp jvm2wasm-part16.cpp jvm2wasm-part17.cpp jvm2wasm-part18.cpp jvm2wasm-part19.cpp
)

# Be pendantic about warnings to force truly clean, nice-looking code
if (CMAKE_CXX_COMPILER_ID MATCHES "Clang" OR CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
    target_compile_options(JVM2CPP PRIVATE -Wall -Wextra -Wpedantic)
elseif (CMAKE_CXX_COMPILER_ID STREQUAL "MSVC")
    target_compile_options(JVM2CPP PRIVATE /W4)
endif()

# Link GLFW and glad with the project
target_link_libraries(JVM2CPP PRIVATE glfw glad stbi)

# Platform-specific requirements
if (WIN32)
    target_link_libraries(JVM2CPP PRIVATE opengl32)
elseif (UNIX)
    find_package(OpenGL REQUIRED)
    target_link_libraries(JVM2CPP PRIVATE OpenGL::GL)
endif()