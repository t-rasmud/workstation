package org.janelia.workstation.gui.large_volume_viewer.controller;

import com.google.common.eventbus.Subscribe;
import org.janelia.workstation.controller.listener.ColorModelListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.JComponent;
import org.janelia.workstation.controller.ViewerEventBus;
import org.janelia.workstation.controller.eventbus.LoadProjectEvent;
import org.janelia.workstation.controller.eventbus.UnloadProjectEvent;
import org.janelia.workstation.controller.model.TmModelManager;
import org.janelia.workstation.geom.Vec3;
import org.janelia.workstation.controller.model.color.ImageColorModel;
import org.janelia.workstation.controller.listener.LoadStatusListener;
import org.janelia.workstation.controller.listener.ViewStateListener;
import org.janelia.workstation.gui.large_volume_viewer.LargeVolumeViewer;
import org.janelia.workstation.gui.large_volume_viewer.OrthogonalPanel;
import org.janelia.workstation.gui.large_volume_viewer.QuadViewUi;
import org.janelia.workstation.controller.tileimagery.TileServer;
import org.janelia.workstation.gui.large_volume_viewer.action.GoToLocationAction;
import org.janelia.workstation.gui.large_volume_viewer.action.MouseMode;
import org.janelia.workstation.gui.large_volume_viewer.action.PanModeAction;
import org.janelia.workstation.gui.large_volume_viewer.action.RecentFileList;
import org.janelia.workstation.gui.large_volume_viewer.action.TraceMouseModeAction;
import org.janelia.workstation.gui.large_volume_viewer.action.WheelMode;
import org.janelia.workstation.gui.large_volume_viewer.action.ZScanScrollModeAction;
import org.janelia.workstation.gui.large_volume_viewer.action.ZoomMouseModeAction;
import org.janelia.workstation.gui.large_volume_viewer.action.ZoomScrollModeAction;
import org.janelia.workstation.gui.large_volume_viewer.listener.*;
import org.janelia.workstation.gui.large_volume_viewer.tracing.PathTraceToParentRequest;
import org.janelia.model.domain.tiledMicroscope.TmColorModel;

/**
 * External controller of the Quad View UI.  Distances it from incoming
 * directives.
 * 
 * @author fosterl
 */
public class QuadViewController implements ViewStateListener {
    private QuadViewUi ui;
    private final AnnotationManager annoMgr;
    private final LargeVolumeViewer lvv;
    private final QuadViewController.QvucMouseWheelModeListener qvucmwListener = new QuadViewController.QvucMouseWheelModeListener();
    private final QvucColorModelListener qvucColorModelListener = new QvucColorModelListener();
    private final Collection<MouseWheelModeListener> relayMwmListeners = new ArrayList<>();
    private final Collection<ColorModelListener> relayCMListeners = new ArrayList<>();
    private final Collection<JComponent> orthPanels = new ArrayList<>();
           
    public QuadViewController(QuadViewUi ui, AnnotationManager annoMgr, LargeVolumeViewer lvv) {
        this.ui = ui;
        this.annoMgr = annoMgr;
        this.lvv = lvv;
        lvv.setMessageListener(new QvucMessageListener());
        this.ui.setPathTraceListener(new QvucPathRequestListener());
        registerLoadRequests();
    }

    private void registerLoadRequests() {
        ViewerEventBus.registerForEvents(this);
    }

    @Subscribe
    public void loadProject(LoadProjectEvent event) {
        URL tileURL = TmModelManager.getInstance().getTileLoader().getUrl();
        ui.loadDataFromURL(tileURL);
    }

   // @Subscribe
    public void unloadProject(UnloadProjectEvent event) {

    }
    
    @Override
    public void setCameraFocus(Vec3 focus) {
        ui.setCameraFocus(focus);
    }
    
    @Override
    public void loadColorModel(TmColorModel colorModel) {
        ui.setImageColorModel(colorModel);
    }
    
    @Override
    public void pathTraceRequested(Long neuronId, Long annotationId) {
        ui.pathTraceRequested(neuronId, annotationId);
    }
    
    @Override
    public void centerNextParent() {
        ui.centerNextParentMicron();
    }
    
    public void registerForEvents(PanModeAction pma) {
        pma.setMwmListener(qvucmwListener);
    }
    
    public void registerForEvents(ZoomMouseModeAction zmma) {
        zmma.setMwmListener(qvucmwListener);
    }
    
    public void registerForEvents(TraceMouseModeAction tmma) {
        tmma.setMwmListener(qvucmwListener);
    }
    
    public void registerForEvents(ZoomScrollModeAction zsma) {
        zsma.setMwmListener(qvucmwListener);
    }
    
    public void registerForEvents(ZScanScrollModeAction zssma) {
        zssma.setMwmListener(qvucmwListener);
    }
    
    /** Repaint events on any component. */
    public void registerAsOrthPanelForRepaint(JComponent component) {
        orthPanels.add(component);
        relayCMListeners.add(new QvucRepaintColorModelListener(component));
    }
    
    public void registerForEvents(OrthogonalPanel op) {
        orthPanels.add(op);
        op.setMessageListener(new QvucMessageListener());
        relayMwmListeners.add(op);
        relayCMListeners.add(new QvucRepaintColorModelListener(op));
    }
    
    /** Since orthogonal panels are created on demand, they should all be unregistered before registering new ones. */
    public void unregisterOrthPanels() {
        for (JComponent op: orthPanels) {
            Collection<MouseWheelModeListener> tempListeners = new ArrayList<>(relayMwmListeners);
            for (MouseWheelModeListener l: relayMwmListeners) {
                if (l == op) {
                    tempListeners.remove(l);
                }
            }
            relayMwmListeners.clear();
            relayMwmListeners.addAll(tempListeners);
            tempListeners = null;
            Collection<ColorModelListener> tempListeners2 = new ArrayList<>(relayCMListeners);
            for (ColorModelListener l: relayCMListeners) {
                if (l instanceof QvucRepaintColorModelListener) {
                    QvucRepaintColorModelListener rl = (QvucRepaintColorModelListener)l;
                    if (rl.getComponent() == op) {
                        tempListeners2.remove(l);
                    }
                }
            }
            relayCMListeners.clear();
            relayCMListeners.addAll(tempListeners2);
            tempListeners2 = null;
        }
        orthPanels.clear();
    }
    
    public void registerForEvents(RecentFileList rfl) {
        rfl.setUrlLoadListener(new QvucUrlLoadListener());
    }
    
    public void registerForEvents(ImageColorModel icm) {
        icm.addColorModelListener(qvucColorModelListener);
    }
    
    public void registerForEvents(TileServer tileServer) {
        tileServer.setLoadStatusListener(new QvucLoadStatusListener());
    }
    
    public void registerForEvents(GoToLocationAction action) {
        action.setListener(new QvucGotoListener());
    }
    
    public void mouseModeChanged(MouseMode.Mode mode) {
        lvv.setMouseMode(mode);
        ui.setMouseMode(mode);
        for (MouseWheelModeListener l: relayMwmListeners) {
            l.setMode(mode);
        }
    }
    
    public void wheelModeChanged(WheelMode.Mode mode) {
        lvv.setWheelMode(mode);
        for (MouseWheelModeListener l: relayMwmListeners) {
            l.setMode(mode);
        }
    }
    
    private class QvucGotoListener implements CameraPanToListener {

        @Override
        public void cameraPanTo(Vec3 location) {
            ui.setCameraFocus( location );
        }
        
    }
    
    private class QvucMouseWheelModeListener implements MouseWheelModeListener {

        @Override
        public void setMode(MouseMode.Mode modeId) {
            mouseModeChanged(modeId);
        }

        @Override
        public void setMode(WheelMode.Mode modeId) {
            wheelModeChanged(modeId);
        }
        
    }
    
    private class QvucUrlLoadListener implements UrlLoadListener {

        @Override
        public void loadUrl(URL url) {
            ui.loadRender(url);
        }
        
    }
    
    private class QvucMessageListener implements MessageListener {

        @Override
        public void message(String msg) {
            ui.setStatusLabelText(msg);
        }
        
    }
    
    private class QvucColorModelListener implements ColorModelListener {

        @Override
        public void colorModelChanged() {
            for (ColorModelListener l: relayCMListeners) {
                l.colorModelChanged();
            }
            ui.updateSliderLockButtons();
        }
        
    }
    
    private class QvucRepaintColorModelListener implements ColorModelListener {
        private JComponent component;
        
        QvucRepaintColorModelListener(JComponent component) {
            this.component = component;
        }
        
        @Override
        public void colorModelChanged() {
            if (component != null)
                component.repaint();
        }
        
        public JComponent getComponent() {
            return component;
        }
        
    }
    
    private class QvucLoadStatusListener implements LoadStatusListener {

        @Override
        public void updateLoadStatus(TileServer.LoadStatus loadStatus) {
            ui.setLoadStatus(loadStatus);
        }
        
    }
    
    private class QvucPathRequestListener implements PathTraceRequestListener {

        @Override
        public void pathTrace(PathTraceToParentRequest request) {
            annoMgr.tracePathToParent(request);
        }
        
    }
}
