package org.janelia.it.workstation.gui.browser.components;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.ActionMap;
import javax.swing.text.DefaultEditorKit;
import org.janelia.it.jacs.model.domain.DomainObject;
import org.janelia.it.jacs.model.domain.ontology.Annotation;
import org.janelia.it.workstation.gui.browser.api.DomainDAO;
import org.janelia.it.workstation.gui.browser.components.viewer.PaginatedResultsPanel;
import org.janelia.it.workstation.gui.browser.nodes.ObjectSetNode;
import org.janelia.it.workstation.gui.browser.search.ResultPage;
import org.janelia.it.workstation.gui.browser.search.SearchResults;
import org.janelia.it.workstation.gui.framework.session_mgr.SessionMgr;
import org.janelia.it.workstation.gui.util.WindowLocator;
import org.janelia.it.workstation.shared.workers.SimpleWorker;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top component which displays children of domain objects.
 */
@ConvertAsProperties(
        dtd = "-//org.janelia.it.workstation.gui.browser.components//DomainBrowser//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = DomainBrowserTopComponent.TC_NAME,
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "editor", openAtStartup = true)
@ActionID(category = "Window", id = "org.janelia.it.workstation.gui.browser.components.DomainBrowserTopComponent")
@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_DomainBrowserAction",
        preferredID = "DomainBrowserTopComponent"
)
@Messages({
    "CTL_DomainBrowserAction=Domain Browser",
    "CTL_DomainBrowserTopComponent=Domain Browser",
    "HINT_DomainBrowserTopComponent=Domain Browser"
})
public final class DomainBrowserTopComponent extends TopComponent implements LookupListener, ExplorerManager.Provider {

    public static final String TC_NAME = "DomainBrowserTopComponent";
    
    private final static Logger log = LoggerFactory.getLogger(DomainBrowserTopComponent.class);
    
    private final PaginatedResultsPanel resultsPanel;
    
    private Lookup.Result<AbstractNode> result = null;
    
    private final ExplorerManager mgr = new ExplorerManager();
    
    public DomainBrowserTopComponent() {
        initComponents();
        
        resultsPanel = new PaginatedResultsPanel() {
            @Override
            protected ResultPage getPage(SearchResults searchResults, int page) throws Exception {
                return searchResults.getPage(page);
            }
        };
        mainPanel.add(resultsPanel, BorderLayout.CENTER);
        
        setName(Bundle.CTL_DomainBrowserTopComponent());
        setToolTipText(Bundle.HINT_DomainBrowserTopComponent());
        associateLookup(ExplorerUtils.createLookup(mgr, getActionMap()));
        
        ActionMap map = this.getActionMap();
        map.put(DefaultEditorKit.copyAction, ExplorerUtils.actionCopy(mgr));
        map.put(DefaultEditorKit.cutAction, ExplorerUtils.actionCut(mgr));
        map.put(DefaultEditorKit.pasteAction, ExplorerUtils.actionPaste(mgr));
        map.put("delete", ExplorerUtils.actionDelete(mgr, true)); 
    }
    
    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();

        mainPanel.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 327, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 298, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel mainPanel;
    // End of variables declaration//GEN-END:variables

    @Override
    public void componentOpened() {
        // Typically we would use the following line to react to the currently selected top component
        //result = Utilities.actionsGlobalContext().lookupResult(AbstractNode.class);
        // But in this case we're only interested in the domain explorer
        TopComponent win = WindowLocator.getByName(DomainExplorerTopComponent.TC_NAME);
        result = win.getLookup().lookupResult(AbstractNode.class);
        result.addLookupListener(this);
    }
    
    @Override
    public void componentClosed() {
        result.removeLookupListener(this);
    }

    @Override
    protected void componentActivated() {
        ExplorerUtils.activateActions(mgr, true);
    }
    
    @Override
    protected void componentDeactivated() {
        ExplorerUtils.activateActions(mgr, false);
    }
    
    @Override
    public ExplorerManager getExplorerManager() {
        return mgr;
    }
    
    @Override
    public void resultChanged(LookupEvent lookupEvent) {
        Collection<? extends AbstractNode> allNodes = result.allInstances();
        if (!allNodes.isEmpty()) {
            final Node obj = allNodes.iterator().next();
            log.trace("Setting context object on IconGridViewer to "+obj.getDisplayName());
            
            SimpleWorker childLoadingWorker = new SimpleWorker() {

                private List<DomainObject> domainObjects;
                private List<Annotation> annotations;

                @Override
                protected void doStuff() throws Exception {
                    log.debug("Getting children...");
                    DomainDAO dao = DomainExplorerTopComponent.getDao();
                    if (obj instanceof ObjectSetNode) {
                        ObjectSetNode objectSetNode = (ObjectSetNode)obj;
                        domainObjects = dao.getDomainObjects(SessionMgr.getSubjectKey(), objectSetNode.getObjectSet());
                        List<Long> ids = new ArrayList<>();
                        for(DomainObject domainObject : domainObjects) {
                            ids.add(domainObject.getId());
                        }
                        annotations = dao.getAnnotations(SessionMgr.getSubjectKey(), ids);
                        log.debug("  Showing "+domainObjects.size()+" items");
                    }
                    else {
                        log.debug("  This is not something we can load");
                    }
                }

                @Override
                protected void hadSuccess() {
                    if (domainObjects==null || domainObjects.isEmpty()) {
                        resultsPanel.showNothing();
                        return;
                    }
                    SearchResults searchResults = SearchResults.paginate(domainObjects, annotations);
                    resultsPanel.showSearchResults(searchResults);
                }

                @Override
                protected void hadError(Throwable error) {
                    SessionMgr.getSessionMgr().handleException(error);
                }
            };

            childLoadingWorker.execute();
            
        } 
        else {
            resultsPanel.showNothing();
        }
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }
}
