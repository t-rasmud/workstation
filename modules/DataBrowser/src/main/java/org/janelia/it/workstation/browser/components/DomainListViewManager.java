package org.janelia.it.workstation.browser.components;

import com.google.common.eventbus.Subscribe;
import org.janelia.it.workstation.browser.actions.DomainObjectContextMenu;
import org.janelia.it.workstation.browser.events.Events;
import org.janelia.it.workstation.browser.events.selection.DomainObjectSelectionEvent;
import org.janelia.it.workstation.browser.gui.util.UIUtils;
import org.janelia.it.workstation.browser.nodes.AbstractDomainObjectNode;
import org.janelia.model.domain.DomainObject;
import org.janelia.model.domain.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * Manages the life cycle of domain list viewers based on user generated selected events. This manager
 * either reuses existing viewers, or creates them as needed and docks them in the appropriate place.
 * 
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class DomainListViewManager implements ViewerManager<DomainListViewTopComponent> {

    private final static Logger log = LoggerFactory.getLogger(DomainListViewManager.class);
    
    public static DomainListViewManager instance;
    
    private DomainListViewManager() {
    }
    
    public static DomainListViewManager getInstance() {
        if (instance==null) {
            instance = new DomainListViewManager();
            Events.getInstance().registerOnEventBus(instance);
        }
        return instance;
    }

    /* Manage the active instance of this top component */
    
    private DomainListViewTopComponent activeInstance;
    @Override
    public void activate(DomainListViewTopComponent instance) {
        activeInstance = instance;
    }
    @Override
    public boolean isActive(DomainListViewTopComponent instance) {
        return activeInstance == instance;
    }
    @Override
    public DomainListViewTopComponent getActiveViewer() {
        return activeInstance;
    }
    
    @Override
    public String getViewerName() {
        return "DomainListViewTopComponent";
    }

    @Override
    public Class<DomainListViewTopComponent> getViewerClass() {
        return DomainListViewTopComponent.class;
    }

    @Subscribe
    public void domainObjectSelected(DomainObjectSelectionEvent event) {

        if (!DomainExplorerTopComponent.isNavigateOnClick()) {
            return;
        }
        
        // We only care about single selections
        DomainObject domainObject = event.getObjectIfSingle();
        if (domainObject==null) {
            return;
        }
        
        // We only care about selection events
        if (!event.isSelect()) {
            log.debug("Event is not selection: {}",event);
            return;
        }

        // We only care about events generated by the explorer or the context menu
        if (event.getSource() != null && 
                (UIUtils.hasAncestorWithType((Component)event.getSource(),DomainExplorerTopComponent.class) ||
                DomainObjectContextMenu.class.isAssignableFrom(event.getSource().getClass()))) {

            log.info("domainObjectSelected({})",Reference.createFor(domainObject));
            DomainListViewTopComponent targetViewer = ViewerUtils.provisionViewer(DomainListViewManager.getInstance(), "editor");
            AbstractDomainObjectNode<?> node = event.getDomainObjectNode();

            if (node==null) {
                log.info("Loading domain object {} into {}",Reference.createFor(domainObject), targetViewer);
                targetViewer.loadDomainObject(domainObject, false);
            }
            else {
                log.info("Loading domain object node {} into {}",Reference.createFor(domainObject), targetViewer);
                targetViewer.loadDomainObjectNode(node, false);
            }
            
            // This isn't done in provisionViewer, because when a component becomes active it selects its currently loaded 
            // object in the Domain Explorer. We need to wait until the current object is updated above. 
            // Only now is it safe to activate the component.
            targetViewer.requestActive();
        }
        else {
            log.trace("Event source is not domain explorer or context menu: {}",event);
            return;
        }
    }
}
