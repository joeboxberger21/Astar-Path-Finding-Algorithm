import input.KeyboardInput;
import input.MouseButtons;
import org.lwjgl.BufferUtils;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class AstarPathfinding {

    // The window handle
    private long window;

    private final int WIDTH = 1600;
    private final int HEIGHT = 900;
    boolean keyPressed = false;

    private GLFWMouseButtonCallback buttonCallback;
    private GLFWKeyCallback keyCallback;

    private ArrayList<ArrayList> cellColumns = new ArrayList<ArrayList>();
    String[] cellTypes = {"solid", "start", "end", "search"};
    int typeSelected = 0;
    int[] startIndex = {-3, -3};
    int[] endIndex = {-3, -3};
    int[] searchIndex = {-3, -3};
    int lowestCost = 99999999;

    boolean debugPressed = false;

    boolean startPlaced = false;
    boolean endPlaced = false;

    public void run() {
        System.out.println("LWJGL: " + Version.getVersion());

        init();
        loop();

        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if ( !glfwInit() )
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable


        // Create the window
        window = glfwCreateWindow(WIDTH, HEIGHT, "A* Pathfinding", NULL, NULL);
        if ( window == NULL )
            throw new RuntimeException("Failed to create the GLFW window");

        // Setup a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if ( key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE )
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
        });

        // Get the thread stack and push a new frame
        try ( MemoryStack stack = stackPush() ) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        } // the stack frame is popped automatically

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);

        int cellWidth = 150;
        int cellHeight = 150;
        int cellSpacing = 5;
        int cellXSize = (cellWidth + cellSpacing);
        int cellYsize = (cellHeight + cellSpacing);
        int rows = (WIDTH / cellXSize) - 1;
        int columns = (HEIGHT / cellYsize) - 1;
        int xSpace = WIDTH - (rows*cellXSize) +cellSpacing;
        int ySpace = HEIGHT - (columns*cellYsize) +cellSpacing;

        for(int i = 0; i < columns; i++) {
            ArrayList<Cell> cellRow = new ArrayList<Cell>();
            cellColumns.add(cellRow);
            for (int f = 0; f < rows; f++) {
                int[] coords = {f, i};
                Cell cell = new Cell(f*cellXSize + (xSpace/2), i*cellYsize + (ySpace/2), cellWidth, cellHeight, coords);
                cellRow.add(cell);
            }
        }
    }

    private void loop() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, WIDTH, 0, HEIGHT, 1, -1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        // Set the clear color
        glClearColor(0.2f, 0.2f, 0.2f, 0.2f);

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);

        DoubleBuffer mouseX = BufferUtils.createDoubleBuffer(1);
        DoubleBuffer mouseY = BufferUtils.createDoubleBuffer(1);

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while ( !glfwWindowShouldClose(window) ) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

            glfwGetCursorPos(window, mouseX, mouseY);
            int newMouseX = (int) mouseX.get(0);
            int newMouseY = HEIGHT - (int) mouseY.get(0);

            glfwSetMouseButtonCallback(window, buttonCallback = new MouseButtons());
            glfwSetKeyCallback(window, keyCallback = new KeyboardInput());
            if(KeyboardInput.key == GLFW_KEY_LEFT && KeyboardInput.action == 1 && typeSelected >= 1 && !keyPressed) {
                typeSelected--;
                keyPressed = true;
            } else if (KeyboardInput.key == GLFW_KEY_RIGHT && KeyboardInput.action == 1 && typeSelected < cellTypes.length - 1 && !keyPressed) {
                typeSelected++;
                keyPressed = true;
            } if (KeyboardInput.action == 0){
                keyPressed = false;
            }


            for(ArrayList<Cell> column : cellColumns) {
                for(Cell cell : column) {
                    cell.inBounds(newMouseX, newMouseY);

                    if(cell.isSelected && MouseButtons.button == 3 && MouseButtons.action == 1 && debugPressed == false) {
                        System.out.println(cell.type + " at " + cell.index[0] + ", " + cell.index[1]);
                        System.out.println("distance from start: " + distance(startIndex[0], startIndex[1], cell.index[0], cell.index[1]) + " | distance from end: " + distance(endIndex[0], endIndex[1], cell.index[0], cell.index[1]));
                        System.out.println("Fcost: " + Fcost(startIndex[0], startIndex[1], endIndex[0], endIndex[1], cell.index[0], cell.index[1]) + "\n");
                        debugPressed = true;
                    } else if (MouseButtons.button == 3 && MouseButtons.action == 0) {
                        debugPressed = false;
                    }

                    if (MouseButtons.button == 1 && MouseButtons.action == 1 && cell.isSelected) {
                        if (cell.type == "start") {
                            startPlaced = false;
                            startIndex[0] = -3;
                            startIndex[1] = -3;
                            lowestCost = 99999;
                        } else if (cell.type == "end") {
                            endPlaced = false;
                            endIndex[0] = -3;
                            endIndex[1] = -3;
                            lowestCost = 99999;
                        }
                        cell.type = "space";
                    } else if (MouseButtons.button == 0 && MouseButtons.action == 1 && cell.isSelected) {
                        if(cellTypes[typeSelected] == "start" && !startPlaced) {
                            cell.type = cellTypes[typeSelected];
                            startPlaced = true;
                            startIndex[0] = cell.index[0];
                            startIndex[1] = cell.index[1];
                            searchIndex[0] = startIndex[0];
                            searchIndex[1] = startIndex[1];
                        } else if (cellTypes[typeSelected] == "end" && !endPlaced) {
                            cell.type = cellTypes[typeSelected];
                            endPlaced = true;
                            endIndex[0] = cell.index[0];
                            endIndex[1] = cell.index[1];
                        } else if (cellTypes[typeSelected] != "start" && cellTypes[typeSelected] != "end") {
                            cell.type = cellTypes[typeSelected];
                        }
                    }

                    if (cell.type == "search" || cell.type == "path") {
                        if(!startPlaced || !endPlaced) {
                            cell.type = "space";
                        }
                    }

                    if (cell.type == "space" && startPlaced && endPlaced) {
                        if (cell.index[0] == searchIndex[0] + 1 && cell.index[1] == searchIndex[1]) {
                            cell.type = "search";
                            System.out.println("Middle Right, Start: " + searchIndex[0] + " " + searchIndex[1] + " Cell: " + cell.index[0] + " " + cell.index[1]);
                        } else if (cell.index[1] == searchIndex[1] + 1 && cell.index[0] == searchIndex[0]) {
                            cell.type = "search";
                            System.out.println("Middle Top, Start: " + searchIndex[0] + " " + searchIndex[1] + " Cell: " + cell.index[0] + " " + cell.index[1]);
                        } else if (cell.index[1] == searchIndex[1] + 1 && cell.index[0] == searchIndex[0] + 1) {
                            cell.type = "search";
                            System.out.println("Top Right, Start: " + searchIndex[0] + " " + searchIndex[1] + " Cell: " + cell.index[0] + " " + cell.index[1]);
                        } else if (cell.index[0] == searchIndex[0] - 1 && cell.index[1] == searchIndex[1]) {
                            cell.type = "search";
                            System.out.println("Middle Left, Start: " + searchIndex[0] + " " + searchIndex[1] + " Cell: " + cell.index[0] + " " + cell.index[1]);
                        } else if (cell.index[1] == searchIndex[1] - 1 && cell.index[0] == searchIndex[0]) {
                            cell.type = "search";
                            System.out.println("Middle Down, Start: " + searchIndex[0] + " " + searchIndex[1] + " Cell: " + cell.index[0] + " " + cell.index[1]);
                        } else if (cell.index[0] == searchIndex[0] - 1 && cell.index[1] == searchIndex[1] - 1) {
                            cell.type = "search";
                            System.out.println("Bottom Left, Start: " + searchIndex[0] + " " + searchIndex[1] + " Cell: " + cell.index[0] + " " + cell.index[1]);
                        } else if (cell.index[0] == searchIndex[0] + 1 && cell.index[1] == searchIndex[1] - 1) {
                            cell.type = "search";
                            System.out.println("Bottom Right, Start: " + searchIndex[0] + " " + searchIndex[1] + " Cell: " + cell.index[0] + " " + cell.index[1]);
                        } else if (cell.index[0] == searchIndex[0] - 1 && cell.index[1] == searchIndex[1] + 1) {
                            cell.type = "search";
                            System.out.println("Top Left, Start: " + searchIndex[0] + " " + searchIndex[1] + " Cell: " + cell.index[0] + " " + cell.index[1]);
                        }
                    }

                    if (cell.type.equals("search")) {
                        int cost = Fcost(startIndex[0], startIndex[1], endIndex[0], endIndex[1], cell.index[0], cell.index[1]);
                        if (cost < lowestCost) {
                            lowestCost = cost;
                            System.out.println("New Cost");
                        }
//                        else {
//                            cell.type = "space";
//                        }
                    }

                    cell.renderSquare();
                }
            }

            glfwPollEvents();
            glfwSwapBuffers(window); // swap the color buffers
            // Poll for window events. The key callback above will only be
            // invoked during this call.
        }
    }

    public static void main(String[] args) {
        new AstarPathfinding().run();
    }

    public static int distance(int targetX, int targetY, int cellX, int cellY) {
        return (int) (Math.sqrt(Math.pow((cellX - targetX), 2) + Math.pow((cellY - targetY), 2))*10);
    }
    public static int Fcost(int startX, int startY, int endX, int endY, int cellX, int cellY) {
        return distance(startX, startY, cellX, cellY) + distance(endX, endY, cellX, cellY);
    }
}

class Cell {
    public float x,y,w,h;
    float red;
    float green;
    float blue;
    int[] index;
    boolean isSelected = false;
    String type = "space";

    Cell (float x, float y, float w, float h, int[] index){
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.index = index;
    }

    void renderSquare() {
        if (type.equals("solid")) {
            red = 1.0f;
            green = 1.0f;
            blue = 1.0f;
        } else if (type.equals("space")) {
            red = 0f;
            green = 0f;
            blue = 0f;
        } else if (type.equals("start")) {
            red = 0.5f;
            green = 1f;
            blue = 0.5f;
        } else if (type.equals("end")) {
            red = 1f;
            green = 0.5f;
            blue = 0.5f;
        } else if (type.equals("path")) {
            red = 0.3f;
            green = 0.3f;
            blue = 0.6f;
        } else if (type.equals("search")) {
            red = 0.6f;
            green = 0.4f;
            blue = 0.8f;
        }

        if (isSelected) {
            red += 0.1f;
            green += 0.1f;
            blue += 0.1f;
        } else {
            red -= 0.1f;
            green -= 0.1f;
            blue -= 0.1f;
        }
        glColor3f(red, green, blue);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }

    boolean inBounds(int mouseX, int mouseY) {
        isSelected = (mouseX > x && mouseX < (x + w)) && (mouseY > y && mouseY < (y + h));
        return isSelected;
    }
}