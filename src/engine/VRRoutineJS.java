package engine;

import annotations.JavaScript;

public class VRRoutineJS {
    @JavaScript(code = "const viewIndex = arg0, dst = unpackFloatArray(arg1,16);\n" +
            "const src = webXR.views[viewIndex].transform.inverse.matrix;\n" +
            "for(let i=0;i<16;i++){\n" +
            "   dst[i] = src[i];\n" +
            "}\n")
    public static native void fillModelMatrix(int viewIndex, float[] dst);

    @JavaScript(code = "const viewIndex = arg0, dst = unpackFloatArray(arg1,16);\n" +
            "const src = webXR.views[viewIndex].projectionMatrix;\n" +
            "for(let i=0;i<16;i++){\n" +
            "   dst[i] = src[i];\n" +
            "}\n")
    public static native void fillProjectionMatrix(int viewIndex, float[] dst);

    @JavaScript(code = "const viewIndex = arg0, dst = unpackIntArray(arg1,4);\n" +
            "const src = webXR.viewports[viewIndex];\n" +
            "dst[0] = src.x; dst[1] = src.y; dst[2] = src.width; dst[3] = src.height;\n")
    public static native void fillViewport(int viewIndex, int[] dst);

    @JavaScript(code = "return webXR.views.length;\n")
    public static native int getNumViews();

    @JavaScript(code = "" +
            "const src = unmap(arg0), w = arg1, h = arg2;\n" +
            "gl.bindFramebuffer(gl.WRITE_FRAMEBUFFER, webXR.targetFramebuffer);\n" +
            "gl.bindFramebuffer(gl.READ_FRAMEBUFFER, src);\n" +
            "gl.blitFramebuffer(0,0,w,h,0,0,w,h,gl.COLOR_BUFFER_BIT,gl.NEAREST);\n" +
            "gl.bindFramebuffer(gl.FRAMEBUFFER, src);\n" // restore state
    )
    public static native void drawToTargetFramebuffer(int targetFB, int width, int height);
}
