package org.janelia.it.workstation.browser.gui.editor;

import static org.janelia.it.workstation.browser.api.DomainMgr.getDomainMgr;

import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.janelia.it.jacs.model.domain.DomainConstants;
import org.janelia.it.jacs.model.domain.DomainObject;
import org.janelia.it.jacs.model.domain.Preference;
import org.janelia.it.jacs.model.domain.gui.search.Filter;
import org.janelia.it.jacs.model.domain.gui.search.Filtering;
import org.janelia.it.jacs.model.domain.gui.search.criteria.AttributeCriteria;
import org.janelia.it.jacs.model.domain.gui.search.criteria.AttributeValueCriteria;
import org.janelia.it.jacs.model.domain.gui.search.criteria.Criteria;
import org.janelia.it.jacs.model.domain.gui.search.criteria.DateRangeCriteria;
import org.janelia.it.jacs.model.domain.gui.search.criteria.FacetCriteria;
import org.janelia.it.jacs.model.domain.gui.search.criteria.TreeNodeCriteria;
import org.janelia.it.jacs.model.domain.sample.Sample;
import org.janelia.it.jacs.model.domain.support.DomainObjectAttribute;
import org.janelia.it.jacs.model.domain.support.DomainUtils;
import org.janelia.it.jacs.shared.solr.FacetValue;
import org.janelia.it.jacs.shared.utils.StringUtils;
import org.janelia.it.workstation.browser.ConsoleApp;
import org.janelia.it.workstation.browser.actions.ExportResultsAction;
import org.janelia.it.workstation.browser.activity_logging.ActivityLogHelper;
import org.janelia.it.workstation.browser.api.ClientDomainUtils;
import org.janelia.it.workstation.browser.api.DomainMgr;
import org.janelia.it.workstation.browser.api.DomainModel;
import org.janelia.it.workstation.browser.components.DomainExplorerTopComponent;
import org.janelia.it.workstation.browser.events.model.DomainObjectChangeEvent;
import org.janelia.it.workstation.browser.events.model.DomainObjectInvalidationEvent;
import org.janelia.it.workstation.browser.events.model.DomainObjectRemoveEvent;
import org.janelia.it.workstation.browser.gui.dialogs.EditCriteriaDialog;
import org.janelia.it.workstation.browser.gui.listview.PaginatedResultsPanel;
import org.janelia.it.workstation.browser.gui.listview.table.DomainObjectTableViewer;
import org.janelia.it.workstation.browser.gui.support.Debouncer;
import org.janelia.it.workstation.browser.gui.support.DesktopApi;
import org.janelia.it.workstation.browser.gui.support.DropDownButton;
import org.janelia.it.workstation.browser.gui.support.Icons;
import org.janelia.it.workstation.browser.gui.support.MouseForwarder;
import org.janelia.it.workstation.browser.gui.support.SearchProvider;
import org.janelia.it.workstation.browser.gui.support.SmartSearchBox;
import org.janelia.it.workstation.browser.gui.support.WindowLocator;
import org.janelia.it.workstation.browser.model.search.ResultPage;
import org.janelia.it.workstation.browser.model.search.SearchConfiguration;
import org.janelia.it.workstation.browser.model.search.SearchResults;
import org.janelia.it.workstation.browser.nodes.AbstractDomainObjectNode;
import org.janelia.it.workstation.browser.nodes.FilterNode;
import org.janelia.it.workstation.browser.util.ConcurrentUtils;
import org.janelia.it.workstation.browser.workers.IndeterminateProgressMonitor;
import org.janelia.it.workstation.browser.workers.SimpleWorker;
import org.perf4j.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;

/**
 * The Filter Editor is the main search GUI in the Workstation. Users can create, save, and load filters 
 * into this panel. The filter is executed every time it changes, and shows results in an embedded 
 * PaginatedResultsPanel. 
 * 
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class FilterEditorPanel extends DomainObjectEditorPanel<Filtering> implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(FilterEditorPanel.class);

    public static final int EXPORT_PAGE_SIZE = 1000;
    
    // UI Settings
    public static final String DEFAULT_FILTER_NAME = "Unsaved Filter";
    public static final Class<?> DEFAULT_SEARCH_CLASS = Sample.class;
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd");
    private static final int MAX_VALUES_STRING_LENGTH = 20;
    
    // Utilities
    private final Debouncer debouncer = new Debouncer();
    
    // UI Elements
    private final ConfigPanel configPanel;
    private final JButton saveButton;
    private final JButton saveAsButton;
    private final PaginatedResultsPanel resultsPanel;
    private final DropDownButton typeCriteriaButton;
    private final DropDownButton addCriteriaButton;
    private final SmartSearchBox searchBox;
    private final JButton infoButton;

    // State
    private FilterNode filterNode;
    private Filter filter;    
    private boolean dirty = false;
    private SearchConfiguration searchConfig;
    
    // Results
    private SearchResults searchResults;

    public FilterEditorPanel() {

        this.saveButton = new JButton("Save");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (filter==null) throw new IllegalStateException("Cannot save null filter");
                SimpleWorker worker = new SimpleWorker() {
                        
                    private Filter savedFilter;
                    
                    @Override
                    protected void doStuff() throws Exception {
                        Filter filterToSave = filter;
                        if (filterToSave.getId()!=null) {
                            // What we have is a clone of a filter, so we need to get the canonical instance
                            filterToSave = DomainMgr.getDomainMgr().getModel().getDomainObject(filter);
                            filterToSave.setCriteriaList(filter.getCriteriaList());
                            filterToSave.setSearchClass(filter.getSearchClass());
                            filterToSave.setSearchString(filter.getSearchString());
                            log.info("Saving filter '{}' with id {}",filterToSave.getName(),filterToSave.getId());
                        }
                        else {
                            log.info("Creating filter '{}'",filterToSave.getName());
                        }

                        savedFilter = DomainMgr.getDomainMgr().getModel().save(filterToSave);
                    }

                    @Override
                    protected void hadSuccess() {
                        setFilter(savedFilter);
                        savePreferences();
                        saveButton.setVisible(false);
                    }

                    @Override
                    protected void hadError(Throwable error) {
                        ConsoleApp.handleException(error);
                    }
                };
                worker.execute();
                
            }
        });
        
        this.saveAsButton = new JButton("Save As");
        saveAsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                String name = filter.getName().equals(DEFAULT_FILTER_NAME)?null:filter.getName();
                String newName = null;
                while (StringUtils.isEmpty(newName)) {
                    newName = (String) JOptionPane.showInputDialog(ConsoleApp.getMainFrame(),
                            "Filter Name:\n", "Save Filter", JOptionPane.PLAIN_MESSAGE, null, null, name);
                    log.info("newName:" + newName);
                    if (newName == null) {
                        // User chose "Cancel"
                        return;
                    }
                    if (StringUtils.isBlank(newName)) {
                        JOptionPane.showMessageDialog(ConsoleApp.getMainFrame(), "Filter name cannot be blank");
                        return;
                    }
                }

                final String finalName = newName;
                final boolean isNewFilter = filter.getId()==null;

                SimpleWorker worker = new SimpleWorker() {
                        
                    @Override
                    protected void doStuff() throws Exception {
                        Filter savedFilter = filter;
                        if (!isNewFilter) {
                            // This filter is already saved, duplicate it so that we don't overwrite the existing one
                            savedFilter = DomainUtils.cloneFilter(filter);
                        }
                        savedFilter.setName(finalName);
                        DomainModel model = getDomainMgr().getModel();
                        savedFilter = model.save(savedFilter);
                        model.addChild(model.getDefaultWorkspace(), savedFilter);
                        setFilter(savedFilter);
                        savePreferences();
                    }

                    @Override
                    protected void hadSuccess() {
                        // Wait for events to resolve
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                // Select the filter and force it to reload
                                DomainExplorerTopComponent.getInstance().selectAndNavigateNodeById(filter.getId());
                            }
                        });
                    }

                    @Override
                    protected void hadError(Throwable error) {
                        ConsoleApp.handleException(error);
                    }
                };

                worker.execute();
                
            }
        });
        
        this.typeCriteriaButton = new DropDownButton();
        this.addCriteriaButton = new DropDownButton("Add Criteria...");
        this.searchBox = new SmartSearchBox("SEARCH_HISTORY");

        infoButton = new JButton(Icons.getIcon("info.png"));
        infoButton.setMargin(new Insets(0,2,0,2));
        infoButton.setBorderPainted(false);
        infoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // TODO: make a custom help page later
                try {
                    if (!DesktopApi.browseDesktop(new URI("http://lucene.apache.org/core/old_versioned_docs/versions/3_5_0/queryparsersyntax.html"))) {
                        JOptionPane.showMessageDialog(
                                WindowLocator.getMainFrame(),
                                "Cannot open URL. Desktop API is not supported on this platform.",
                                "Error",
                                JOptionPane.ERROR_MESSAGE,
                                null
                        );
                    }
                }
                catch (Exception ex) {
                    ConsoleApp.handleException(ex);
                }
            }
        });

        AbstractAction mySearchAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchBox.addCurrentSearchTermToHistory();
                refreshSearchResults(true);
            }
        };
        
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0,true), "enterAction");
        getActionMap().put("enterAction", mySearchAction);
        
        this.resultsPanel = new PaginatedResultsPanel(getSelectionModel(), this) {
            @Override
            protected ResultPage getPage(SearchResults searchResults, int page) throws Exception {
                return searchResults.getPage(page);
            }
        };
        resultsPanel.addMouseListener(new MouseForwarder(this, "PaginatedResultsPanel->FilterEditorPanel"));

        configPanel = new ConfigPanel(true);
        
        setLayout(new BorderLayout());
        add(configPanel, BorderLayout.NORTH);
        add(resultsPanel, BorderLayout.CENTER);
    }

    private void setFilter(Filtering canonicalFilter) {
        // Clone the filter so that we don't modify the one in the cache
        this.filter = DomainUtils.cloneFilter(canonicalFilter);
        if (Filter.class.equals(canonicalFilter.getClass())) {
            // We can only override the given object with a cloned filter (e.g. use the "Save" action) if it's a Filter to begin with
            filter.setId(canonicalFilter.getId());
        }
        this.searchConfig = new SearchConfiguration(filter, SearchResults.PAGE_SIZE);
    }
    
    public void loadNewFilter() {
        Filter newFilter = new Filter();
        newFilter.setSearchClass(Sample.class.getSimpleName());
        FacetCriteria facet = new FacetCriteria();
        facet.setAttributeName("sageSynced");
        facet.setValues(Sets.newHashSet("true"));
        newFilter.addCriteria(facet);
        loadDomainObject(newFilter, true, null);
    }

    @Override
    public void loadDomainObjectNode(AbstractDomainObjectNode<Filtering> filterNode, boolean isUserDriven, Callable<Void> success) {
        this.filterNode = (FilterNode)filterNode;
        loadDomainObject(filterNode.getDomainObject(), isUserDriven, success);
    }

    public void loadDomainObject(Filtering filter, final boolean isUserDriven, final Callable<Void> success) {

        if (filter==null) return;
        
        if (!debouncer.queue(success)) {
            log.info("Skipping load, since there is one already in progress");
            return;
        }
        
        if (filter.getName()==null) {
            filter.setName(DEFAULT_FILTER_NAME);
        }
        
        log.debug("loadDomainObject(Filter:{})",filter.getName());
        final StopWatch w = new StopWatch();

        getSelectionModel().setParentObject(filter);
        this.dirty = false;
        setFilter(filter);
        loadPreferences();

        try {
            updateView();
            
            configPanel.removeAllTitleComponents();
            if (ClientDomainUtils.hasWriteAccess(filter) || filter.getName().equals(DEFAULT_FILTER_NAME)) {
	            configPanel.addTitleComponent(saveButton, false, true);
	            configPanel.addTitleComponent(saveAsButton, false, true);
            }
            configPanel.setExpanded(filter.getId()==null);

            search();
        }
        catch (Exception e) {
            ConsoleApp.handleException(e);
        }

        ActivityLogHelper.logElapsed("FilterEditorPanel.loadDomainObject", filter, w);
    }
    
    private void refreshSearchResults(boolean isUserDriven) {
        debouncer.queue();
        refreshSearchResults(isUserDriven, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                debouncer.success();
                return null;
            }
        },new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                debouncer.failure();
                return null;
            }
        });
    }
    
    private void refreshSearchResults(final boolean isUserDriven, final Callable<Void> success, final Callable<Void> failure) {
        log.trace("refresh");
        
        String inputFieldValue = searchBox.getSearchString();
        if (!StringUtils.areEqual(filter.getSearchString(), inputFieldValue)) {
            dirty = true;
        }
        
        filter.setSearchString(inputFieldValue);

        saveButton.setVisible(dirty && filter.getId()!=null && !filter.getName().equals(DEFAULT_FILTER_NAME));
        
        performSearch(isUserDriven, success, failure);
    }
    
    public synchronized void performSearch(final boolean isUserDriven, final Callable<Void> success, final Callable<Void> failure) {

        if (searchConfig==null || searchConfig.getSearchClass()==null) return;
        
        log.debug("Performing search with isUserDriven={}",isUserDriven);
        final StopWatch w = new StopWatch();
        resultsPanel.showLoadingIndicator();
        
        SimpleWorker worker = new SimpleWorker() {

            @Override
            protected void doStuff() throws Exception {
                searchResults = searchConfig.performSearch();
            }

            @Override
            protected void hadSuccess() {
                try {
                    resultsPanel.showSearchResults(searchResults, isUserDriven, new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            updateView();
                            ConcurrentUtils.invokeAndHandleExceptions(success);
                            ActivityLogHelper.logElapsed("FilterEditorPanel.performSearch", w);
                            return null;
                        }
                        
                    });
                }
                catch (Exception e) {
                    hadError(e);
                }
            }

            @Override
            protected void hadError(Throwable error) {
                showNothing();
                ConcurrentUtils.invokeAndHandleExceptions(failure);
                ConsoleApp.handleException(error);
            }
        };

        worker.execute();
    }

    public void showNothing() {
        resultsPanel.showNothing();
    }
    
    private void updateView() {

        searchBox.setSearchString(filter.getSearchString());

        final String currType = DomainUtils.getTypeName(searchConfig.getSearchClass());
        typeCriteriaButton.setText("Result Type: " + currType);

        typeCriteriaButton.getPopupMenu().removeAll();
        ButtonGroup typeGroup = new ButtonGroup();
        for (final Class<? extends DomainObject> searchClass : DomainUtils.getSearchClasses()) {
            final String type = DomainUtils.getTypeName(searchClass);
            JMenuItem menuItem = new JRadioButtonMenuItem(type, searchClass.equals(searchConfig.getSearchClass()));
            menuItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    dirty = true;
                    searchConfig.setSearchClass(searchClass);
                    updateView();
                    refreshSearchResults(true);
                }
            });
            typeGroup.add(menuItem);
            typeCriteriaButton.getPopupMenu().add(menuItem);
        }

        configPanel.setTitle(filter.getName());
        
        // Update filters
        configPanel.removeAllConfigComponents();
        configPanel.addConfigComponent(typeCriteriaButton);
        configPanel.addConfigComponent(searchBox);
        configPanel.addConfigComponent(infoButton);

        for (Criteria criteria : filter.getCriteriaList()) {
            if (criteria instanceof TreeNodeCriteria) {
                DropDownButton customCriteriaButton = createCustomCriteriaButton((TreeNodeCriteria)criteria);
                if (customCriteriaButton!=null) {
                    configPanel.addConfigComponent(customCriteriaButton);
                }
            }
        }

        for(DomainObjectAttribute attr : searchConfig.getDomainObjectAttributes()) {
            if (attr.getFacetKey()!=null) {
                log.trace("Adding facet: {}",attr.getLabel());
                StringBuilder label = new StringBuilder();
                label.append(attr.getLabel());
                List<String> values = new ArrayList<>(getSelectedFacetValues(attr.getName()));
                Collections.sort(values);
                if (!values.isEmpty()) {
                    label.append(" (");
                    label.append(StringUtils.getCommaDelimited(values, MAX_VALUES_STRING_LENGTH));
                    label.append(")");
                }
                DropDownButton facetButton = new DropDownButton(label.toString());
                populateFacetMenu(attr, facetButton.getPopupMenu());
                configPanel.addConfigComponent(facetButton);
            }
        }

        for (Criteria criteria : filter.getCriteriaList()) {
            if (criteria instanceof AttributeCriteria) {
                DropDownButton customCriteriaButton = createCustomCriteriaButton((AttributeCriteria)criteria);
                if (customCriteriaButton!=null) {
                    configPanel.addConfigComponent(customCriteriaButton);
                }
            }
        }

        JPopupMenu addCriteriaPopupMenu = addCriteriaButton.getPopupMenu();
        addCriteriaPopupMenu.removeAll();

        for (final DomainObjectAttribute attr : searchConfig.getDomainObjectAttributes()) {

            boolean found = false;
            if (filter.hasCriteria()) {
                for (Criteria criteria : filter.getCriteriaList()) {
                    if (criteria instanceof AttributeCriteria) {
                        AttributeCriteria ac = (AttributeCriteria)criteria;
                        if (ac.getAttributeName().equals(attr.getName())) {
                            found = true;
                        }
                    }
                }
            }
            
            if (found) continue;
            
            JMenuItem menuItem = new JMenuItem(attr.getLabel());
            menuItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    
                    AttributeCriteria criteria;
                    Class<?> attrClass = attr.getGetter().getReturnType();
                    if (attrClass.equals(Date.class)) {
                        criteria = new DateRangeCriteria();
                    }
                    else {
                        criteria = new AttributeValueCriteria();
                    }
                    
                    criteria.setAttributeName(attr.getName());
                    EditCriteriaDialog dialog = new EditCriteriaDialog();
                    criteria = (AttributeCriteria)dialog.showForCriteria(criteria, attr.getLabel());
                    
                    if (criteria!=null) {
                        filter.addCriteria(criteria);
                        dirty = true;
                        refreshSearchResults(true);
                    }
                }
            });
            addCriteriaPopupMenu.add(menuItem);
        }
        
        configPanel.addConfigComponent(addCriteriaButton);
        configPanel.updateUI();
    }

    private DropDownButton createCustomCriteriaButton(final TreeNodeCriteria criteria) {

        DropDownButton facetButton = new DropDownButton("In: "+criteria.getTreeNodeName());

        JPopupMenu popupMenu = facetButton.getPopupMenu();
        popupMenu.removeAll();

        final JMenuItem removeMenuItem = new JMenuItem("Remove");
        removeMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                removeTreeNodeCriteria(criteria.getTreeNodeName());
                dirty = true;
                refreshSearchResults(true);
            }
        });
        popupMenu.add(removeMenuItem);

        return facetButton;
    }

    private DropDownButton createCustomCriteriaButton(final AttributeCriteria criteria) {
        
        String label;
        final DomainObjectAttribute attr = searchConfig.getDomainObjectAttribute(criteria.getAttributeName());
        
        if (criteria instanceof AttributeValueCriteria) {
            AttributeValueCriteria avc = (AttributeValueCriteria)criteria;
            label = attr.getLabel()+": "+avc.getValue();
        }
        else if (criteria instanceof DateRangeCriteria) {
            DateRangeCriteria drc = (DateRangeCriteria)criteria;
            label = attr.getLabel()+": "+DATE_FORMAT.format(drc.getStartDate())+" - "+DATE_FORMAT.format(drc.getEndDate());
        }
        else {
            return null;
        }
        
        DropDownButton facetButton = new DropDownButton(label);

        JPopupMenu popupMenu = facetButton.getPopupMenu();
        popupMenu.removeAll();
        
        final JMenuItem editMenuItem = new JMenuItem("Edit");
        editMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                EditCriteriaDialog dialog = new EditCriteriaDialog();
                if (dialog.showForCriteria(criteria, attr.getLabel())!=null) {
                    dirty = true;
                    refreshSearchResults(true);
                }
            }
        });
        popupMenu.add(editMenuItem);
        
        final JMenuItem removeMenuItem = new JMenuItem("Remove");
        removeMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                removeAttributeCriteria(criteria.getAttributeName());
                dirty = true;
                refreshSearchResults(true);
            }
        });
        popupMenu.add(removeMenuItem);
        
        return facetButton;
    }
    
    private void populateFacetMenu(final DomainObjectAttribute attr, JPopupMenu popupMenu) {

        Set<String> selectedValues = getSelectedFacetValues(attr.getName());

        if (!selectedValues.isEmpty()) {
            final JMenuItem menuItem = new JMenuItem("Clear selected");
            menuItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    updateFacet(attr.getName(), null, false);
                    dirty = true;
                    refreshSearchResults(true);
                }
            });
            popupMenu.add(menuItem);
        }
        
        Collection<FacetValue> attrFacetValues = searchConfig.getFacetValues(attr.getFacetKey());
                
        if (attrFacetValues!=null) {
            for (final FacetValue facetValue : attrFacetValues) {
                boolean selected = selectedValues.contains(facetValue.getValue());
                if (facetValue.getCount()==0 && !selected) {
                    // Skip anything that is not selected, and which doesn't have results. Clicking it would be futile.
                    continue;
                }
                String label = facetValue.getValue()+" ("+facetValue.getCount()+" items)";
                final JMenuItem menuItem = new JCheckBoxMenuItem(label, selected);
                menuItem.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (menuItem.isSelected()) {
                            updateFacet(attr.getName(), facetValue.getValue(), true);
                        }
                        else {
                            updateFacet(attr.getName(), facetValue.getValue(), false);
                        }
                        dirty = true;
                        refreshSearchResults(true);
                    }
                });
                popupMenu.add(menuItem);
            }
        }
    }

    private void removeTreeNodeCriteria(String treeNodeName) {
        if (filter.hasCriteria()) {
            for (Iterator<Criteria> i = filter.getCriteriaList().iterator(); i.hasNext(); ) {
                Criteria criteria = i.next();
                if (criteria instanceof TreeNodeCriteria) {
                    TreeNodeCriteria tnc = (TreeNodeCriteria) criteria;
                    if (tnc.getTreeNodeName().equals(treeNodeName)) {
                        // Remove facet entirely
                        i.remove();
                    }
                }
            }
        }
    }

    private void removeAttributeCriteria(String attrName) {
        if (filter.hasCriteria()) {
            for (Iterator<Criteria> i = filter.getCriteriaList().iterator(); i.hasNext(); ) {
                Criteria criteria = i.next();
                if (criteria instanceof AttributeCriteria) {
                    AttributeCriteria ac = (AttributeCriteria) criteria;
                    if (ac.getAttributeName().equals(attrName)) {
                        // Remove facet entirely
                        i.remove();
                    }
                }
            }
        }
    }
    
    private void updateFacet(String attrName, String value, boolean addValue) {
        boolean modified = false;
        // Attempt to modify an existing facet criteria
        if (filter.hasCriteria()) {
            for (Iterator<Criteria> i = filter.getCriteriaList().iterator(); i.hasNext(); ) {
                Criteria criteria = i.next();
                if (criteria instanceof FacetCriteria) {
                    FacetCriteria fc = (FacetCriteria) criteria;
                    if (fc.getAttributeName().equals(attrName)) {
                        if (value==null) {
                            // Remove facet entirely
                            i.remove();
                        }
                        else {
                            modified = true;
                            if (addValue) {
                                fc.getValues().add(value);
                            }
                            else {
                                fc.getValues().remove(value);
                                if (fc.getValues().isEmpty()) {
                                    i.remove();
                                }
                            }
                        }
                    }
                }
            }
        }
        // Facet criteria was not found, create a new one
        if (!modified && addValue) {
            FacetCriteria fc = new FacetCriteria(); 
            fc.setAttributeName(attrName);
            fc.getValues().add(value);
            filter.addCriteria(fc);
        }
    }
    
    private Set<String> getSelectedFacetValues(String attrName) {
        if (filter==null || !filter.hasCriteria()) return new HashSet<>();
        for (Criteria criteria : filter.getCriteriaList()) {
            if (criteria instanceof FacetCriteria) {
                FacetCriteria fc = (FacetCriteria) criteria;
                if (fc.getAttributeName().equals(attrName)) {
                    return fc.getValues();
                }
            }
        }
        return new HashSet<>();
    }

    @Subscribe
    public void domainObjectInvalidated(DomainObjectInvalidationEvent event) {
        try {
            if (filter==null) return;
            if (event.isTotalInvalidation()) {
                log.info("Total invalidation, reloading...");
                reload();
            }
            else {
                for (DomainObject domainObject : event.getDomainObjects()) {
                    if (domainObject.getId().equals(filter.getId())) {
                        log.info("Filter invalidated, reloading...");
                        reload();
                        break;
                    }
                }
            }
        } 
        catch (Exception e) {
            ConsoleApp.handleException(e);
        }
    }

    private void reload() throws Exception {
        if (filter==null || filter.getId()==null) return;
        Filter updatedFilter = getDomainMgr().getModel().getDomainObject(filter.getClass(), filter.getId());
        this.filter = updatedFilter;
        if (updatedFilter!=null && filterNode!=null && !filterNode.getFilter().equals(updatedFilter)) {
            filterNode.update(updatedFilter);
            restoreState(saveState());
        }
        else {
            this.filterNode = null;
            loadNewFilter();
        }
    }

    @Override
    public String getName() {
        if (filter==null) {
            return "Filter Editor";
        }
        return "Filter: "+StringUtils.abbreviate(filter.getName(), 15);
    }

    @Override
    public PaginatedResultsPanel getResultsPanel() {
        return resultsPanel;
    }

    @Override
    protected Filtering getDomainObject() {
        return filter;
    }

    @Override
    protected AbstractDomainObjectNode<Filtering> getDomainObjectNode() {
        return filterNode;
    }
    
    @Override
    public void activate() {
        resultsPanel.activate();
    }

    @Override
    public void deactivate() {
        resultsPanel.deactivate();
    }

    @Subscribe
    public void domainObjectRemoved(DomainObjectRemoveEvent event) {
        if (filter==null) return;
        if (event.getDomainObject().getId().equals(filter.getId())) {
            loadNewFilter();
        }
    }

    @Subscribe
    public void domainObjectChanged(DomainObjectChangeEvent event) {
        if (searchResults.updateIfFound(event.getDomainObject())) {
            log.info("Updated search results with changed domain object: {}", event.getDomainObject());
        }
    }
    
    @Override
    public String getSortField() {
        return searchConfig.getSortCriteria();
    }

    @Override
    public void setSortField(String sortCriteria) {
        searchConfig.setSortCriteria(sortCriteria);
        savePreferences();
    }

    @Override
    public void search() {
        refreshSearchResults(true);
    }

    private void loadPreferences() {
        if (filter.getId()==null) return;
        try {
            Preference sortCriteriaPref = DomainMgr.getDomainMgr().getPreference(
                    DomainConstants.PREFERENCE_CATEGORY_SORT_CRITERIA, filter.getId().toString());
            if (sortCriteriaPref!=null) {
                log.debug("Loaded sort criteria preference: {}",sortCriteriaPref.getValue());
                searchConfig.setSortCriteria((String) sortCriteriaPref.getValue());
            }
            else {
                searchConfig.setSortCriteria(null);
            }
        }
        catch (Exception e) {
            log.error("Could not load sort criteria",e);
        }
    }

    private void savePreferences() {
        if (filter.getId()==null || StringUtils.isEmpty(searchConfig.getSortCriteria())) return;
        try {
            DomainMgr.getDomainMgr().setPreference(
                    DomainConstants.PREFERENCE_CATEGORY_SORT_CRITERIA, filter.getId().toString(), searchConfig.getSortCriteria());
            log.debug("Saved sort criteria preference: {}",searchConfig.getSortCriteria());
        }
        catch (Exception e) {
            log.error("Could not save sort criteria",e);
        }
    }

    @Override
    public void export() {

        SimpleWorker worker = new SimpleWorker() {

            private SearchResults exportSearchResults = searchResults;

            @Override
            protected void doStuff() throws Exception {
                if (!searchResults.isAllLoaded()) {
                    // If anything is unloaded, we create a new search that uses a larger page size, in order to batch the export faster.
                    SearchConfiguration exportSearchConfig = new SearchConfiguration(filter, EXPORT_PAGE_SIZE);
                    exportSearchResults = exportSearchConfig.performSearch();
                }
            }

            @Override
            protected void hadSuccess() {
                DomainObjectTableViewer viewer = null;
                if (resultsPanel.getViewer() instanceof DomainObjectTableViewer) {
                    viewer = (DomainObjectTableViewer)resultsPanel.getViewer();
                }
                ExportResultsAction<DomainObject> action = new ExportResultsAction<>(exportSearchResults, viewer);
                action.actionPerformed(null);
            }

            @Override
            protected void hadError(Throwable error) {
                ConsoleApp.handleException(error);
            }
        };

        worker.setProgressMonitor(new IndeterminateProgressMonitor(ConsoleApp.getMainFrame(), "Loading...", ""));
        worker.execute();
    }
}
