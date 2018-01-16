package org.janelia.it.workstation.ab2.controller;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.media.opengl.GL4;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;

import org.janelia.it.workstation.ab2.event.*;
import org.janelia.it.workstation.ab2.gl.GLAbstractActor;
import org.janelia.it.workstation.ab2.gl.GLRegionManager;
import org.janelia.it.workstation.ab2.gl.GLSelectable;
import org.janelia.it.workstation.ab2.gl.GLRegion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AB2ControllerMode implements GLEventListener, AB2EventHandler {
    Logger logger = LoggerFactory.getLogger(AB2ControllerMode.class);

    private ConcurrentLinkedQueue<AB2Event> displayEventQueue;

    protected AB2Controller controller;

    protected IntBuffer pickFramebufferId;
    protected IntBuffer pickColorTextureId;
    protected IntBuffer pickDepthTextureId;

    protected long mouseReleaseTimestampMs=0L;

    int[] drawBuffersTargets = new int[]{
            GL4.GL_COLOR_ATTACHMENT0
    };

    IntBuffer drawBuffer = IntBuffer.allocate(1);


    public AB2ControllerMode(AB2Controller controller) {
        this.controller=controller;
        displayEventQueue=new ConcurrentLinkedQueue<>();
        drawBuffer.put(0, drawBuffersTargets[0]);
    }

    public abstract GLRegion getRegionAtPosition(Point point);

    public abstract void start();

    public abstract void stop();

    public abstract void shutdown();

    public abstract GLRegionManager getRegionManager();

    public int getWaitingDisplayEventCount() { return displayEventQueue.size(); }

    /*

    Discussion of Mouse-related Event Handling

    As the user interacts with the application, volatile state associated with user actions that should not be
    included in the persistent "model" of the application is kept in an intance of the AB2UserContext class. Typically,
    this state is temporary information associated with a user action, such as:

     1. drag-and-drop
     2. the currently selected item
     3. the current hover item

     Due to performance concerns in the display loop, each GL actor should not inquire whether they are the
     currently selected item. Rather, whenever the pick buffer indicates a selection, the associated actor is
     marked as selected, and any previously selected actor is marked as not-selected. This ensures only
     one actor is selected at a time.

     Similarly, mouse movement position is used to determine the current hover item, also for which there may only
     be one at a time.

     One important concept is "drop-handling seniority". If a drag-and-drop occurs on a hovered actor, that actor
     receives the drop, which it may then escalate to its associated renderer. Thus, the "bottom up" approach is first.
     On the other hand, if a drop occur        AB2UserContext userContext = AB2Controller.getController().getUserContext();
s at an unmarked pick location, then the drop coordinates are passed to the
     RegionManager, which then determines the region in which it occured, and then the drop is handed to the
     Region for handling.

     Thus, Regions, Renderers, and Actors need to handle Events. However, note that only Regions and Actors have a
     concept of unique geographic bounds, since more than one renderer can share the same screen space. Strictly
     speaking, there can be many more than one Actor on a particular pixel, but there will only be one "owning"
     that pixel in the pick buffer, based on draw order.

     Note that in this approach, it is the Actors and Regions which are given selection and release events to
     process, and it is up to them to escalate with new events with greater sematic meaning.

     Thus, semantic content increases with each step:

     MouseEvent -> Actor/Region Event -> RendererEvent -> Controller Event

     */

    // NOTE: if an even needs access to the pick framebuffer, it should be forwarded to the display event queue.

    public void processEvent(AB2Event event) {
        AB2UserContext userContext = AB2Controller.getController().getUserContext();
        //logger.info("processEvent() entry, type="+event.getClass().getName()+" dragCount="+userContext.getDragObjects().size());
        boolean repaint=false;

        if (event instanceof AB2MouseReleasedEvent) {
            mouseReleaseTimestampMs=new Date().getTime();
            if (userContext.isMouseIsDragging()) {
                //logger.info(">>>   c1");
                // NOTE: this hover object should only be non-null if the current drag objects are all
                // acceptable to the actual hover object. Otherwise, the object should not be
                // registered as the current hover object.
                GLSelectable releaseObject = userContext.getHoverObject();
                //logger.info(">>>   c2");
                List<GLSelectable> dragObjects = userContext.getDragObjects();
                //logger.info(">>>   c3");
                AB2MouseDropEvent dropEvent = new AB2MouseDropEvent(((AB2MouseReleasedEvent) event).getMouseEvent(), dragObjects);
                //logger.info(">>>   c4");
                if (releaseObject!=null && dragObjects.size()>0) {
                    //logger.info(">>>   c5");
                    releaseObject.processEvent(dropEvent);
                }
                for (GLSelectable dragObject : dragObjects) {
                    //logger.info(">>> c5.5");
                    dragObject.releaseAllDrag();
                }
                //logger.info(">>>   c6");
                userContext.clearDrag();
                //logger.info(">>>   c7 - userContext isDragging()="+userContext.isMouseIsDragging());
            }
            //logger.info(">>>   c8");
            repaint=true;
        }
        else if (event instanceof AB2MouseWheelEvent) {
            GLSelectable hoverObject = userContext.getHoverObject();
            if (hoverObject!=null) {
                //logger.info("Handing AB2MouseWheelEvent to hoverObject type="+hoverObject.getClass().getName());
                hoverObject.processEvent(event);
                repaint=true;
            } else {
                //logger.info("hoverObject is null - nothing to hand off AB2MouseWheelEvent");
            }
        }
        else if (event instanceof AB2MouseClickedEvent) {
            //logger.info("processEvent - received event="+event.getClass().getName());
            //userContext.clearDrag();
            displayEventQueue.add(event);
            repaint=true;
        }
        else if (event instanceof AB2MouseMovedEvent) {
            displayEventQueue.add(event);
            repaint=true;
        }
        else if (event instanceof AB2MouseDragEvent) {
            displayEventQueue.add(event);
            repaint=true;
        }
        else if (event instanceof AB2RegionManagerResizeNeededEvent) {
            displayEventQueue.add(event);
            repaint=true;
        }

        ////////////////////////////////////////////////////////////////////////////////////////

        if (repaint) {
            controller.setNeedsRepaint(true);
        }

    }

    static public boolean checkDragAcceptability(GLSelectable hoverObject, List<GLSelectable> dragObjects) {
        if (dragObjects==null || dragObjects.size()<1) {
            return false;
        } else {
            for (GLSelectable dragObject : dragObjects) {
                if (!hoverObject.acceptsDropType(dragObject)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void processDisplayEvent(GLAutoDrawable drawable, AB2Event event) {
        //logger.info("processDisplayEvent() start - type="+event.getClass().getName());
        AB2UserContext userContext=controller.getUserContext();
        GL4 gl=(GL4)drawable.getGL();

        MouseEvent mouseEvent=null;
        int x=-1;
        int y=-1;
        Point p1=null;
        int pickId=-1;
        GLAbstractActor pickActor=null;
        GLSelectable pickObject=null;

        /////////////////////////////////////////////////////////////////////////////////////////////////////
        /// General state update
        /////////////////////////////////////////////////////////////////////////////////////////////////////

        if (event instanceof AB2MouseDragEvent) {
            long msSinceLastRelease=new Date().getTime()-mouseReleaseTimestampMs;
            if (msSinceLastRelease<100) {
                //logger.info(">>>>>>>>>>>>> DISREGARDING DRAG EVENT");
                return;
            }
        }

        if (event instanceof AB2MouseEvent) {
            AB2MouseEvent ab2MouseEvent=(AB2MouseEvent)event;
            mouseEvent=ab2MouseEvent.getMouseEvent();
            x = mouseEvent.getX();
            y = mouseEvent.getY(); // y is inverted - 0 is at the top
            p1 = mouseEvent.getPoint();

//            String p1Status="";
//            if (p1==null) {
//                p1Status+="p1 is null";
//            } else {
//                p1Status+="p1="+p1.x+" "+p1.getX()+" "+p1.y+" "+p1.getY();
//            }
//            logger.info("processDisplayEvent x="+x+" y="+y+" "+p1Status);

            pickId=getPickIdAtXY(gl, x, y, true, true);
            if (pickId>0) {
                pickActor = GLAbstractActor.getActorById(pickId);
                event.setActorPickId(pickId);
            }

            // Need to update hover state
            pickObject = pickActor;
            if (pickObject == null) {
                GLRegion region = getRegionAtPosition(p1);
                if (region != null) {
                    pickObject = region;
                }
            }

            if (pickObject!=null) {
                GLSelectable hoverObject = userContext.getHoverObject();

                if (pickObject instanceof GLRegion) {

                    if (!(pickObject==hoverObject)) {
                        if (hoverObject!=null) {
                            hoverObject.releaseAllHover();
                            userContext.setHoverObject(null);
                        }
                    }

                    boolean dragAcceptable = true;
                    if (userContext.isMouseIsDragging()) {
                        if (pickObject.isHoverable()) {
                            dragAcceptable = checkDragAcceptability(pickObject, userContext.getDragObjects());
                        }
                    }

                    if (dragAcceptable && pickObject.isHoverable()) {
                        userContext.setHoverObject(pickObject);
                        pickObject.setHover();
                    }

                } else if (pickObject instanceof GLAbstractActor) {

                    if (!(hoverObject!=null && hoverObject.getHoverId().equals(pickId))) {
                        if (hoverObject!=null) {
                            hoverObject.releaseAllHover();
                            userContext.setHoverObject(null);
                        }
                    }

                    boolean dragAcceptable = true;
                    if (userContext.isMouseIsDragging()) {
                        if (pickObject.isHoverable(pickId)) {
                            dragAcceptable = checkDragAcceptability(pickObject, userContext.getDragObjects());
                        }
                    }

                    if (dragAcceptable && pickObject.isHoverable(pickId)) {
                        userContext.setHoverObject(pickObject);
                        pickObject.setHover(pickId);
                    }

                }

                controller.setNeedsRepaint(true);
            }

            // DEBUG
//            boolean isSelected=userContext.getSelectObjects().contains(pickObject);
//            boolean isHovered=userContext.getHoverObject()==pickObject;
//            boolean isDragging=userContext.getDragObjects().contains(pickObject);

//            if (pickObject!=null) {
//                logger.info("PICK OBJECT=" + pickObject.getClass().getName());
//                logger.info("   - isSelectable()=" + pickObject.isSelectable());
//                logger.info("   - isHoverable()=" + pickObject.isHoverable());
//                logger.info("   - isDraggable()=" + pickObject.isDraggable());
//                logger.info("     - isSelected()=" + isSelected);
//                logger.info("     - isHovered()=" + isHovered);
//                logger.info("     - isDragging()=" + isDragging);
//                if (pickObject instanceof GLAbstractActor) {
//                    logger.info("     -isDisplay()=" + ((GLAbstractActor) pickObject).getDisplay());
//                }
//            }
        }

        /////////////////////////////////////////////////////////////////////////////////////////////////////
        /// Drag
        /////////////////////////////////////////////////////////////////////////////////////////////////////

        if (event instanceof AB2MouseDragEvent) {
            controller.setNeedsRepaint(true);
            //logger.info("DRAG check1");
            if (!userContext.isMouseIsDragging()) {
                //logger.info("DRAG check2");
                //logger.info("Start of drag, selected objects=");
                List<GLSelectable> selectedObjects = userContext.getSelectObjects();
                if (!selectedObjects.contains(userContext.getHoverObject())) {
                    GLSelectable hoverObject=userContext.getHoverObject();
                    if (hoverObject instanceof GLRegion) {
                        if (hoverObject.isDraggable()) {
                            selectedObjects.add(hoverObject);
                        }
                    } else if (hoverObject instanceof GLAbstractActor) {
                        if (hoverObject.isDraggable(pickId)) {
                            hoverObject.setSelect(pickId);
                            selectedObjects.add(hoverObject);
                        }
                    }
                }
//                for (GLSelectable s : dragObjects) {
//                    logger.info("  >object="+s.getClass().getName());
//                }
                userContext.clearDrag();
                userContext.setMouseIsDragging(true);
                userContext.getPositionHistory().add(p1);
                if (selectedObjects != null && selectedObjects.size() > 0) {
                    List<GLSelectable> permittedDragObjects=new ArrayList<>();
                    for (GLSelectable selectedObject : selectedObjects) {
                        if (selectedObject instanceof GLRegion) {
                            if (selectedObject.isDraggable()) {
                                selectedObject.setDrag();
                                selectedObject.releaseSelect();
                                permittedDragObjects.add(selectedObject);
                            }
                        } else if (selectedObject instanceof GLAbstractActor) {
                            if (selectedObject.getSelectedIds().size() > 0) {
                                List<Integer> selectIds = selectedObject.getSelectedIds();
                                Set<Integer> deselectSet=new HashSet<>();
                                for (Integer selectId : selectIds) {
                                    if (selectedObject.isDraggable(selectId)) {
                                        selectedObject.setDrag(selectId);
                                        deselectSet.add(selectId);
                                    }
                                }
                                for (Integer deselectId : deselectSet) {
                                    selectedObject.releaseSelect(deselectId);
                                }
                            }
                            if (selectedObject.getDraggingIds().size()>0) {
                                permittedDragObjects.add(selectedObject);
                            }
                        }
                    }
                    userContext.clearSelectObjects();
                    userContext.addDragObjects(permittedDragObjects);
                }
                else {
                    if (pickObject!=null) {
                        pickObject.processEvent(event);
                    }
                }
            }
            else {
                // Assume we have an established drag state
                userContext.getPositionHistory().add(p1);
                List<GLSelectable> dragObjects = userContext.getDragObjects();
                if (dragObjects != null) {
                    for (GLSelectable dragObject : dragObjects) {
                        dragObject.processEvent(event);
                    }
                }
                if (pickObject!=null) {
                    pickObject.processEvent(event);
                }
            }
        }

        /////////////////////////////////////////////////////////////////////////////////////////////////////
        /// Click - Shift Down
        /////////////////////////////////////////////////////////////////////////////////////////////////////

        else if (event instanceof AB2MouseClickedEvent && ((AB2MouseClickedEvent) event).getMouseEvent().isShiftDown()) {
            //logger.info("processDisplayEvent() , AB2MouseClickedEvent");
            if (pickObject != null) {

                boolean alreadySelected=userContext.getSelectObjects().contains(pickObject);

                if (pickObject instanceof GLRegion && pickObject.isSelectable()) {

                    if (alreadySelected) {
                        userContext.removeSelectObject(pickObject);
                        pickObject.releaseSelect();
                    } else {
                        userContext.addSelectObject(pickObject);
                        pickObject.setSelect();
                    }

                } else if (pickObject instanceof GLAbstractActor && pickObject.isSelectable(pickId)) {

                    List<Integer> selectedIds = pickObject.getSelectedIds();

                    if (selectedIds.contains(pickId)) {
                        // already selected
                        pickObject.releaseSelect(pickId);
                        if (pickObject.getSelectedIds().size()==0) {
                            userContext.removeSelectObject(pickObject);
                        }

                    } else {
                        // not selected yet
                        pickObject.setSelect(pickId);
                        if (!alreadySelected) {
                            userContext.addSelectObject(pickObject);
                        }
                    }

                }
                pickObject.processEvent(event);
                controller.setNeedsRepaint(true);
            }
        }

        /////////////////////////////////////////////////////////////////////////////////////////////////////
        /// Click - Shift Up
        /////////////////////////////////////////////////////////////////////////////////////////////////////

        else if (event instanceof AB2MouseClickedEvent) {
            //logger.info("processDisplayEvent() , AB2MouseClickedEvent");
            if (pickObject != null) {

                boolean alreadySelected=userContext.getSelectObjects().contains(pickObject);

                if (pickObject instanceof GLRegion && pickObject.isSelectable()) {

                    pickObject.releaseAllSelect();

                    if (!alreadySelected) {
                        userContext.addSelectObject(pickObject);
                        pickObject.setSelect();
                    }

                } else if (pickObject instanceof GLAbstractActor) {

                    List<Integer> selectedIds = pickObject.getSelectedIds();

//                    logger.info("considering adding click id="+pickId);

                    boolean pickIdAlreadySelected=false;

                    if (selectedIds.contains(pickId)) {
  //                      logger.info(">>> check1");
                        pickIdAlreadySelected=true;
                    }

//                    logger.info(">>> check2");

                    if (pickObject.isSelectable(pickId)) {

//                        logger.info(">>> check3");

                        pickObject.releaseAllSelect();

                        if (!pickIdAlreadySelected) {

//                            logger.info(">>> check4");

                            pickObject.setSelect(pickId);
                        }

                        if (!alreadySelected) {

//                            logger.info(">>> check5");

                            userContext.addSelectObject(pickObject);
                        }

//                        logger.info(">>> check6");

                    }

//                    logger.info(">>> check7");

                }
                pickObject.processEvent(event);
                controller.setNeedsRepaint(true);
            }
        }

        /////////////////////////////////////////////////////////////////////////////////////////////////////
        /// Move
        /////////////////////////////////////////////////////////////////////////////////////////////////////

        else if (event instanceof AB2MouseMovedEvent) {
            GLSelectable hoverObject=userContext.getHoverObject();
            if (hoverObject!=null) {
                hoverObject.processEvent(event);
                controller.setNeedsRepaint(true);
            }
        }

        /////////////////////////////////////////////////////////////////////////////////////////////////////
        /// Resize
        /////////////////////////////////////////////////////////////////////////////////////////////////////

        else if (event instanceof AB2RegionManagerResizeNeededEvent) {
            getRegionManager().reshape(drawable, 0, 0, controller.getGlWidth(), controller.getGlHeight());
            controller.setNeedsRepaint(true);
        }

    }

    public int getPickFramebufferId() { return pickFramebufferId.get(0); }

    public IntBuffer getDrawBufferColorAttachment0() { return drawBuffer; }

    @Override
    public void reshape(GLAutoDrawable glAutoDrawable, int x, int y, int width, int height) {
        GL4 gl4=(GL4)(glAutoDrawable.getGL());
        resetPickFramebuffer(gl4, width, height);
    }

    @Override
    public void dispose(GLAutoDrawable glAutoDrawable) {
        GL4 gl4=(GL4)(glAutoDrawable.getGL());
        disposePickFramebuffer(gl4);
        System.gc();
    }

    private void clearDisplay(GL4 gl) {
        // paint solid background color
//        gl.glDisable(GL4.GL_BLEND);
//        gl.glClearColor(0f, 0f, 0f, 1f);
//        gl.glClear(GL4.GL_COLOR_BUFFER_BIT);


        gl.glClear(GL4.GL_DEPTH_BUFFER_BIT | GL4.GL_COLOR_BUFFER_BIT | GL4.GL_STENCIL_BUFFER_BIT);
        gl.glEnable(GL4.GL_DEPTH_TEST);
        gl.glDisable(GL4.GL_BLEND);

//        gl.glBlendEquation(GL4.GL_FUNC_ADD);
//        gl.glDisable(GL4.GL_BLEND);
        //gl.glDepthMask(true);

        //gl.glClearBufferfv(gl.GL_COLOR, 0, backgroundColorBuffer);

        //gl.glDisable(GL4.GL_BLEND);
        //checkGlError(gl, "Check3");



        //gl.glEnable(GL4.GL_BLEND);

        //gl.glBlendEquation(GL4.GL_MAX);

//        gl.glBlendEquation(GL4.GL_FUNC_ADD);
//        checkGlError(gl, "Check8.2");

//        gl.glBlendFunc(GL4.GL_ONE, GL4.GL_DST_ALPHA);
        //gl.glBlendFunc(GL4.GL_SRC_ALPHA, GL4.GL_ONE_MINUS_SRC_ALPHA);

//        gl.glBlendFunc(GL4.GL_SRC_ALPHA_SATURATE, GL4.GL_ONE);

//        gl.glDisable(GL4.GL_BLEND);

//        gl.glColorMask(true, true, true, true);
//        gl.glDepthMask(true);

//        gl.glDisable(GL4.GL_SCISSOR_TEST);
//        gl.glBindFramebuffer(GL4.GL_FRAMEBUFFER, 0);
//        checkGlError(gl, "Check4");
//        gl.glClearColor(0.0f, 0.2f, 0.0f, 1.0f);
//        checkGlError(gl, "Check5");
//        gl.glClearDepth(1.0f);
//        checkGlError(gl, "Check6");
//        gl.glClear(GL4.GL_COLOR_BUFFER_BIT | GL4.GL_DEPTH_BUFFER_BIT | GL4.GL_STENCIL_BUFFER_BIT);
//        checkGlError(gl, "Check1");

//        gl.glFlush();

//        gl.glEnable(GL4.GL_BLEND);
    }

    @Override
    final public void display(GLAutoDrawable glAutoDrawable) {
        GL4 gl4 = (GL4) (glAutoDrawable.getGL());

        gl4.glBindFramebuffer(GL4.GL_FRAMEBUFFER, pickFramebufferId.get(0));
        gl4.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        gl4.glClearDepth(1.0f);
        gl4.glClear(GL4.GL_COLOR_BUFFER_BIT | GL4.GL_DEPTH_BUFFER_BIT);
        gl4.glBindFramebuffer(GL4.GL_FRAMEBUFFER, 0);

        clearDisplay(gl4);

        modeDisplay(glAutoDrawable);
        boolean repaint=false;
        while (!displayEventQueue.isEmpty()) {
            AB2Event event = displayEventQueue.poll();
            if (event != null) {
                processDisplayEvent(glAutoDrawable, event);
                repaint = true;
            }
        }
        if (repaint) {
            controller.setNeedsRepaint(true);
        }
    }

    public abstract void modeDisplay(GLAutoDrawable glAutoDrawable);


    protected int getPickIdAtXY(GL4 gl, int x, int y, boolean invertY, boolean bindFramebuffer) {
        //logger.info("getPickIdAtXY x="+x+" y="+y+" invert="+invertY);
        int fX=x;
        int fY=y;
        if (invertY) {
            fY=controller.getGlHeight()-y-1;
            if (fY<0) { fY=0; }
        }
        if (bindFramebuffer) { gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, pickFramebufferId.get(0)); }
        byte[] pixels = readPixels(gl, pickColorTextureId.get(0), GL4.GL_COLOR_ATTACHMENT0, fX, fY, 1, 1);
        int id = getId(pixels);

        /*
        byte[] pixels = readPixels(gl, pickColorTextureId.get(0), GL4.GL_COLOR_ATTACHMENT0, 0, 0,
                viewport.getWidthPixels(), viewport.getHeightPixels());

        logger.info("readPixels() returned "+pixels.length+" pixels");

        int positiveCount=0;
        int negativeCount=0;
        for (int i=0;i<pixels.length;i++) {
            if (pixels[i]>0) {
                positiveCount++;
            } else if (pixels[i]<0) {
                negativeCount++;
            }
        }
        logger.info("positiveCount="+positiveCount+" negativeCount="+negativeCount);
        */

        if (bindFramebuffer) { gl.glBindFramebuffer(GL4.GL_FRAMEBUFFER, 0); }
        return id; // debug
    }

    // From: https://www.opengl.org/discussion_boards/showthread.php/198703-Framebuffer-Integer-Texture-Attachment
    //
    // NOTE: this needs to be tested on different OSes
    //
    // To encode integer values:
    //
    //    glTexImage2D(GL_TEXTURE_2D, 0, GL_R32I, width, height, 0, GL_RED_INTEGER, GL_INT, 0);
    //
    // To read:
    //
    //    int pixel = 0;
    //    glReadBuffer(GL_COLOR_ATTACHMENT1); // whereever the texture is attached to ...
    //    glReadPixels(x, y, 1, 1, GL_RED_INTEGER, GL_INT, &pixel);
    //
    //
    private byte[] readPixels(GL4 gl, int textureId, int attachment, int startX, int startY, int width, int height) {
        gl.glBindTexture(GL4.GL_TEXTURE_2D, textureId);
        int pixelSize = Integer.SIZE/Byte.SIZE;
        int bufferSize = width * height * pixelSize;
        byte[] rawBuffer = new byte[bufferSize];
        ByteBuffer buffer = ByteBuffer.wrap(rawBuffer);
        gl.glReadBuffer(attachment);
        //logger.info("glReadPixels() startX="+startX+" startY="+startY+" width="+width+" height="+height);
        gl.glReadPixels(startX, startY, width, height, GL4.GL_RED_INTEGER, GL4.GL_INT, buffer);
        GLAbstractActor.checkGlError(gl, "AB2Renderer3D readPixels(), after glReadPixels()");
        gl.glBindTexture(GL4.GL_TEXTURE_2D, 0);
        return rawBuffer;
    }

    private int getId(byte[] rawBuffer) {
//        for (int i=0;i<rawBuffer.length;i++) {
//            logger.info("getId byte "+i+" = "+rawBuffer[i]);
//        }
        ByteBuffer bb = ByteBuffer.wrap(rawBuffer);

        if(ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN)
            bb.order(ByteOrder.LITTLE_ENDIAN);

        return bb.getInt();
    }

    protected void disposePickFramebuffer(GL4 gl) {
        if (pickDepthTextureId!=null) {
            gl.glDeleteTextures(1, pickDepthTextureId);
            pickDepthTextureId=null;
        }
        if (pickColorTextureId!=null) {
            gl.glDeleteTextures(1, pickColorTextureId);
            pickColorTextureId=null;
        }
        if (pickFramebufferId!=null) {
            gl.glDeleteFramebuffers(1, pickFramebufferId);
            pickFramebufferId=null;
        }
    }

    protected void resetPickFramebuffer(GL4 gl, int width, int height) {
        //logger.info("resetPickFramebuffer start");
        disposePickFramebuffer(gl);

        pickFramebufferId=IntBuffer.allocate(1);
        gl.glGenFramebuffers(1, pickFramebufferId);
        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, pickFramebufferId.get(0));

        pickColorTextureId=IntBuffer.allocate(1);
        gl.glGenTextures(1, pickColorTextureId);
        gl.glBindTexture(gl.GL_TEXTURE_2D, pickColorTextureId.get(0));
        gl.glTexParameteri(GL4.GL_TEXTURE_2D, GL4.GL_TEXTURE_MIN_FILTER, GL4.GL_NEAREST);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D, GL4.GL_TEXTURE_MAG_FILTER, GL4.GL_NEAREST);
        gl.glTexImage2D(GL4.GL_TEXTURE_2D, 0, GL4.GL_R32I, width, height, 0, GL4.GL_RED_INTEGER, GL4.GL_INT, null);

        pickDepthTextureId=IntBuffer.allocate(1);
        gl.glGenTextures(1, pickDepthTextureId);
        gl.glBindTexture(gl.GL_TEXTURE_2D, pickDepthTextureId.get(0));
        gl.glTexImage2D(GL4.GL_TEXTURE_2D, 0, GL4.GL_DEPTH_COMPONENT, width, height, 0, GL4.GL_DEPTH_COMPONENT, GL4.GL_FLOAT, null);

        gl.glFramebufferTexture(gl.GL_FRAMEBUFFER, gl.GL_COLOR_ATTACHMENT0, pickColorTextureId.get(0), 0);
        gl.glFramebufferTexture(gl.GL_FRAMEBUFFER, gl.GL_DEPTH_ATTACHMENT, pickDepthTextureId.get(0), 0);

        int status = gl.glCheckFramebufferStatus(GL4.GL_FRAMEBUFFER);
        if (status != GL4.GL_FRAMEBUFFER_COMPLETE) {
            logger.error("Failed to establish framebuffer: {}", decodeFramebufferStatus(status));
        }
        else {
            //logger.info("Picking Framebuffer complete.");
        }

        gl.glBindFramebuffer(gl.GL_FRAMEBUFFER, 0);
        gl.glBindTexture(gl.GL_TEXTURE_2D, 0);
        //logger.info("resetPickFrameBuffer end");

    }

    private String decodeFramebufferStatus( int status ) {
        String rtnVal = null;
        switch (status) {
            case GL4.GL_FRAMEBUFFER_UNDEFINED:
                rtnVal = "GL_FRAMEBUFFER_UNDEFINED means target is the default framebuffer, but the default framebuffer does not exist.";
                break;
            case GL4.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT :
                rtnVal = "GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT is returned if any of the framebuffer attachment points are framebuffer incomplete.";
                break;
            case GL4.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT:
                rtnVal = "GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT is returned if the framebuffer does not have at least one image attached to it.";
                break;
            case GL4.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER:
                rtnVal = "GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER is returned if the value of GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE\n" +
                        "		 is GL_NONE for any color attachment point(s) named by GL_DRAW_BUFFERi.";
                break;
            case GL4.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER:
                rtnVal = "GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER is returned if GL_READ_BUFFER is not GL_NONE\n" +
                        "		 and the value of GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE is GL_NONE for the color attachment point named\n" +
                        "		 by GL_READ_BUFFER.";
                break;
            case GL4.GL_FRAMEBUFFER_UNSUPPORTED:
                rtnVal = "GL_FRAMEBUFFER_UNSUPPORTED is returned if the combination of internal formats of the attached images violates\n" +
                        "		 an implementation-dependent set of restrictions.";
                break;
            case GL4.GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE:
                rtnVal = "GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE is returned if the value of GL_RENDERBUFFER_SAMPLES is not the same\n" +
                        "		 for all attached renderbuffers; if the value of GL_TEXTURE_SAMPLES is the not same for all attached textures; or, if the attached\n" +
                        "		 images are a mix of renderbuffers and textures, the value of GL_RENDERBUFFER_SAMPLES does not match the value of\n" +
                        "		 GL_TEXTURE_SAMPLES.  GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE  also returned if the value of GL_TEXTURE_FIXED_SAMPLE_LOCATIONS is\n" +
                        "		 not the same for all attached textures; or, if the attached images are a mix of renderbuffers and textures, the value of GL_TEXTURE_FIXED_SAMPLE_LOCATIONS\n" +
                        "		 is not GL_TRUE for all attached textures.";
                break;
            case GL4.GL_FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS:
                rtnVal = " is returned if any framebuffer attachment is layered, and any populated attachment is not layered,\n" +
                        "		 or if all populated color attachments are not from textures of the same target.";
                break;
            default:
                rtnVal = "--Message not decoded: " + status;
        }
        return rtnVal;
    }


}
