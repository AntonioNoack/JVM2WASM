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
if(MSVC)
    add_compile_options(/bigobj)
endif()

# Create the executable
add_executable(JVM2CPP jvm2wasm-base.cpp)

# Link GLFW and glad with the project
target_link_libraries(JVM2CPP PRIVATE glfw glad stbi)

# Platform-specific requirements
if (WIN32)
    target_link_libraries(JVM2CPP PRIVATE opengl32)
elseif (UNIX)
    find_package(OpenGL REQUIRED)
    target_link_libraries(JVM2CPP PRIVATE OpenGL::GL)
endif()