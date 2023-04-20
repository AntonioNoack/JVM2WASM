# JVM2WASM

This project is a compiler from JVM to WASM for [Rem's Engine](https://github.com/AntonioNoack/RemsEngine)'s web port.
It has a [garbage collector](src/jvm/GC.java), and [JavaScript bindings for OpenGL via OpenGL ES](src/jvm/LWJGLxOpenGL.java).
The target JVM language is Java 8 (lambdas).

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

index0.js is the file of JavaScript bindings, that is automatically generated from the source code.
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

