package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static jvm.JVM32.getAddr;
import static jvm.NativeLog.log;

public class Debug {

    // can be removed in the future, just good for debugging
    @Alias(names = "debug")
    @SuppressWarnings("rawtypes")
    public static void debug(Object instance, boolean staticToo) throws IllegalAccessException {
        if (instance == null) {
            log("null");
        } else {
            Class clazz = instance.getClass();
            log("Class", clazz.getName());
            Field[] fields = clazz.getFields();
            //noinspection ConstantValue
            if (fields != null) for (Field f : fields) {
                if (f == null) continue;
                if (staticToo || !Modifier.isStatic(f.getModifiers())) {
                    debugField(instance, staticToo, f);
                }
            }
            if (instance instanceof Object[]) {
                debugArray(instance);
            }
        }
    }

    public static void debugField(Object instance, boolean staticToo, Field f) throws IllegalAccessException {
        String name = f.getName();
        String type = f.getType().getName();
        switch (type) {
            case "byte":
                log(type, name, f.getByte(instance));
                break;
            case "short":
                log(type, name, f.getShort(instance));
                break;
            case "char":
                log(type, name, f.getChar(instance));
                break;
            case "int":
                log(type, name, f.getInt(instance));
                break;
            case "long":
                log(type, name, f.getLong(instance));
                break;
            case "float":
                log(type, name, f.getFloat(instance));
                break;
            case "double":
                log(type, name, f.getDouble(instance));
                break;
            case "boolean":
                log(type, name, f.getBoolean(instance));
                break;
            default:
                Object value = f.get(instance);
                if (value == null) log(type, name, 0);
                else if (value instanceof String) log(type, name, getAddr(value), value.toString());
                else log(type, name, getAddr(value), value.getClass().getName());
                break;
        }
    }

    @NoThrow
    @JavaScript(code = "let lib=window.lib,len=Math.min(lib.r32(arg0+objectOverhead),100),arr=[];\n" +
            "for(let i=0;i<len;i++) arr.push(lib.r32(arg0+arrayOverhead+(i<<2)));\n" +
            "console.log(arr)")
    private static native void debugArray(Object instance);
}
