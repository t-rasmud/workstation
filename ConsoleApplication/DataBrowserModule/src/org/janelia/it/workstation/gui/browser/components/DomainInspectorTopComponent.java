package org.janelia.it.workstation.gui.browser.components;

import java.awt.BorderLayout;

import org.janelia.it.jacs.model.domain.DomainObject;
import org.janelia.it.jacs.model.domain.Reference;
import org.janelia.it.jacs.model.domain.sample.PipelineResult;
import org.janelia.it.workstation.gui.browser.events.Events;
import org.janelia.it.workstation.gui.browser.events.selection.DomainObjectSelectionEvent;
import org.janelia.it.workstation.gui.browser.events.selection.PipelineResultSelectionEvent;
import org.janelia.it.workstation.gui.browser.gui.inspector.DomainInspectorPanel;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;

/**
 * Top component for the Data Inspector, which shows details about a single
 * domain object. 
 * 
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
@ConvertAsProperties(
        dtd = "-//org.janelia.it.workstation.gui.browser.components//DomainInspector//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = DomainInspectorTopComponent.TC_NAME,
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "appExplorerBtm", openAtStartup = true, position = 20)
@ActionID(category = "Window", id = "org.janelia.it.workstation.gui.browser.components.DomainInspectorTopComponent")
@ActionReference(path = "Menu/Window" /*, position = 333 */)
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
        log.info("Activating domain inspector");
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
    }

    void readProperties(java.util.Properties p) {
    }
    
    // Custom methods

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
            log.info("domainObjectSelected({})", Reference.createFor(domainObject));
            detailsPanel.loadDomainObject(domainObject);
        }
    }

    @Subscribe
    public void resultSelected(PipelineResultSelectionEvent event) {

        if (event.isUserDriven()) {
            PipelineResult result = event.getPipelineResult();
            log.info("resultSelected({})", result.getId());
            detailsPanel.loadPipelineResult(result);
        }
    }

}
