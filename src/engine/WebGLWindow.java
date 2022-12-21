package engine;

import me.anno.gpu.OSWindow;
import org.jetbrains.annotations.NotNull;

public final class WebGLWindow extends OSWindow {

    public WebGLWindow(@NotNull String title) {
        super(title);
    }

    @Override
    public void addCallbacks() {
        System.out.println("Skipping adding callbacks");
    }
}
