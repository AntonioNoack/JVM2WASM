package jvm

import exportHelpers
import gIndex
import utils.*

fun appendNativeHelperFunctions(printer: StringBuilder2) {

    val t = BooleanArray(64) { true }

    fun forAll(str: String, data: BooleanArray = t, base: Int = 0) {
        val idx = base * 4
        if (data[idx + 0]) printer.append(str.replace("v1", i32))
        if (data[idx + 1]) printer.append(str.replace("v1", i64))
        if (data[idx + 2]) printer.append(str.replace("v1", f32))
        if (data[idx + 3]) printer.append(str.replace("v1", f64))
    }

    fun forAll2(str: String, data: BooleanArray = t, base: Int = 0) {
        val idx = base * 4
        forAll(str.replace("v2", i32), data, idx)
        forAll(str.replace("v2", i64), data, idx + 1)
        forAll(str.replace("v2", f32), data, idx + 2)
        forAll(str.replace("v2", f64), data, idx + 3)
    }

    fun forAll3(str: String, data: BooleanArray = t, base: Int = 0) {
        val idx = base * 4
        forAll2(str.replace("v3", i32), data, idx)
        forAll2(str.replace("v3", i64), data, idx + 1)
        forAll2(str.replace("v3", f32), data, idx + 2)
        forAll2(str.replace("v3", f64), data, idx + 3)
    }

    if (exportHelpers) {
        forAll("(func \$dupv1 (export \"dupv1\") (param v1) (result v1 v1) local.get 0 local.get 0)\n")
        forAll2("(func \$dup2v1v2 (export \"dup2v1v2\") (param v1 v2) (result v1 v2 v1 v2) local.get 0 local.get 1 local.get 0 local.get 1)\n")
        forAll2("(func \$swapv1v2 (export \"swapv1v2\") (param v1 v2) (result v2 v1) local.get 1 local.get 0)\n")
    } else {
        forAll("(func \$dupv1 (param v1) (result v1 v1) local.get 0 local.get 0)\n")
        forAll2("(func \$dup2v1v2 (param v1 v2) (result v1 v2 v1 v2) local.get 0 local.get 1 local.get 0 local.get 1)\n")
        forAll2("(func \$swapv1v2 (param v1 v2) (result v2 v1) local.get 1 local.get 0)\n")
    }

    // instance, value, offset
    printer.append("(func \$setFieldI8 (param i32 i32 i32) (result) local.get 0 local.get 2 i32.add local.get 1 i32.store8)\n")
    printer.append("(func \$setFieldI16 (param i32 i32 i32) (result) local.get 0 local.get 2 i32.add local.get 1 i32.store16)\n")
    printer.append("(func \$setFieldI32 (param i32 i32 i32) (result) local.get 0 local.get 2 i32.add local.get 1 i32.store)\n")
    printer.append("(func \$setFieldI64 (param i32 i64 i32) (result) local.get 0 local.get 2 i32.add local.get 1 i64.store)\n")
    printer.append("(func \$setFieldF32 (param i32 f32 i32) (result) local.get 0 local.get 2 i32.add local.get 1 f32.store)\n")
    printer.append("(func \$setFieldF64 (param i32 f64 i32) (result) local.get 0 local.get 2 i32.add local.get 1 f64.store)\n")

    forAll2(
        "(func \$dup_x1v1v2 (export \"dup_x1v1v2\") (param v2 v1) (result v1 v2 v1) local.get 1 local.get 0 local.get 1)\n",
        gIndex.usedDup_x1
    )
    forAll3(
        "(func \$dup_x2v1v2v3 (export \"dup_x2v1v2v3\") (param v3 v2 v1) (result v1 v3 v2 v1) local.get 2 local.get 0 local.get 1 local.get 2)\n",
        gIndex.usedDup_x2
    )

    // a % b = a - a/b*b
    if (exportHelpers) {
        printer.append(
            "(func \$f32rem (export \"f32rem\") (param f32 f32) (result f32)\n" +
                    "  local.get 0 local.get 0 local.get 1 f32.div\n" +
                    "  local.get 1 f32.trunc f32.mul f32.sub)\n"
        )
        printer.append(
            "(func \$f64rem (export \"f64rem\") (param f64 f64) (result f64)\n" +
                    "  local.get 0 local.get 0 local.get 1 f64.div\n" +
                    "  local.get 1 f64.trunc f64.mul f64.sub)\n"
        )
    } else {
        printer.append(
            "(func \$f32rem (param f32 f32) (result f32)\n" +
                    "  local.get 0 local.get 0 local.get 1 f32.div\n" +
                    "  local.get 1 f32.trunc f32.mul f32.sub)\n"
        )
        printer.append(
            "(func \$f64rem (param f64 f64) (result f64)\n" +
                    "  local.get 0 local.get 0 local.get 1 f64.div\n" +
                    "  local.get 1 f64.trunc f64.mul f64.sub)\n"
        )
    }

    printer.append("(func \$i32neg (param i32) (result i32) i32.const 0 local.get 0 i32.sub)\n")
    printer.append("(func \$i64neg (param i64) (result i64) i64.const 0 local.get 0 i64.sub)\n")
    printer.append("(func \$lcmp (param i64 i64) (result i32) local.get 0 local.get 1 i64.gt_s local.get 0 local.get 1 i64.lt_s i32.sub)\n")

    // to do implement this, when we have multi-threading
    printer.append("(func \$monitorEnter (param $ptrType))\n")
    printer.append("(func \$monitorExit (param $ptrType))\n")
    printer.append("(func \$wasStaticInited (param i32) (result i32)\n" +
            "  (local \$addr i32)\n" +
            "  global.get \$Z local.get 0 i32.add local.set \$addr\n" + // calculate flag address
            "  local.get \$addr i32.load8_u\n" + // load result
            "  local.get \$addr i32.const 1 i32.store8\n" + // set flag
            // return result
            ")\n")
}