package org.janelia.it.workstation.gui.browser.gui.listview.icongrid;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

import org.janelia.it.jacs.model.domain.DomainObject;
import org.janelia.it.jacs.model.domain.Preference;
import org.janelia.it.jacs.model.domain.Reference;
import org.janelia.it.jacs.model.domain.enums.FileType;
import org.janelia.it.jacs.model.domain.interfaces.HasFileGroups;
import org.janelia.it.jacs.model.domain.interfaces.HasFiles;
import org.janelia.it.jacs.model.domain.ontology.Annotation;
import org.janelia.it.jacs.model.domain.sample.ObjectiveSample;
import org.janelia.it.jacs.model.domain.sample.PipelineResult;
import org.janelia.it.jacs.model.domain.sample.Sample;
import org.janelia.it.jacs.model.domain.sample.SamplePipelineRun;
import org.janelia.it.jacs.model.domain.support.DomainUtils;
import org.janelia.it.workstation.gui.browser.actions.DomainObjectContextMenu;
import org.janelia.it.workstation.gui.browser.api.DomainMgr;
import org.janelia.it.workstation.gui.browser.events.selection.DomainObjectSelectionModel;
import org.janelia.it.workstation.gui.browser.gui.listview.AnnotatedDomainObjectListViewer;
import org.janelia.it.workstation.gui.browser.gui.support.SearchProvider;
import org.janelia.it.workstation.gui.browser.model.AnnotatedDomainObjectList;
import org.janelia.it.workstation.gui.browser.model.DomainConstants;
import org.janelia.it.workstation.gui.framework.session_mgr.SessionMgr;
import org.janelia.it.workstation.shared.workers.SimpleWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;

/**
 * An IconGridViewer implementation for viewing domain objects. 
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class DomainObjectIconGridViewer extends IconGridViewerPanel<DomainObject,Reference> implements AnnotatedDomainObjectListViewer {
    
    private static final Logger log = LoggerFactory.getLogger(DomainObjectIconGridViewer.class);
    
    private AnnotatedDomainObjectList domainObjectList;
    private DomainObjectSelectionModel selectionModel;
    private SearchProvider searchProvider; // Implement UI for sorting using the search provider
    
    private DefaultResult defaultResult = new DefaultResult(DomainConstants.PREFERENCE_VALUE_LATEST);
    private String defaultImageType = FileType.SignalMip.name();
    
    
    private final ImageModel<DomainObject,Reference> imageModel = new ImageModel<DomainObject, Reference>() {
        
        @Override
        public Reference getImageUniqueId(DomainObject domainObject) {
            return Reference.createFor(domainObject);
        }

        @Override
        public String getImageFilepath(DomainObject domainObject) {
            if (domainObject instanceof Sample) {
                Sample sample = (Sample)domainObject;
                List<String> objectives = sample.getOrderedObjectives();
                if (objectives==null || objectives.isEmpty()) return null;
                
                HasFiles chosenResult = null;
                if (DomainConstants.PREFERENCE_VALUE_LATEST.equals(defaultResult.getResultKey())) {
                    ObjectiveSample objSample = sample.getObjectiveSample(objectives.get(objectives.size()-1));
                    if (objSample==null) return null;
                    SamplePipelineRun run = objSample.getLatestRun();
                    if (run==null) return null;
                    chosenResult = run.getLatestResult();

                    if (chosenResult instanceof HasFileGroups) {
                        HasFileGroups hasGroups = (HasFileGroups)chosenResult;
                        // Pick the first group, since there is no way to tell which is latest
                        for(String groupKey : hasGroups.getGroupKeys()) {
                            chosenResult = hasGroups.getGroup(groupKey);
                            break;
                        }
                    }
                }
                else {
                    for(String objective : objectives) {
                        if (!objective.equals(defaultResult.getObjective())) continue;
                        ObjectiveSample objSample = sample.getObjectiveSample(objective);
                        if (objSample==null) continue;
                        SamplePipelineRun run = objSample.getLatestRun();
                        if (run==null || run.getResults()==null) continue;
                        
                        for(PipelineResult result : run.getResults()) {
                            if (result instanceof HasFileGroups) {
                                HasFileGroups hasGroups = (HasFileGroups)result;
                                for(String groupKey : hasGroups.getGroupKeys()) {
                                    if (result.getName().equals(defaultResult.getResultNamePrefix()) && groupKey.equals(defaultResult.getGroupName())) {
                                        chosenResult = hasGroups.getGroup(groupKey);
                                        break;
                                    }
                                }
                            }
                            else {
                                if (result.getName().equals(defaultResult.getResultName())) {
                                    chosenResult = result;
                                    break;
                                }
                            }   
                        }
                    }
                }
                
                return chosenResult==null? null : DomainUtils.getFilepath(chosenResult, defaultImageType);
            }
            else if (domainObject instanceof HasFiles) {
                HasFiles hasFiles = (HasFiles)domainObject;
                return DomainUtils.getFilepath(hasFiles, defaultImageType);
            }
            return null;
        }
        
        @Override
        public DomainObject getImageByUniqueId(Reference id) {
            return DomainMgr.getDomainMgr().getModel().getDomainObject(id);
        }
        
        @Override
        public Object getImageLabel(DomainObject domainObject) {
            return domainObject.getName();
        }
        
        @Override
        public List<Annotation> getAnnotations(DomainObject domainObject) {
            return domainObjectList.getAnnotations(domainObject.getId());
        }
    };

    public DomainObjectIconGridViewer() {
        setImageModel(imageModel);
    }

    @Override
    public void setSearchProvider(SearchProvider searchProvider) {
        this.searchProvider = searchProvider;
    }
    
    @Override
    public void setSelectionModel(DomainObjectSelectionModel selectionModel) {
        super.setSelectionModel(selectionModel);
        this.selectionModel = selectionModel;
    }
    
    @Override
    public DomainObjectSelectionModel getSelectionModel() {
        return selectionModel;
    }
    
    @Override
    protected JPopupMenu getButtonPopupMenu() {
        List<Reference> selectionIds = selectionModel.getSelectedIds();
        List<DomainObject> domainObjects = new ArrayList<>();
        for (Reference id : selectionIds) {
            DomainObject imageObject = getImageModel().getImageByUniqueId(id);
            if (imageObject == null) {
                log.warn("Could not locate selected entity with id {}", id);
            }
            else {
                domainObjects.add(imageObject);
            }
        }
        JPopupMenu popupMenu = new DomainObjectContextMenu(domainObjects);
        ((DomainObjectContextMenu) popupMenu).addMenuItems();
        return popupMenu;
    }
    
    @Override
    public void refreshDomainObject(DomainObject domainObject) {
        refreshImageObject(domainObject);
    }
    
    @Override
    protected void buttonDrillDown(DomainObject domainObject) {
    }
    
    
    @Override
    public void showDomainObjects(AnnotatedDomainObjectList objects, final Callable<Void> success) {

        this.domainObjectList = objects;
        log.debug("showDomainObjects(domainObjectList.size={})",domainObjectList.getDomainObjects().size());
        
        final DomainObject parentObject = (DomainObject)selectionModel.getParentObject();
        if (parentObject!=null && parentObject.getId()!=null) {
            Preference preference = DomainMgr.getDomainMgr().getPreference(DomainConstants.PREFERENCE_CATEGORY_DEFAULT_SAMPLE_RESULT, parentObject.getId().toString());
            if (preference!=null) {
                this.defaultResult = new DefaultResult(preference.getValue());
            }
            Preference preference2 = DomainMgr.getDomainMgr().getPreference(DomainConstants.PREFERENCE_CATEGORY_DEFAULT_IMAGE_TYPE, parentObject.getId().toString());
            if (preference2!=null) {
                this.defaultImageType = preference2.getValue();
            }
        }
        
        Multiset<String> countedTypeNames = LinkedHashMultiset.create();
        Multiset<String> countedResultNames = LinkedHashMultiset.create();
        // Add twice so that it is selected by >1 filter below
        countedResultNames.add(DomainConstants.PREFERENCE_VALUE_LATEST);
        countedResultNames.add(DomainConstants.PREFERENCE_VALUE_LATEST);
        
        for(DomainObject domainObject : domainObjectList.getDomainObjects()) {
            if (domainObject instanceof Sample) {
                Sample sample = (Sample)domainObject;
                for(String objective : sample.getOrderedObjectives()) {
                    ObjectiveSample objectiveSample = sample.getObjectiveSample(objective);
                    SamplePipelineRun run = objectiveSample.getLatestRun();
                    if (run==null || run.getResults()==null) continue;
                    for(PipelineResult result : run.getResults()) {
                        if (result instanceof HasFileGroups) {
                            HasFileGroups hasGroups = (HasFileGroups)result;
                            for(String groupKey : hasGroups.getGroupKeys()) {
                                String name = objective+" "+result.getName()+" ("+groupKey+")";
                                countedResultNames.add(name);
                                HasFiles hasFiles = hasGroups.getGroup(groupKey);
                                if (hasFiles.getFiles()!=null) {
                                    for(FileType fileType : hasFiles.getFiles().keySet()) {
                                        if (!fileType.is2dImage()) continue;
                                        countedTypeNames.add(fileType.name());
                                    }
                                }
                            }
                        }
                        else {
                            String name = objective+" "+result.getName();
                            countedResultNames.add(name);
                            if (result.getFiles()!=null) {
                                for(FileType fileType : result.getFiles().keySet()) {
                                    if (!fileType.is2dImage()) continue;
                                    countedTypeNames.add(fileType.name());
                                }
                            }
                        }
                    }
                }
            }
        }
        
        getToolbar().getDefaultResultButton().setVisible(countedResultNames.size()>2);
        JPopupMenu popupMenu = getToolbar().getDefaultResultButton().getPopupMenu();
        popupMenu.removeAll();
        
        for(final String resultName : countedResultNames.elementSet()) {
            if (countedResultNames.count(resultName)>1) {
                JMenuItem menuItem = new JRadioButtonMenuItem(resultName, resultName.equals(defaultResult.getResultKey()));
                menuItem.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {

                        defaultResult = new DefaultResult(resultName);
                        
                        SimpleWorker worker = new SimpleWorker() {

                            @Override
                            protected void doStuff() throws Exception {
                                Preference preference = DomainMgr.getDomainMgr().getPreference(DomainConstants.PREFERENCE_CATEGORY_DEFAULT_SAMPLE_RESULT,parentObject.getId().toString());
                                if (preference==null) {
                                    preference = new Preference(SessionMgr.getSubjectKey(), DomainConstants.PREFERENCE_CATEGORY_DEFAULT_SAMPLE_RESULT, parentObject.getId().toString(), resultName);
                                }
                                else {
                                    preference.setValue(resultName);
                                }
                                DomainMgr.getDomainMgr().savePreference(preference);
                            }

                            @Override
                            protected void hadSuccess() {
                                showDomainObjects(domainObjectList, null);
                            }

                            @Override
                            protected void hadError(Throwable error) {
                                SessionMgr.getSessionMgr().handleException(error);
                            }
                        };

                        worker.execute();
                    }
                });
                popupMenu.add(menuItem);
            }
        }        

        getToolbar().getDefaultTypeButton().setVisible(!countedTypeNames.isEmpty());
        JPopupMenu popupMenu2 = getToolbar().getDefaultTypeButton().getPopupMenu();
        popupMenu2.removeAll();

        for(final String typeName : countedTypeNames.elementSet()) {
            if (countedTypeNames.count(typeName)>1) {
                FileType fileType = FileType.valueOf(typeName);
                JMenuItem menuItem = new JRadioButtonMenuItem(fileType.getLabel(), typeName.equals(defaultImageType));
                menuItem.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {

                        defaultImageType = typeName;
                        
                        SimpleWorker worker = new SimpleWorker() {

                            @Override
                            protected void doStuff() throws Exception {
                                Preference preference = DomainMgr.getDomainMgr().getPreference(DomainConstants.PREFERENCE_CATEGORY_DEFAULT_IMAGE_TYPE,parentObject.getId().toString());
                                if (preference==null) {
                                    preference = new Preference(SessionMgr.getSubjectKey(), DomainConstants.PREFERENCE_CATEGORY_DEFAULT_IMAGE_TYPE, parentObject.getId().toString(), typeName);
                                }
                                else {
                                    preference.setValue(typeName);
                                }
                                DomainMgr.getDomainMgr().savePreference(preference);
                            }

                            @Override
                            protected void hadSuccess() {
                                showDomainObjects(domainObjectList, null);
                            }

                            @Override
                            protected void hadError(Throwable error) {
                                SessionMgr.getSessionMgr().handleException(error);
                            }
                        };

                        worker.execute();
                    }
                });
                popupMenu2.add(menuItem);
            }
        }        
        
        showImageObjects(domainObjectList.getDomainObjects(), success);
    }

    @Override
    public void selectDomainObjects(List<DomainObject> domainObjects, boolean select, boolean clearAll) {
        if (domainObjects.isEmpty()) {
            return;
        }
        DomainObject first = domainObjects.get(0);
        selectDomainObject(first, select, clearAll);
        for(int i=1; i<domainObjects.size(); i++) {
            DomainObject domainObject = domainObjects.get(i);
            selectDomainObject(domainObject, select, false);
        }
    }
    
    public void selectDomainObject(DomainObject domainObject, boolean selected, boolean clearAll) {
        if (selected) {
            selectImageObject(domainObject, clearAll);
        }
        else {
            deselectImageObject(domainObject);
        }
    }

    @Override
    public void preferenceChanged(Preference preference) {
    }
    
    @Override
    public JPanel getPanel() {
        return this;
    }
    
    /**
     * The purpose of this class is to parse the default result key and cache 
     * the resulting tokens for use within speed-sensitive UI rendering methods.
     */
    private class DefaultResult {

        private final String resultKey;
        private final String objective;
        private final String resultName;
        private final String resultNamePrefix;
        private final String groupName;

        private DefaultResult(String resultKey) {
            this.resultKey = resultKey;
            if (!DomainConstants.PREFERENCE_VALUE_LATEST.equals(resultKey)) {
                String[] parts = resultKey.split(" ",2);
                this.objective = parts[0];
                this.resultName = parts[1];

                Pattern p = Pattern.compile("(.*?)\\s*(\\((.*?)\\))?");
                Matcher m = p.matcher(resultName);
                if (!m.matches()) {
                    throw new IllegalStateException("Result name cannot be parsed: "+parts[1]);
                }
                else {
                    this.resultNamePrefix = m.matches()?m.group(1):null;
                    this.groupName = m.matches()?m.group(3):null;
                }
            }
            else {
                this.objective = null;
                this.resultName = null;
                this.resultNamePrefix = null;
                this.groupName = null;
            }
        }

        public String getResultKey() {
            return resultKey;
        }

        public String getObjective() {
            return objective;
        }

        public String getResultName() {
            return resultName;
        }

        public String getResultNamePrefix() {
            return resultNamePrefix;
        }

        public String getGroupName() {
            return groupName;
        }
    }
}
