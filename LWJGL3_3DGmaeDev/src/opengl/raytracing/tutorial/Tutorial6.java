/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package opengl.raytracing.tutorial;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.system.MemoryUtil.*;
import static util.Std430Writer.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.*;
import java.util.*;
import java.util.zip.ZipInputStream;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import opengl.util.*;
import util.DynamicByteBuffer;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

/**
 * In this tutorial we will trace triangle meshes imported from a scene
 * description file via {@link Assimp}. We will also use a binary Bounding
 * Volume Hierarchy structure with axis-aligned bounding boxes and show how this
 * can be traversed in a stack-less way on the GPU.
 * <p>
 * The strategy to traverse the BVH tree in the compute shader is explained in
 * the corresponding raytracing.glsl shader file.
 * 
 * @author Kai Burjack
 */
public class Tutorial6 {

    /**
     * A triangle described by three vertices with its positions and normals.
     */
    private static class Triangle {
        private static final float ONE_THIRD = 1.0f / 3.0f;
        private float v0x, v0y, v0z;
        private float v1x, v1y, v1z;
        private float v2x, v2y, v2z;
        private float n0x, n0y, n0z;
        private float n1x, n1y, n1z;
        private float n2x, n2y, n2z;

        private float centroid(int axis) {
            switch (axis) {
            case 0:
                return (v0x + v1x + v2x) * ONE_THIRD;
            case 1:
                return (v0y + v1y + v2y) * ONE_THIRD;
            case 2:
                return (v0z + v1z + v2z) * ONE_THIRD;
            default:
                throw new IllegalArgumentException();
            }
        }

        private float max(int axis) {
            switch (axis) {
            case 0:
                return Math.max(Math.max(v0x, v1x), v2x);
            case 1:
                return Math.max(Math.max(v0y, v1y), v2y);
            case 2:
                return Math.max(Math.max(v0z, v1z), v2z);
            default:
                throw new IllegalArgumentException();
            }
        }

        private float min(int axis) {
            switch (axis) {
            case 0:
                return Math.min(Math.min(v0x, v1x), v2x);
            case 1:
                return Math.min(Math.min(v0y, v1y), v2y);
            case 2:
                return Math.min(Math.min(v0z, v1z), v2z);
            default:
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * This demo uses a binary Bounding Volume Hierarchy using axis-aligned bounding
     * boxes to accelerate ray-triangle intersections.
     */
    private static class BVH {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        BVH parent, left, right;
        BVH hitNext, missNext;
        boolean isLeft;
        List<Triangle> triangles;
    }

    /**
     * Reflects the "Node" structure of a BVH node as used in the raytracing.glsl
     * shader.
     * <p>
     * We use the {@link Std430Writer} to layout our BVH nodes in memory as the
     * shader expects it.
     */
    static class GPUNode {
        Vector3f min; // int padW; // <- std430 padding!
        Vector3f max;
        int hitNext; // <- this will layout as max.w
        int missNext, firstTri, numTris;
        // int pad0; // <- std430 padding!
    }

    /**
     * Describes the imported Assimp scene and builds OpenGL buffer objects for
     * vertex, normal and elements/indices.
     */
    private static class Model {
        private List<Mesh> meshes;

        private Model(AIScene scene) {
            int meshCount = scene.mNumMeshes();
            PointerBuffer meshesBuffer = scene.mMeshes();
            meshes = new ArrayList<>();
            for (int i = 0; i < meshCount; ++i)
                meshes.add(new Mesh(AIMesh.create(meshesBuffer.get(i))));
        }

        private static class Mesh {
            private int vao;
            private int vertexArrayBuffer;
            private FloatBuffer verticesFB;
            private int normalArrayBuffer;
            private FloatBuffer normalsFB;
            private int elementArrayBuffer;
            private IntBuffer indicesIB;
            private int elementCount;

            /**
             * Build everything from the given {@link AIMesh}.
             *
             * @param mesh the Assimp {@link AIMesh} object
             */
            private Mesh(AIMesh mesh) {
                vao = glGenVertexArrays();
                glBindVertexArray(vao);
                vertexArrayBuffer = glGenBuffers();
                glBindBuffer(GL_ARRAY_BUFFER, vertexArrayBuffer);
                AIVector3D.Buffer vertices = mesh.mVertices();
                int verticesBytes = 4 * 3 * vertices.remaining();
                verticesFB = BufferUtils.createByteBuffer(verticesBytes).asFloatBuffer();
                memCopy(vertices.address(), memAddress(verticesFB), verticesBytes);
                nglBufferData(GL_ARRAY_BUFFER, verticesBytes, vertices.address(), GL_STATIC_DRAW);
                glEnableVertexAttribArray(0);
                glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0L);
                normalArrayBuffer = glGenBuffers();
                glBindBuffer(GL_ARRAY_BUFFER, normalArrayBuffer);
                AIVector3D.Buffer normals = mesh.mNormals();
                int normalsBytes = 4 * 3 * normals.remaining();
                normalsFB = BufferUtils.createByteBuffer(normalsBytes).asFloatBuffer();
                memCopy(normals.address(), memAddress(normalsFB), normalsBytes);
                nglBufferData(GL_ARRAY_BUFFER, normalsBytes, normals.address(), GL_STATIC_DRAW);
                glEnableVertexAttribArray(1);
                glVertexAttribPointer(1, 3, GL_FLOAT, true, 0, 0L);
                int faceCount = mesh.mNumFaces();
                elementCount = faceCount * 3;
                indicesIB = BufferUtils.createIntBuffer(elementCount);
                AIFace.Buffer facesBuffer = mesh.mFaces();
                for (int i = 0; i < faceCount; ++i) {
                    AIFace face = facesBuffer.get(i);
                    if (face.mNumIndices() != 3)
                        throw new IllegalStateException("AIFace.mNumIndices() != 3");
                    indicesIB.put(face.mIndices());
                }
                indicesIB.flip();
                elementArrayBuffer = glGenBuffers();
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer);
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesIB, GL_STATIC_DRAW);
                glBindVertexArray(0);
            }
        }
    }

    /**
     * The GLFW window handle.
     */
    private long window;
    private int width = 1200;
    private int height = 800;
    /**
     * Whether we need to recreate our ray tracer framebuffer.
     */
    private boolean resetFramebuffer = true;

    /**
     * The OpenGL texture acting as our framebuffer for the ray tracer.
     */
    private int pttex;
    /**
     * A VAO simply holding a VBO for rendering a simple quad.
     */
    private int vao;
    /**
     * The shader program handle of the compute shader.
     */
    private int computeProgram;
    /**
     * The shader program handle of a fullscreen quad shader.
     */
    private int quadProgram;
    /**
     * A sampler object to sample the framebuffer texture when finally presenting it
     * on the screen.
     */
    private int sampler;

    /**
     * The location of the 'eye' uniform declared in the compute shader holding the
     * world-space eye position.
     */
    private int eyeUniform;
    /*
     * The location of the rayNN uniforms. These will be explained in trace().
     */
    private int ray00Uniform, ray10Uniform, ray01Uniform, ray11Uniform;
    /**
     * The binding point in the compute shader of the framebuffer image (level 0 of
     * the {@link #pttex} texture).
     */
    private int framebufferImageBinding;
    private int nodesSsbo;
    private int nodesSsboBinding;
    private int trianglesSsbo;
    private int trianglesSsboBinding;
    /**
     * Value of the work group size in X dimension declared in the compute shader.
     */
    private int workGroupSizeX;
    /**
     * Value of the work group size in Y dimension declared in the compute shader.
     */
    private int workGroupSizeY;

    private float mouseX, mouseY;
    private boolean mouseDown;

    private Model model;
    private boolean[] keydown = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private Matrix4f projMatrix = new Matrix4f();
    private Matrix4f viewMatrix = new Matrix4f();
    private Matrix4f invViewProjMatrix = new Matrix4f();
    private Vector3f tmpVector = new Vector3f();
    private Vector3f cameraPosition = new Vector3f(0.0f, 2.2f, 3.0f);
    private Vector3f cameraLookAt = new Vector3f(0.0f, 0.5f, 0.0f);
    private Vector3f cameraUp = new Vector3f(0.0f, 1.0f, 0.0f);

    /*
     * All the GLFW callbacks we use to detect certain events, such as keyboard and
     * mouse events or window resize events.
     */
    private GLFWErrorCallback errCallback;
    private GLFWKeyCallback keyCallback;
    private GLFWFramebufferSizeCallback fbCallback;
    private GLFWCursorPosCallback cpCallback;
    private GLFWMouseButtonCallback mbCallback;

    /*
     * LWJGL's OpenGL debug callback object, which will get notified by OpenGL about
     * certain events, such as OpenGL errors, warnings or merely information.
     */
    private Callback debugProc;

    /**
     * Do everything necessary once at the start of the application.
     */
    private void init() throws IOException {
        /*
         * Set a GLFW error callback to be notified about any error messages GLFW
         * generates.
         */
        glfwSetErrorCallback(errCallback = GLFWErrorCallback.createPrint(System.err));
        /*
         * Initialize GLFW itself.
         */
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        /*
         * And set some OpenGL context attributes, such as that we are using OpenGL 4.3.
         * This is the minimum core version such so that can use compute shaders.
         */
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // <- make the window visible explicitly later
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        /*
         * Now, create the window.
         */
        window = glfwCreateWindow(width, height, "Path Tracing Tutorial 6", NULL, NULL);
        if (window == NULL)
            throw new AssertionError("Failed to create the GLFW window");

        System.out.println("Press WSAD, LCTRL, SPACE to move around in the scene.");
        System.out.println("Press Q/E to roll left/right.");
        System.out.println("Hold down left shift to move faster.");
        System.out.println("Move the mouse to look around.");

        /* And set some GLFW callbacks to get notified about events. */
        glfwSetKeyCallback(window, keyCallback = new GLFWKeyCallback() {
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (key == GLFW_KEY_ESCAPE && action != GLFW_RELEASE)
                    glfwSetWindowShouldClose(window, true);
                if (key == -1)
                    return;
                keydown[key] = action == GLFW_PRESS || action == GLFW_REPEAT;
            }
        });

        /*
         * We need to get notified when the GLFW window framebuffer size changed (i.e.
         * by resizing the window), in order to recreate our own ray tracer framebuffer
         * texture.
         */
        glfwSetFramebufferSizeCallback(window, fbCallback = new GLFWFramebufferSizeCallback() {
            public void invoke(long window, int width, int height) {
                if (width > 0 && height > 0 && (Tutorial6.this.width != width || Tutorial6.this.height != height)) {
                    Tutorial6.this.width = width;
                    Tutorial6.this.height = height;
                    Tutorial6.this.resetFramebuffer = true;
                }
            }
        });

        glfwSetCursorPosCallback(window, cpCallback = new GLFWCursorPosCallback() {
            public void invoke(long window, double x, double y) {
                if (mouseDown) {
                    float deltaX = (float) x - Tutorial6.this.mouseX;
                    float deltaY = (float) y - Tutorial6.this.mouseY;
                    Tutorial6.this.viewMatrix.rotateLocalY(deltaX * 0.01f);
                    Tutorial6.this.viewMatrix.rotateLocalX(deltaY * 0.01f);
                }
                Tutorial6.this.mouseX = (float) x;
                Tutorial6.this.mouseY = (float) y;
            }
        });

        glfwSetMouseButtonCallback(window, mbCallback = new GLFWMouseButtonCallback() {
            public void invoke(long window, int button, int action, int mods) {
                if (action == GLFW_PRESS) {
                    Tutorial6.this.mouseDown = true;
                } else if (action == GLFW_RELEASE) {
                    Tutorial6.this.mouseDown = false;
                }
            }
        });

        /*
         * Center the created GLFW window on the screen.
         */
        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        glfwSetWindowPos(window, (vidmode.width() - width) / 2, (vidmode.height() - height) / 2);
        glfwMakeContextCurrent(window);
        glfwSwapInterval(0);

        /*
         * Account for HiDPI screens where window size != framebuffer pixel size.
         */
        try (MemoryStack frame = MemoryStack.stackPush()) {
            IntBuffer framebufferSize = frame.mallocInt(2);
            nglfwGetFramebufferSize(window, memAddress(framebufferSize), memAddress(framebufferSize) + 4);
            width = framebufferSize.get(0);
            height = framebufferSize.get(1);
        }

        GL.createCapabilities();
        debugProc = GLUtil.setupDebugMessageCallback();

        viewMatrix.setLookAt(cameraPosition, cameraLookAt, cameraUp);

        /* Load OBJ model */
        byte[] bytes = readSingleFileZip("org/lwjgl/demo/opengl/raytracing/tutorial6/scene.obj.zip");
        ByteBuffer bb = BufferUtils.createByteBuffer(bytes.length);
        bb.put(bytes).flip();
        AIScene scene = Assimp.aiImportFileFromMemory(bb, 0, "obj");
        model = new Model(scene);
        /* And create KD-tree and triangles SSBOs */
        createSceneSSBOs();

        /* Create all needed GL resources */
        createFramebufferTextures();
        createSampler();
        this.vao = glGenVertexArrays();
        createComputeProgram();
        initComputeProgram();
        createQuadProgram();
        initQuadProgram();

        glfwShowWindow(window);
    }

    /**
     * Return the content of a single file inside of the given zip file as byte
     * array.
     *
     * @param zipResource the classpath of a zip file resources containing a single
     *                    file entry
     * @return the decompressed byte array of that single file
     */
    private static byte[] readSingleFileZip(String zipResource) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(
                Tutorial6.class.getClassLoader().getResourceAsStream(zipResource));
        zipStream.getNextEntry();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read = 0;
        while ((read = zipStream.read(buffer)) > 0) {
            baos.write(buffer, 0, read);
        }
        zipStream.close();
        return baos.toByteArray();
    }

    /**
     * For our compute shader we need to build a list/array of BVH nodes which the
     * shader will index into when reading the BVH nodes. Nodes will also store the
     * indexes/offsets to the next nodes to be visited. In order to do all that,
     * this method walks the BVH tree and assigns offsets/indexes to the BVH nodes.
     *
     * @param node    the root node of the BVH
     * @param indexes will contain the assigned indexes
     */
    private static void allocate(BVH node, Map<BVH, Integer> indexes) {
        Queue<BVH> nodes = new LinkedList<BVH>();
        nodes.add(node);
        while (!nodes.isEmpty()) {
            BVH n = nodes.poll();
            if (n == null)
                continue;
            int index = indexes.size();
            indexes.put(n, Integer.valueOf(index));
            nodes.add(n.left);
            nodes.add(n.right);
        }
    }

    /**
     * Convert the Assimp-imported scene into the Shader Storage Buffer Objects
     * needed for stackless BVH traversal in the compute shader.
     */
    private void createSceneSSBOs() {
        /*
         * First, we clump all meshes into a linear list of non-indexed triangles.
         */
        List<Triangle> triangles = new ArrayList<Triangle>();
        for (Model.Mesh mesh : model.meshes) {
            int trianglesCount = mesh.elementCount / 3;
            for (int i = 0; i < trianglesCount; i++) {
                Triangle t = new Triangle();
                int i0 = mesh.indicesIB.get(i * 3 + 0);
                int i1 = mesh.indicesIB.get(i * 3 + 1);
                int i2 = mesh.indicesIB.get(i * 3 + 2);
                t.v0x = mesh.verticesFB.get(i0 * 3 + 0);
                t.v0y = mesh.verticesFB.get(i0 * 3 + 1);
                t.v0z = mesh.verticesFB.get(i0 * 3 + 2);
                t.v1x = mesh.verticesFB.get(i1 * 3 + 0);
                t.v1y = mesh.verticesFB.get(i1 * 3 + 1);
                t.v1z = mesh.verticesFB.get(i1 * 3 + 2);
                t.v2x = mesh.verticesFB.get(i2 * 3 + 0);
                t.v2y = mesh.verticesFB.get(i2 * 3 + 1);
                t.v2z = mesh.verticesFB.get(i2 * 3 + 2);
                t.n0x = mesh.normalsFB.get(i0 * 3 + 0);
                t.n0y = mesh.normalsFB.get(i0 * 3 + 1);
                t.n0z = mesh.normalsFB.get(i0 * 3 + 2);
                t.n1x = mesh.normalsFB.get(i1 * 3 + 0);
                t.n1y = mesh.normalsFB.get(i1 * 3 + 1);
                t.n1z = mesh.normalsFB.get(i1 * 3 + 2);
                t.n2x = mesh.normalsFB.get(i2 * 3 + 0);
                t.n2y = mesh.normalsFB.get(i2 * 3 + 1);
                t.n2z = mesh.normalsFB.get(i2 * 3 + 2);
                triangles.add(t);
            }
        }
        /*
         * Next, we build a BVH using a simple "centroid split" strategy.
         */
        BVH root = buildBvh(triangles);
        buildNextLinks(root);
        /*
         * Then, we take this BVH and transform it into a structure understood by the
         * GPU/shader, which will be two Shader Storage Buffer Objects. One for the list
         * of BVH nodes and the other for the list of triangles.
         */
        DynamicByteBuffer nodesBuffer = new DynamicByteBuffer();
        DynamicByteBuffer trianglesBuffer = new DynamicByteBuffer();
        bhvToBuffers(root, nodesBuffer, trianglesBuffer);
        /*
         * And finally we upload the two buffers to the SSBOs.
         */
        this.nodesSsbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, nodesSsbo);
        nglBufferData(GL_ARRAY_BUFFER, nodesBuffer.pos, nodesBuffer.addr, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        this.trianglesSsbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, trianglesSsbo);
        nglBufferData(GL_ARRAY_BUFFER, trianglesBuffer.pos, trianglesBuffer.addr, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Heuristic for the BVH algorithm, telling it when to stop splitting a node.
     */
    private static final int MAX_TRIANGLES_IN_NODE = 32;

    /**
     * Build a "centroid split" Bounding Volume Hierarchy of axis-aligned bounding
     * boxes.
     *
     * @param triangles the list of triangles
     * @return the BVH
     */
    private static BVH buildBvh(List<Triangle> triangles) {
        BVH n = new BVH();
        /*
         * Compute the bounding box of the triangles.
         */
        for (Triangle t : triangles) {
            n.minX = Math.min(n.minX, t.min(0));
            n.minY = Math.min(n.minY, t.min(1));
            n.minZ = Math.min(n.minZ, t.min(2));
            n.maxX = Math.max(n.maxX, t.max(0));
            n.maxY = Math.max(n.maxY, t.max(1));
            n.maxZ = Math.max(n.maxZ, t.max(2));
        }
        /*
         * Do we still want to split?
         */
        if (triangles.size() > MAX_TRIANGLES_IN_NODE) {
            /*
             * Yes, so compute the axis with the largest extents.
             */
            float lenX = n.maxX - n.minX;
            float lenY = n.maxY - n.minY;
            float lenZ = n.maxZ - n.minZ;
            int axis = 0;
            if (lenY > lenX)
                axis = 1;
            if (lenZ > lenY && lenZ > lenX)
                axis = 2;
            float c = 0;
            /*
             * And determine the centroid of all triangles along that axis.
             */
            for (Triangle t : triangles)
                c += t.centroid(axis);
            c /= triangles.size();
            /*
             * Next, partition the triangles based on their individual centroids.
             */
            List<Triangle> left = new ArrayList<>(triangles.size() / 2);
            List<Triangle> right = new ArrayList<>(triangles.size() / 2);
            for (Triangle t : triangles) {
                if (t.centroid(axis) < c)
                    left.add(t);
                else
                    right.add(t);
            }
            /*
             * And continue building left and right.
             */
            n.left = buildBvh(left);
            n.left.isLeft = true; // <- this is needed for buildNextLinks()
            n.left.parent = n;
            n.right = buildBvh(right);
            n.right.parent = n;
        } else {
            /*
             * We do not want to split further, so just set the triangles list.
             */
            n.triangles = triangles;
        }
        return n;
    }

    /**
     * This is a custom implementation somewhat derived from
     * <a href="https://sebadorn.de/2015/03/13/stackless-bvh-traversal">Stackless
     * BVH traversal</a> by introducing "hitNext" and "missNext" pointers to each
     * BVH node.
     * <p>
     * The idea is simple: Describe an in-order tree traversal by assigning "next"
     * pointers to each BVH node. So, in GPU memory, each BVH node does not have a
     * left and right child pointer anymore, but a pointer describing where to go
     * next when that particular node has been processed. And in addition to a
     * single simple "next" pointer, we add an optimization by using two pointers.
     * One "hitNext" and one "missNext" pointer. The "hitNext" pointer is followed
     * when the ray intersects this box, and will result in that node's first/left
     * child being visited (if it is not a leaf node), or the next in-order node
     * being visited.
     *
     * @param bvh the BVH to build "next" links for
     */
    private static void buildNextLinks(BVH bvh) {
        if (bvh.left != null)
            buildNextLinks(bvh.left);
        if (bvh.right != null)
            buildNextLinks(bvh.right);

        if (bvh.parent == null) {
            /*
             * We are the root node. In the case of a hit, we visit the left child. In the
             * case of a miss, we are done.
             */
            bvh.hitNext = bvh.left;
            bvh.missNext = null;
        } else if (bvh.isLeft && bvh.left == null) {
            /*
             * We are a left leaf node. In the case of a hit or a miss, we proceed with the
             * right silbing (which always exists).
             */
            bvh.hitNext = bvh.missNext = bvh.parent.right;
        } else if (bvh.isLeft && bvh.left != null) {
            /*
             * We are a left non-leaf. In the case of a hit we visit our left child. In the
             * case of a miss we visit the right sibling.
             */
            bvh.hitNext = bvh.left;
            bvh.missNext = bvh.parent.right;
        } else if (!bvh.isLeft && bvh.left != null) {
            /*
             * Here is where it gets a bit more complicated. When we are a right non-leaf
             * node, we need to figure out which next in-order node to visit next in the
             * case of a miss. See https://sebadorn.de/2015/03/13/stackless-bvh-traversal
             * for schematics on this.
             *
             * But first, we say that in the case of a hit we visit our left child.
             */
            bvh.hitNext = bvh.left;
            BVH next = bvh.parent;
            while (!next.isLeft && next.parent != null)
                next = next.parent;
            if (next.parent != null)
                next = next.parent.right;
            else
                next = null;
            bvh.missNext = next;
        } else if (!bvh.isLeft && bvh.left == null) {
            /*
             * Here is where it gets a bit more complicated. When we are a right leaf node,
             * we need to figure out which next in-order node to visit next. See
             * https://sebadorn.de/2015/03/13/stackless-bvh-traversal for schematics on
             * this.
             */
            BVH next = bvh.parent;
            while (!next.isLeft && next.parent != null)
                next = next.parent;
            if (next.parent != null)
                next = next.parent.right;
            else
                next = null;
            bvh.missNext = bvh.hitNext = next;
        }
    }

    /**
     * Build the memory of the Shader Storage Buffer Objects for the nodes and
     * triangles list.
     * <p>
     * We will use {@link Std430Writer} to write the memory in std430 layout
     * expected by the shader. For this, we simply fill {@link GPUNode} instances.
     *
     * @param root            the BVH root node
     * @param nodesBuffer     a dynamic/growable ByteBuffer holding the BVH nodes
     * @param trianglesBuffer a dynamic/growable ByteBuffer holding the triangles
     */
    private static void bhvToBuffers(BVH root, DynamicByteBuffer nodesBuffer, DynamicByteBuffer trianglesBuffer) {
        Map<BVH, Integer> indexes = new LinkedHashMap<BVH, Integer>();
        // Allocate indexes for each of the nodes
        allocate(root, indexes);
        int triangleIndex = 0;
        List<GPUNode> gpuNodes = new ArrayList<GPUNode>();
        // Iterate over each node in insertion order and write to the buffers
        for (Map.Entry<BVH, Integer> e : indexes.entrySet()) {
            BVH n = e.getKey();
            GPUNode gn = new GPUNode();
            gn.min = new Vector3f(n.minX, n.minY, n.minZ);
            gn.max = new Vector3f(n.maxX, n.maxY, n.maxZ);
            if (n.hitNext != null)
                gn.hitNext = indexes.get(n.hitNext).intValue();
            else
                gn.hitNext = -1;
            if (n.missNext != null)
                gn.missNext = indexes.get(n.missNext).intValue();
            else
                gn.missNext = -1;
            if (n.triangles != null) {
                gn.firstTri = triangleIndex;
                gn.numTris = n.triangles.size();
                triangleIndex += n.triangles.size();
                /* Write triangles to buffer */
                for (int i = 0; i < n.triangles.size(); i++) {
                    Triangle t = n.triangles.get(i);
                    trianglesBuffer.putFloat(t.v0x).putFloat(t.v0y).putFloat(t.v0z).putFloat(1.0f);
                    trianglesBuffer.putFloat(t.v1x).putFloat(t.v1y).putFloat(t.v1z).putFloat(1.0f);
                    trianglesBuffer.putFloat(t.v2x).putFloat(t.v2y).putFloat(t.v2z).putFloat(1.0f);
                    trianglesBuffer.putFloat(t.n0x).putFloat(t.n0y).putFloat(t.n0z).putFloat(0.0f);
                    trianglesBuffer.putFloat(t.n1x).putFloat(t.n1y).putFloat(t.n1z).putFloat(0.0f);
                    trianglesBuffer.putFloat(t.n2x).putFloat(t.n2y).putFloat(t.n2z).putFloat(0.0f);
                }
            } else {
                gn.firstTri = 0; // no triangles
                gn.numTris = 0; // no triangles
            }
            gpuNodes.add(gn);
        }
        // Write GPUNode list to ByteBuffer in std430 layout
        write(gpuNodes, GPUNode.class, nodesBuffer);
    }

    /**
     * Create the full-scren quad shader.
     */
    private void createQuadProgram() throws IOException {
        /*
         * Create program and shader objects for our full-screen quad rendering.
         */
        int program = glCreateProgram();
        int vshader = DemoUtils.createShader("org/lwjgl/demo/opengl/raytracing/tutorial6/quad.vs.glsl", GL_VERTEX_SHADER);
        int fshader = DemoUtils.createShader("org/lwjgl/demo/opengl/raytracing/tutorial6/quad.fs.glsl", GL_FRAGMENT_SHADER);
        glAttachShader(program, vshader);
        glAttachShader(program, fshader);
        glBindFragDataLocation(program, 0, "color");
        glLinkProgram(program);
        int linked = glGetProgrami(program, GL_LINK_STATUS);
        String programLog = glGetProgramInfoLog(program);
        if (programLog.trim().length() > 0) {
            System.err.println(programLog);
        }
        if (linked == 0) {
            throw new AssertionError("Could not link program");
        }
        this.quadProgram = program;
    }

    /**
     * Create the tracing compute shader program.
     */
    private void createComputeProgram() throws IOException {
        /*
         * Create our GLSL compute shader. It does not look any different to creating a
         * program with vertex/fragment shaders. The only thing that changes is the
         * shader type, now being GL_COMPUTE_SHADER.
         */
        int program = glCreateProgram();
        int cshader = DemoUtils.createShader("org/lwjgl/demo/opengl/raytracing/tutorial6/raytracing.glsl", GL_COMPUTE_SHADER);
        int geometry = DemoUtils.createShader("org/lwjgl/demo/opengl/raytracing/tutorial6/geometry.glsl", GL_COMPUTE_SHADER);
        glAttachShader(program, cshader);
        glAttachShader(program, geometry);
        glLinkProgram(program);
        int linked = glGetProgrami(program, GL_LINK_STATUS);
        String programLog = glGetProgramInfoLog(program);
        if (programLog.trim().length() > 0) {
            System.err.println(programLog);
        }
        if (linked == 0) {
            throw new AssertionError("Could not link program");
        }
        this.computeProgram = program;
    }

    /**
     * Initialize the full-screen-quad program. This just binds the program briefly
     * to obtain the uniform locations.
     */
    private void initQuadProgram() {
        glUseProgram(quadProgram);
        int texUniform = glGetUniformLocation(quadProgram, "tex");
        glUniform1i(texUniform, 0);
        glUseProgram(0);
    }

    /**
     * Initialize the compute shader. This just binds the program briefly to obtain
     * the uniform locations, the declared work group size values and the image
     * binding point of the framebuffer image.
     */
    private void initComputeProgram() {
        glUseProgram(computeProgram);
        IntBuffer workGroupSize = BufferUtils.createIntBuffer(3);
        glGetProgramiv(computeProgram, GL_COMPUTE_WORK_GROUP_SIZE, workGroupSize);
        workGroupSizeX = workGroupSize.get(0);
        workGroupSizeY = workGroupSize.get(1);
        eyeUniform = glGetUniformLocation(computeProgram, "eye");
        ray00Uniform = glGetUniformLocation(computeProgram, "ray00");
        ray10Uniform = glGetUniformLocation(computeProgram, "ray10");
        ray01Uniform = glGetUniformLocation(computeProgram, "ray01");
        ray11Uniform = glGetUniformLocation(computeProgram, "ray11");

        /* Query the SSBO binding points */
        IntBuffer props = BufferUtils.createIntBuffer(1);
        IntBuffer params = BufferUtils.createIntBuffer(1);
        props.put(0, GL_BUFFER_BINDING);
        int nodesResourceIndex = glGetProgramResourceIndex(computeProgram, GL_SHADER_STORAGE_BLOCK, "Nodes");
        glGetProgramResourceiv(computeProgram, GL_SHADER_STORAGE_BLOCK, nodesResourceIndex, props, null, params);
        nodesSsboBinding = params.get(0);
        int trianglesResourceIndex = glGetProgramResourceIndex(computeProgram, GL_SHADER_STORAGE_BLOCK, "Triangles");
        glGetProgramResourceiv(computeProgram, GL_SHADER_STORAGE_BLOCK, trianglesResourceIndex, props, null, params);
        trianglesSsboBinding = params.get(0);

        /* Query the image binding points */
        int loc = glGetUniformLocation(computeProgram, "framebufferImage");
        glGetUniformiv(computeProgram, loc, params);
        framebufferImageBinding = params.get(0);

        glUseProgram(0);
    }

    /**
     * Create the textures that will serve as our framebuffer that the compute
     * shader will write/render to.
     */
    private void createFramebufferTextures() {
        pttex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, pttex);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA32F, width, height);
    }

    /**
     * Create the sampler to sample the framebuffer texture within the fullscreen
     * quad shader. We use NEAREST filtering since one texel on the framebuffer
     * texture corresponds exactly to one pixel on the GLFW window framebuffer.
     */
    private void createSampler() {
        this.sampler = glGenSamplers();
        glSamplerParameteri(this.sampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glSamplerParameteri(this.sampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    }

    /**
     * Recreate the framebuffer when the window size changes.
     */
    private void resizeFramebufferTexture() {
        glDeleteTextures(pttex);
        createFramebufferTextures();
    }

    /**
     * Update the camera position based on pressed keys to move around.
     *
     * @param dt the elapsed time since the last frame in seconds
     */
    private void update(float dt) {
        float factor = 1.0f;
        if (keydown[GLFW_KEY_LEFT_SHIFT])
            factor = 3.0f;
        if (keydown[GLFW_KEY_W]) {
            viewMatrix.translateLocal(0, 0, factor * dt);
        }
        if (keydown[GLFW_KEY_S]) {
            viewMatrix.translateLocal(0, 0, -factor * dt);
        }
        if (keydown[GLFW_KEY_A]) {
            viewMatrix.translateLocal(factor * dt, 0, 0);
        }
        if (keydown[GLFW_KEY_D]) {
            viewMatrix.translateLocal(-factor * dt, 0, 0);
        }
        if (keydown[GLFW_KEY_Q]) {
            viewMatrix.rotateLocalZ(-factor * dt);
        }
        if (keydown[GLFW_KEY_E]) {
            viewMatrix.rotateLocalZ(factor * dt);
        }
        if (keydown[GLFW_KEY_LEFT_CONTROL]) {
            viewMatrix.translateLocal(0, factor * dt, 0);
        }
        if (keydown[GLFW_KEY_SPACE]) {
            viewMatrix.translateLocal(0, -factor * dt, 0);
        }

        /* Update matrices */
        projMatrix.setPerspective((float) Math.toRadians(60.0f), (float) width / height, 0.01f, 100.0f);
    }

    /**
     * Trace the scene using our newly built Bounding Volume Hierarchy to avoid
     * unnecessary ray-triangle intersection tests.
     */
    private long lastTime = System.nanoTime();
    private int frame = 0;
    private float avgTime = 0.0f;

    private void trace() {
        glUseProgram(computeProgram);

        /*
         * If the framebuffer size has changed, because the GLFW window was resized, we
         * need to reset the camera's projection matrix and recreate our framebuffer
         * texture.
         */
        if (resetFramebuffer) {
            resizeFramebufferTexture();
            resetFramebuffer = false;
        }

        /*
         * Invert the view-projection matrix to unproject NDC-space coordinates to
         * world-space vectors. See next few statements.
         */
        projMatrix.invertPerspectiveView(viewMatrix, invViewProjMatrix);
        /*
         * Compute and set the view frustum corner rays in the shader for the shader to
         * compute the direction from the eye through a framebuffer's pixel center for a
         * given shader work item.
         */
        viewMatrix.originAffine(cameraPosition);
        glUniform3f(eyeUniform, cameraPosition.x, cameraPosition.y, cameraPosition.z);
        invViewProjMatrix.transformProject(tmpVector.set(-1, -1, 0)).sub(cameraPosition);
        glUniform3f(ray00Uniform, tmpVector.x, tmpVector.y, tmpVector.z);
        invViewProjMatrix.transformProject(tmpVector.set(-1, 1, 0)).sub(cameraPosition);
        glUniform3f(ray01Uniform, tmpVector.x, tmpVector.y, tmpVector.z);
        invViewProjMatrix.transformProject(tmpVector.set(1, -1, 0)).sub(cameraPosition);
        glUniform3f(ray10Uniform, tmpVector.x, tmpVector.y, tmpVector.z);
        invViewProjMatrix.transformProject(tmpVector.set(1, 1, 0)).sub(cameraPosition);
        glUniform3f(ray11Uniform, tmpVector.x, tmpVector.y, tmpVector.z);

        /*
         * Bind level 0 of the path tracer framebuffer texture as writable and readable
         * image in the shader. This tells OpenGL that any writes to and reads from the
         * image defined in our shader is going to go to the first level of the texture
         * 'tex'.
         */
        glBindImageTexture(framebufferImageBinding, pttex, 0, false, 0, GL_READ_WRITE, GL_RGBA32F);
        /*
         * Bind the SSBO containing our BVH nodes.
         */
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, nodesSsboBinding, nodesSsbo);
        /* Bind the SSBO containing our triangles */
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, trianglesSsboBinding, trianglesSsbo);

        /*
         * Compute appropriate global work size dimensions.
         */
        int numGroupsX = (int) Math.ceil((double)width / workGroupSizeX);
        int numGroupsY = (int) Math.ceil((double)height / workGroupSizeY);

        /*
         * Prepare query objects to retrieve the GPU timestamp, which we will use to
         * measure the time it takes the compute shader to execute.
         */
        int q0 = ARBOcclusionQuery.glGenQueriesARB();
        int q1 = ARBOcclusionQuery.glGenQueriesARB();
        ARBTimerQuery.glQueryCounter(q0, ARBTimerQuery.GL_TIMESTAMP);
        /* Execute the shader. */
        glDispatchCompute(numGroupsX, numGroupsY, 1);
        /* Measure the time. */
        ARBTimerQuery.glQueryCounter(q1, ARBTimerQuery.GL_TIMESTAMP);
        while (ARBOcclusionQuery.glGetQueryObjectiARB(q0, ARBOcclusionQuery.GL_QUERY_RESULT_AVAILABLE_ARB) == 0
                || ARBOcclusionQuery.glGetQueryObjectiARB(q1, ARBOcclusionQuery.GL_QUERY_RESULT_AVAILABLE_ARB) == 0) {
            /* Wait until results are available. */
        }
        long time1 = ARBTimerQuery.glGetQueryObjectui64(q0, ARBOcclusionQuery.GL_QUERY_RESULT_ARB);
        long time2 = ARBTimerQuery.glGetQueryObjectui64(q1, ARBOcclusionQuery.GL_QUERY_RESULT_ARB);
        float factor = (float) frame / (frame + 1);
        avgTime = (1.0f - factor) * avgTime + factor * (time2 - time1);
        frame++;
        if (System.nanoTime() - lastTime >= 1E9) {
            System.err.println(avgTime * 1E-6 + " ms.");
            lastTime = System.nanoTime();
            frame = 0;
        }
        ARBOcclusionQuery.glDeleteQueriesARB(q0);
        ARBOcclusionQuery.glDeleteQueriesARB(q1);
        /*
         * Synchronize all writes to the framebuffer image before we let OpenGL source
         * texels from it afterwards when rendering the final image with the full-screen
         * quad.
         */
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

        /* Reset bindings. */
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, nodesSsboBinding, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, trianglesSsboBinding, 0);
        glBindImageTexture(framebufferImageBinding, 0, 0, false, 0, GL_READ_WRITE, GL_RGBA32F);
        glUseProgram(0);
    }

    /**
     * Present the final image on the default framebuffer of the GLFW window.
     */
    private void present() {
        /*
         * Draw the rendered image on the screen using a textured full-screen quad.
         */
        glUseProgram(quadProgram);
        glBindVertexArray(vao);
        glBindTexture(GL_TEXTURE_2D, pttex);
        glBindSampler(0, this.sampler);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindSampler(0, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        glUseProgram(0);
    }

    private void loop() {
        /*
         * Our render loop is really simple...
         */
        float lastTime = System.nanoTime();
        while (!glfwWindowShouldClose(window)) {
            float thisTime = System.nanoTime();
            float dt = (thisTime - lastTime) / 1E9f;
            lastTime = thisTime;
            /*
             * ...we just poll for GLFW window events (as usual).
             */
            glfwPollEvents();
            /*
             * Tell OpenGL about any possibly modified viewport size.
             */
            glViewport(0, 0, width, height);
            /*
             * Update the camera
             */
            update(dt);
            /*
             * Call the compute shader to trace the scene and produce an image in our
             * framebuffer texture.
             */
            trace();
            /*
             * Finally we blit/render the framebuffer texture to the default window
             * framebuffer of the GLFW window.
             */
            present();
            /*
             * Tell the GLFW window to swap buffers so that our rendered framebuffer texture
             * becomes visible.
             */
            glfwSwapBuffers(window);
        }
    }

    private void run() throws Exception {
        try {
            init();
            loop();
            if (debugProc != null)
                debugProc.free();
            errCallback.free();
            keyCallback.free();
            fbCallback.free();
            cpCallback.free();
            mbCallback.free();
            glfwDestroyWindow(window);
        } finally {
            glfwTerminate();
        }
    }

    public static void main(String[] args) throws Exception {
        new Tutorial6().run();
    }

}