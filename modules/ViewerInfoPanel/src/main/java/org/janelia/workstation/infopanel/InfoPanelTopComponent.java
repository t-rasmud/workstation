package org.janelia.workstation.infopanel;

import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

@ConvertAsProperties(
        dtd = "-//org.janelia.workstation.gui.task_workflow//InfoPanelTopComponent//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = InfoPanelTopComponent.PREFERRED_ID,
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_NEVER
)
@TopComponent.Registration(mode = "properties", openAtStartup = false, position = 500)
@ActionID(category = "Window", id = "org.janelia.workstation.gui.task_workflow.InfoPanelTopComponentTopComponent")
@ActionReference(path = "Menu/Window/Horta", position = 103)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_InfoPanelTopComponentAction",
        preferredID = InfoPanelTopComponent.PREFERRED_ID
)
@Messages({
    "CTL_InfoPanelTopComponentAction=Info Panel",
    "CTL_InfoPanelTopComponentTopComponent=" + InfoPanelTopComponent.LABEL_TEXT,
    "HINT_InfoPanelTopComponentTopComponent=Info Panel"
})
public final class InfoPanelTopComponent extends TopComponent {
    private static final Logger log = LoggerFactory.getLogger(InfoPanelTopComponent.class);
    public static final String PREFERRED_ID = "InfoPanelTopComponent";
    public static final String LABEL_TEXT = "Horta Control Center";


    // Annotation Panel Layout
    private AnnotationPanel annotationPanel;

    public InfoPanelTopComponent() {
        setName(Bundle.CTL_InfoPanelTopComponentTopComponent());
        setToolTipText(Bundle.HINT_InfoPanelTopComponentTopComponent());

        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        annotationPanel = new AnnotationPanel();
        add (annotationPanel);

    }


    @Override
    public Dimension getPreferredSize() {
        return new Dimension(250, 0);
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
    }

    public static final InfoPanelTopComponent getInstance() {
        return (InfoPanelTopComponent)WindowManager.getDefault().findTopComponent(PREFERRED_ID);
    }
    
    @Override
    public void componentOpened() {

    }

    @Override
    public void componentClosed() {
    }

}
