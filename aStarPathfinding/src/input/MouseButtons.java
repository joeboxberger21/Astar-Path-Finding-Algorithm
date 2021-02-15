package input;

import org.lwjgl.glfw.GLFWMouseButtonCallback;

public class MouseButtons extends GLFWMouseButtonCallback {
    public static int button, action, mods;
    @Override
    public void invoke(long window, int button, int action, int mods) {
        this.button = button;
        this.action = action;
        this.mods = mods;
    }
}
