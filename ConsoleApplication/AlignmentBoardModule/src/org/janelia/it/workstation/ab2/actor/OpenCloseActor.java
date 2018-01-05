package org.janelia.it.workstation.ab2.actor;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.media.opengl.GL4;

import org.janelia.geometry3d.Vector3;
import org.janelia.geometry3d.Vector4;
import org.janelia.it.workstation.ab2.gl.GLAbstractActor;
import org.janelia.it.workstation.ab2.gl.GLShaderProgram;
import org.janelia.it.workstation.ab2.renderer.AB2Renderer2D;
import org.janelia.it.workstation.ab2.shader.AB2Basic2DShader;
import org.janelia.it.workstation.ab2.shader.AB2PickShader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenCloseActor extends GLAbstractActor {

    private final Logger logger = LoggerFactory.getLogger(OpenCloseActor.class);

    Vector3 position;
    float size;

    IntBuffer backgroundVertexArrayId=IntBuffer.allocate(1);
    IntBuffer backgroundVertexBufferId=IntBuffer.allocate(1);

    IntBuffer openVertexArrayId=IntBuffer.allocate(1);
    IntBuffer openVertexBufferId=IntBuffer.allocate(1);

    IntBuffer closedVertexArrayId=IntBuffer.allocate(1);
    IntBuffer closedVertexBufferId=IntBuffer.allocate(1);

    FloatBuffer backgroundVertexFb;
    FloatBuffer openVertexFb;
    FloatBuffer closedVertexFb;

    Vector4 backgroundColor;
    Vector4 foregroundColor;
    Vector4 hoverColor;
    Vector4 selectColor;

    AB2Renderer2D renderer2d;

    public OpenCloseActor(AB2Renderer2D renderer, int actorId, Vector3 position, Vector4 foregroundColor,
                           Vector4 backgroundColor, Vector4 hoverColor, Vector4 selectColor) {
        super(renderer, actorId);
        this.renderer2d=renderer;
        this.position=position;
        this.foregroundColor=foregroundColor;
        this.backgroundColor=backgroundColor;
        this.hoverColor=hoverColor;
        this.selectColor=selectColor;
    }

    public Vector4 getForegroundColor() {
        return foregroundColor;
    }

    public void setForegroundColor(Vector4 color) {
        this.foregroundColor=foregroundColor;
    }

    public Vector4 getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Vector4 color) {
        this.backgroundColor=backgroundColor;
    }

    public Vector4 getHoverColor() {
        return hoverColor;
    }

    public void setHoverColor(Vector4 hoverColor) {
        this.hoverColor = hoverColor;
    }

    public Vector4 getSelectColor() {
        return selectColor;
    }

    public void setSelectColor(Vector4 selectColor) {
        this.selectColor = selectColor;
    }


    public void updatePosition(Vector3 position) {
        this.position=position;
        needsResize=true;
    }

    // In this case, we just need a single position, since we will try using a point
    // with a large size for the background.

    private float[] computeBackgroundVertexData() {

        float[] vertexData = {
                position.get(0), position.get(1), position.get(2)
        };

        return vertexData;
    }

    // Here, we just want a little squre in the middle of the point circle.

    private float[] computeForegroundOpenVertexData() {

        float sl=(this.size*0.8f)/2.0f;

        float cx=position.get(0);
        float cy=position.get(1);
        float cz=position.get(2);

        float[] vertexData = {
                cx-sl, cy-sl, cz, // lower left
                cx-sl, cy+sl, cz, // upper left

                cx-sl, cy+sl, cz, // upper left
                cx+sl, cy+sl, cz, // upper right

                cx+sl, cy+sl, cz, // upper right
                cx+sl, cy-sl, cz, // lower right

                cx+sl, cy-sl, cz, // lower right
                cx-sl, cy-sl, cz, // lower left
        };

        return vertexData;
    }

    // Here, we want a simple horizontal line.

    private float[] computeForegroundClosedVertexData() {

        float sl=(this.size*0.8f)/2.0f;

        float cx=position.get(0);
        float cy=position.get(1);
        float cz=position.get(2);

        float[] vertexData = {
                cx-sl, cy, cz,
                cx+sl, cy, cz
        };

        return vertexData;
    }

    @Override
    public boolean isTwoDimensional() { return true; }

    @Override
    public void init(GL4 gl, GLShaderProgram shader) {

        if (shader instanceof AB2Basic2DShader) {

            AB2Basic2DShader basic2DShader=(AB2Basic2DShader)shader;

            float[] backgroundVertexData=computeBackgroundVertexData();
            float[] openVertexData=computeForegroundOpenVertexData();
            float[] closedVertexData=computeForegroundClosedVertexData();

            backgroundVertexFb=createGLFloatBuffer(backgroundVertexData);
            openVertexFb=createGLFloatBuffer(openVertexData);
            closedVertexFb=createGLFloatBuffer(closedVertexData);

            gl.glGenVertexArrays(1, backgroundVertexArrayId);
            gl.glBindVertexArray(backgroundVertexArrayId.get(0));
            gl.glGenBuffers(1, backgroundVertexBufferId);
            gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, backgroundVertexBufferId.get(0));
            gl.glBufferData(GL4.GL_ARRAY_BUFFER, backgroundVertexFb.capacity() * 4, backgroundVertexFb, GL4.GL_STATIC_DRAW);
            gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0);

            gl.glGenVertexArrays(1, openVertexArrayId);
            gl.glBindVertexArray(openVertexArrayId.get(0));
            gl.glGenBuffers(1, openVertexBufferId);
            gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, openVertexBufferId.get(0));
            gl.glBufferData(GL4.GL_ARRAY_BUFFER, openVertexFb.capacity() * 4, openVertexFb, GL4.GL_STATIC_DRAW);
            gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0);

            gl.glGenVertexArrays(1, closedVertexArrayId);
            gl.glBindVertexArray(closedVertexArrayId.get(0));
            gl.glGenBuffers(1, closedVertexBufferId);
            gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, closedVertexBufferId.get(0));
            gl.glBufferData(GL4.GL_ARRAY_BUFFER, closedVertexFb.capacity() * 4, closedVertexFb, GL4.GL_STATIC_DRAW);
            gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0);

        }

    }

    private void updateVertexBuffers(GL4 gl) {

        float[] backgroundVertexData=computeBackgroundVertexData();
        float[] openVertexData=computeForegroundOpenVertexData();
        float[] closedVertexData=computeForegroundClosedVertexData();

        backgroundVertexFb=createGLFloatBuffer(backgroundVertexData);
        openVertexFb=createGLFloatBuffer(openVertexData);
        closedVertexFb=createGLFloatBuffer(closedVertexData);

        gl.glBindVertexArray(backgroundVertexArrayId.get(0));
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, backgroundVertexBufferId.get(0));
        gl.glBufferSubData(GL4.GL_ARRAY_BUFFER, 0, backgroundVertexFb.capacity() * 4, backgroundVertexFb);
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0);

        gl.glBindVertexArray(openVertexArrayId.get(0));
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, openVertexBufferId.get(0));
        gl.glBufferSubData(GL4.GL_ARRAY_BUFFER, 0, openVertexFb.capacity() * 4, openVertexFb);
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0);

        gl.glBindVertexArray(closedVertexArrayId.get(0));
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, closedVertexBufferId.get(0));
        gl.glBufferSubData(GL4.GL_ARRAY_BUFFER, 0, closedVertexFb.capacity() * 4, closedVertexFb);
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0);

    }

    @Override
    public void display(GL4 gl, GLShaderProgram shader) {

        //logger.info("display() called");

        if (needsResize) {
            updateVertexBuffers(gl);
            needsResize=false;
        }

        //todo: finish this

        if (shader instanceof AB2Basic2DShader) {
            AB2Basic2DShader basic2DShader=(AB2Basic2DShader)shader;
            basic2DShader.setMVP2d(gl, getModelMatrix().multiply(renderer2d.getVp2d()));
            if (isSelected) {
                basic2DShader.setColor(gl, selectColor);
            } else if (isHovered) {
                basic2DShader.setColor(gl, hoverColor);
            } else {
                basic2DShader.setColor(gl, color);
            }
        } else if (shader instanceof AB2PickShader) {
            AB2PickShader pickShader=(AB2PickShader)shader;
            pickShader.setMVP(gl, getModelMatrix().multiply(renderer2d.getVp2d()));
            pickShader.setPickId(gl, actorId);
        }

        gl.glBindVertexArray(vertexArrayId.get(0));
        checkGlError(gl, "d3 glBindVertexArray()");

        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, vertexBufferId.get(0));
        checkGlError(gl, "d4 glBindBuffer()");

        gl.glVertexAttribPointer(0, 3, GL4.GL_FLOAT, false, 0, 0);
        checkGlError(gl, "d5 glVertexAttribPointer()");

        gl.glEnableVertexAttribArray(0);
        checkGlError(gl, "d6 glEnableVertexAttribArray()");

        gl.glDrawArrays(GL4.GL_TRIANGLES, 0, vertexFb.capacity()/2);
        checkGlError(gl, "d9 glDrawArrays()");

        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0);
        checkGlError(gl, "d10 glBindBuffer()");

    }

    @Override
    public void dispose(GL4 gl, GLShaderProgram shader) {
        if (shader instanceof AB2Basic2DShader) {
            gl.glDeleteVertexArrays(1, vertexArrayId);
            gl.glDeleteBuffers(1, vertexBufferId);
        }
        super.dispose(gl, shader);
    }

}
