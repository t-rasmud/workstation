package org.janelia.workstation.browser.gui.components;

import java.awt.BorderLayout;
import java.util.Properties;

import org.janelia.workstation.browser.gui.progress.ProgressMeterPanel;
import org.janelia.workstation.common.gui.support.WindowLocator;
import org.janelia.workstation.core.workers.BackgroundWorker;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top component which usually slides in from the right side when a background
 * task is executed. Shows progress and "next step" buttons for all 
 * background tasks.
 */
@ConvertAsProperties(
        dtd = "-//org.janelia.workstation.browser.component//ProgressTopComponent//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = ProgressTopComponent.PREFERRED_ID,
        iconBase = "images/cog.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "rightSlidingSide", openAtStartup = true, position=30)
@ActionID(category = "Window", id = "ProgressTopComponent")
@ActionReference(path = "Menu/Window/Core", position = 60)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_ProgressTopComponentAction",
        preferredID = ProgressTopComponent.PREFERRED_ID
)
@Messages({
    "CTL_ProgressTopComponentAction=Background Tasks",
    "CTL_ProgressTopComponent=Background Tasks",
    "HINT_ProgressTopComponentTopComponent=See progress of background tasks"
})
public final class ProgressTopComponent extends TopComponent {

    private static final Logger log = LoggerFactory.getLogger(ProgressTopComponent.class);
    
    public static final String PREFERRED_ID = "ProgressTopComponent";

    private ProgressMeterPanel progressMeterPanel;

    public ProgressTopComponent() {
        initComponents();
        setName(Bundle.CTL_ProgressTopComponent());
        setToolTipText(Bundle.HINT_ProgressTopComponentTopComponent());
        this.progressMeterPanel = new ProgressMeterPanel();
        add(progressMeterPanel, BorderLayout.CENTER);
    }

    public void workerStarted(BackgroundWorker worker) {
        progressMeterPanel.workerStarted(worker);
    }

    public void workerChanged(BackgroundWorker worker) {
        progressMeterPanel.workerChanged(worker);
    }

    public void workerEnded(BackgroundWorker worker) {
        progressMeterPanel.workerEnded(worker);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    
    @Override
    public void componentOpened() {
    }

    @Override
    public void componentClosed() {
    }
    
    void writeProperties(Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        //p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(Properties p) {
        //String version = p.getProperty("version");
        // TODO read your settings according to their version
    }

    public static ProgressTopComponent ensureActive(boolean makeVisible) {
        ProgressTopComponent tc = (ProgressTopComponent) WindowLocator.getByName(ProgressTopComponent.PREFERRED_ID);
        if (tc==null) {
            log.debug("Progress panel not found, creating...");
            String modeName = "rightSlidingSide";
            tc = new ProgressTopComponent();
            Mode mode = WindowManager.getDefault().findMode(modeName);
            if (mode!=null) {
                mode.dockInto(tc);
            }
            else {
                log.warn("No such mode found: "+modeName);
            }
            tc.open();
            tc.requestActive();
        }
        else {
            log.debug("Found progress panel");
            if (makeVisible) {
                if (!tc.isOpened()) {
                    tc.open();
                }
                if (!tc.isVisible()) {
                    log.debug("Progress panel is not visible, making active");
                    tc.requestVisible();
                }
                tc.requestActive();
            }
        }
        return tc;
    }
}
