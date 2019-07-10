package org.janelia.workstation.browser.gui.components;

import java.awt.BorderLayout;
import java.util.Map;

import com.google.common.eventbus.Subscribe;
import org.janelia.model.domain.DomainObject;
import org.janelia.workstation.browser.gui.inspector.DomainInspectorPanel;
import org.janelia.workstation.common.gui.support.WindowLocator;
import org.janelia.workstation.core.events.Events;
import org.janelia.workstation.core.events.selection.DomainObjectSelectionEvent;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top component for the Data Inspector, which shows details about a single domain object, 
 * and allows users to change its permissions. 
 * 
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
@ConvertAsProperties(
        dtd = "-//org.janelia.workstation.browser.components//DomainInspector//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = DomainInspectorTopComponent.TC_NAME,
        iconBase = "images/zoom.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "appExplorerBtm", openAtStartup = true, position = 20)
@ActionID(category = "Window", id = "org.janelia.workstation.browser.components.DomainInspectorTopComponent")
@ActionReference(path = "Menu/Window/Core", position = 30)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_DomainInspectorAction",
        preferredID = DomainInspectorTopComponent.TC_NAME
)
@Messages({
    "CTL_DomainInspectorAction=Data Inspector",
    "CTL_DomainInspectorTopComponent=Data Inspector",
    "HINT_DomainInspectorTopComponent=Details about the selected domain object"
})
public final class DomainInspectorTopComponent extends TopComponent {

    private Logger log = LoggerFactory.getLogger(DomainInspectorTopComponent.class);

    public static final String TC_NAME = "DomainInspectorTopComponent";
    public static final String TC_VERSION = "1.0";

    public static DomainInspectorTopComponent getInstance() {
        return (DomainInspectorTopComponent) WindowLocator.getByName(DomainInspectorTopComponent.TC_NAME);
    }
    
    private final DomainInspectorPanel detailsPanel;

    public DomainInspectorTopComponent() {
        initComponents();
        setName(Bundle.CTL_DomainInspectorTopComponent());
        setToolTipText(Bundle.HINT_DomainInspectorTopComponent());
        this.detailsPanel = new DomainInspectorPanel();
        add(detailsPanel, BorderLayout.CENTER);
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    
    @Override
    protected void componentActivated() {
    }
    
    @Override
    protected void componentDeactivated() {
    }

    @Override
    public void componentOpened() {
        Events.getInstance().registerOnEventBus(this);
    }

    @Override
    public void componentClosed() {
        Events.getInstance().unregisterOnEventBus(this);
    }
    
    void writeProperties(java.util.Properties p) {
        // This component does not need to save its state, because other components (such as the Data Explorer) 
        // will load into it when they read their properties on startup.
    }

    void readProperties(java.util.Properties p) {
    }
    
    // Custom methods

    public void inspect(Object object) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void inspect(DomainObject domainObject) {
        detailsPanel.loadDomainObject(domainObject);
    }

    public void inspect(Map<String, Object> properties) {
        detailsPanel.loadProperties(properties);
    }
    
    @Subscribe
    public void domainObjectSelected(DomainObjectSelectionEvent event) {

        // We only care about single selections
        DomainObject domainObject = event.getObjectIfSingle();
        if (domainObject==null) {
            return;
        }
        
        if (!event.isSelect()) {
            log.debug("Event is not selection: {}",event);
            return;
        }

        if (event.isUserDriven()) {
            log.info("domainObjectSelected({})", domainObject);
            detailsPanel.loadDomainObject(domainObject);
        }
    }
}
