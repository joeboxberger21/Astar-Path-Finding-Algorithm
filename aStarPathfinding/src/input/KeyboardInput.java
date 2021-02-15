package input;

import org.lwjgl.glfw.GLFWKeyCallback;

public class KeyboardInput extends GLFWKeyCallback {
    public static int key, scanmode, action, mods;
    @Override
    public void invoke(long window, int key, int scancode, int action, int mods) {
        this.key = key;
        this.scanmode = scancode;
        this.action = action;
        this.mods = mods;
    }
}
