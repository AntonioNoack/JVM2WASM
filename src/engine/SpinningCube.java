package engine;

import me.anno.ecs.components.mesh.Mesh;
import me.anno.engine.DefaultAssets;
import me.anno.gpu.shader.Shader;
import me.anno.gpu.shader.ShaderLib;
import org.joml.Matrix4f;

public class SpinningCube {

    private static final Matrix4f transform = new Matrix4f();

    /**
     * a rendering test
     */
    public static void renderSpinningCube(float time, float aspect) {

        float size = 0.1f;
        transform.identity()
                .scale(size, size * aspect, 0.1f)
                .rotateZ(time);

        Mesh mesh = DefaultAssets.INSTANCE.getFlatCube();
        Shader shader = ShaderLib.INSTANCE.getShader3DSimple().getValue();
        shader.use();
        shader.m4x4("transform", transform);
        shader.v4f("tiling", 0f);
        mesh.draw(null, shader, 0);
    }
}
