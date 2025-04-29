package engine;

import annotations.JavaScript;

public class VRRoutineJS {
    @JavaScript(code = "const viewIndex = arg0, dst = unpackFloatArray(arg1,16);\n" +
            "const src = webXR.views[viewIndex].transform.matrix;\n" +
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

    @JavaScript(code = "const viewIndex = arg0, dst = unpackIntArray(arg1,2);\n" +
            "const src = webXR.viewports[viewIndex];\n" +
            "dst[0] = src.width; dst[1] = src.height;\n")
    public static native void fillViewport(int viewIndex, int[] dst);

    @JavaScript(code = "return webXR.views.length;\n")
    public static native int getNumViews();

    @JavaScript(code = "" +
            "const src = unmap(arg0), view = webXR.viewports[arg1];\n" +
            "const x = view.x, y = view.y, w = view.width, h = view.height;\n" +
            "gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, webXR.targetFramebuffer);\n" +
            "gl.bindFramebuffer(gl.READ_FRAMEBUFFER, src);\n" +
            "gl.blitFramebuffer(0,0,w,h,x,y,x+w,y+h,gl.COLOR_BUFFER_BIT,gl.NEAREST);\n" // src,dst,data,filtering
    )
    public static native void drawToTargetFramebuffer(int srcFB, int viewIndex);
}
