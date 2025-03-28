# JVM2WASM

This project is a compiler from JVM to WASM for [Rem's Engine](https://github.com/AntonioNoack/RemsEngine)'s web port.
It has a [garbage collector](src/jvm/GC.java), and [JavaScript bindings for OpenGL via OpenGL ES](src/jvm/LWJGLxOpenGL.java).
The target JVM language is Java 8 (lambdas).

This project now has a second target with [WASM2CPP](#WASM2Cpp): Native desktop, compiling the generated WASM into C++.

The generated code relies heavily on switch-statements, because structural analysis is hard, and I haven't implemented "pattern-independent structuring" (a paper, that solves it) yet.
This makes it much slower than native code (50x-100x in [SciMark2](https://math.nist.gov/scimark2/)).

Also, there are lots of redundant if-statements for error handling, and currently error handling is implemented using two return values.
This probably decreases performance a lot, too.
Error handling could be implemented using WASM-exceptions.

Currently, since I generate text-wasm instead of binary-wasm, I cannot map binary instructions to line numbers.
Therefore, with optional exception handling, a manual stack is created, where the stack-trace is tracked.
This probably decreases performance as well.

The resulting file is quite large at the moment, partially because a lot of unnecessary functions are encoded,
and because they take up quite a bit of space.

## Main class
The main class for translation is [JVM2WASM.kt](src/JVM2WASM.kt). It controls, where class indexing should start,
and which classes shall be replaced by others (to reduce total executable size).

## Quirks
- I replaced the java.lang.String contents with UTF-8, because that's more sensible imo and saves memory.
- There is only a single thread. It cannot sleep (because that's impossible in JavaScript).
- There is only asynchronous IO via JavaScript bindings, because JavaScript has no synchronous IO (or it shouldn't be used, because that would block the UI thread).
- My Java-8 implementation is lackluster in relation to lambdas: I only support Java-native lambdas.
- It is precompiled, so at runtime, classes are immutable.
- Reflection is partially supported: fields and methods exist, constructors only the one without arguments. Annotations aren't supported yet.

## Runtime
The runtime is defined in [index.js](src/index.js). It is defined like that for Rem's Engine.
It calls into [Engine.java](src/engine/Engine.java), first main() and then update() is called every frame. MouseMove(), keyDown() etc are called on their respective DOM-events.

tmp/index0.js is the file of JavaScript bindings, that is automatically generated from the source code.
Entries can be added using the @JavaScript(code="") annotation within Java source code. If a method is missing,
but depended on, e.g., because it is native to the JVM or blacklisted, it will throw an exception if called.
@Alias(name="") can be used to make the function name on the JavaScript side explicit and shorter, or to implement such a blacklisted method:
just use the same name, and it will be used as an implementation.
@Alias(name="") may also be used to override existing methods, e.g., to simplify its behaviour, extend it, replace its implementation (e.g., with a JS library), or similar.
@WASM(code="") can be used for inline assembly.

Notice that methods implemented in @JavaScript need an extra return value for the potential exception!
Use @NoThrow to mark your method as safe.

Example (*with the idea that the original implementation was overkill for our needs*):
```java
@NoThrow
@Alias(name = "java_lang_Character_isDigit_IZ")
public static boolean Character_isDigit(int code) {
    return unsignedLessThan(code - '0', 10);
}

@NoThrow
@WASM(code = "i32.lt_u")
public static native boolean unsignedLessThan(int a, int b);
```

## WASM2CPP

For running my engine in pure C++ environments, I also created a transpiler from WASM text to C++. The performance is ok-ish, running
at 150 fps on my Ryzen 7950x3D with lots of security features disabled (max-performance mode), and rendering testSceneWithUI() with a low-poly sphere.
The same program runs at 417 fps in Java, and ideally would achieve 600+ fps.

The benefit of C++ over WASM is that jumps are supported natively, so there isn't much overhead for them.

The standard-library for running in C++ is pretty bare-bones at the moment, not being able to load files, not having network access, and having no font mechanism except the engine fallback (7-segment-like).

This C++-"port" should allow the engine to be compiled to any OS. There also isn't much "++" about it, most could be easily converted to C.

## Garbage Collector
The following section discusses my implemented garbage collectors, where performance has been tested in my C++-transpiled native environment
on a Ryzen 7950x3D (one of the fastest CPUs available at this time).

### Serial GC

Serial GC is too slow: when it runs every frame, it reduces the FPS to 40 on my system. When it runs every X frames,
the large accumulated backlog of old instances creates a large lag spike (200ms/50MB, X = 2000, from 50MB down to 20MB).
It has the advantage of not accumulating new memory during collection.

### Parallel GC

C++ offers multi-threading, so I experimented with a parallely running GC: finding the largest gaps, which is the most expensive step,
can be executed in parallel. The implementation is currently a little instable, crashing after 18,000 iterations in a stability test (when triggering GC every frame).
Running GC every 2000 frames at 60 fps should be stable enough though (92+ hours runtime).

### Concurrent GC

Inspired by the parallel GC crashing during development, I also developed a concurrent garbage collector:
the load is spread out over multiple frames, reducing FPS slightly (5ms first frame, ~1ms/frame) for a few frames, for a second.
It only crashed after 236,000 iterations (why ever). This is the best choice for stability and performance.

### Performance Comparison using SciMark 2.0a

| Performance Test                 | Amazon JDK    | WASM in Chrome      | C++ (Debug)          | C++ (Release)          |
|----------------------------------|---------------|---------------------|----------------------|------------------------|
| **Composite Score**              | 4646.12       | 85.86 (54x slower)  | 169.53 (27x slower)  | 3265.81 (1.4x slower)  |
| FFT (1024)                       | 2895.01       | 66.82 (43x slower)  | 75.23 (38x slower)   | 3037.42 (1.05x faster) |
| SOR (100x100)                    | 2306.35       | 159.25 (14x slower) | 407.73 (5.6x slower) | 2611.93 (1.13x faster) |
| Monte Carlo                      | 1431.18       | 29.31 (48x slower)  | 63.91 (22x slower)   | 1126.99 (1.27x slower) |
| Sparse MatMult (N=1000, nz=5000) | 4086.42       | 86.39 (47x slower)  | 206.22 (20x slower)  | 4050.12 (tie)          |
| LU (100x100)                     | 12511.65      | 87.54 (142x slower) | 94.55 (132x slower)  | 5502.60 (2.27x slower) |

## System Information

- **Java Version**: 1.8.0_402 Corretto (Amazon.com Inc.)
- **OS**: Windows 10 (10.0.19045, amd64)
- **Memory**: 32GB DDR5 4800 MT/s
- **CPU**: Ryzen 9 7950x3D
- **Chrome**: 125.0.6422.60 (outdated by a few months)

Test run in a regular engine build, 2025/03/27 on *abf3660e18cc583710b77943e78db881b7d79dda*.
Running the performance tests in the JDK somehow gets it stuck in measureMonteCarlo with the normal run mode 🤔.
It finished in debug-mode though, which is why I used the JDK debug-mode for my results.

## Compilation times (JRE + Rem's Engine)
Translating JVM bytecode to C++ and WAT takes 10-12 seconds (without static-init at compile-time, that is 3s extra).
Translating WAT to WASM using WABT (WASM binary toolkit) in WSL takes roughly three seconds.
Compiling the debug build currently takes roughly five seconds, and release build takes thirty seconds.

## Used Libraries / Dependencies
- [ObjectWeb ASM 9.3](https://asm.ow2.io/)
- [Rem's Engine](https://github.com/AntonioNoack/RemsEngine)

The system was developed for a sun-based JVM.
I have implemented stubs for a few methods like `sun.reflect.Reflection.getCallerClass()` and `sun.misc.VM.isSystemDomainLoader()`.
Other JVMs might implement different backends for classes like java.nio.?Buffer, and might need stubs or implementations for them, too.