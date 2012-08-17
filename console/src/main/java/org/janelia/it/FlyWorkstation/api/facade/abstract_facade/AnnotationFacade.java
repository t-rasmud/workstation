package org.janelia.it.FlyWorkstation.api.facade.abstract_facade;

import java.util.List;
import java.util.Set;

import org.janelia.it.jacs.model.entity.Entity;
import org.janelia.it.jacs.shared.annotation.PatternAnnotationDataManager;

/**
 * Created by IntelliJ IDEA.
 * User: saffordt
 * Date: 8/8/11
 * Time: 9:33 AM
 */
public interface AnnotationFacade extends EntityFacade {
	
    public List<Entity> getAnnotationsForEntity(Long entityId) throws Exception;

    public List<Entity> getAnnotationsForEntities(List<Long> entityIds) throws Exception;
    
    public List<Entity> getAnnotationsForChildren(Long entityId) throws Exception;

    public List<Entity> getEntitiesForAnnotationSession(Long annotationSessionId) throws Exception;

    public List<Entity> getAnnotationsForSession(Long annotationSessionId) throws Exception;

    public List<Entity> getCategoriesForAnnotationSession(Long annotationSessionId) throws Exception;

    public Set<Long> getCompletedEntityIds(Long annotationSessionId) throws Exception;
    
    public void removeAnnotation(Long annotationId) throws Exception;

    public void removeAllOntologyAnnotationsForSession(Long annotationSessionId) throws Exception;
    
    public Object[] getPatternAnnotationQuantifierMapsFromSummary() throws Exception;

    public Object[] getMaskQuantifierMapsFromSummary(String maskFolderName) throws Exception;

    public PatternAnnotationDataManager getPatternAnnotationDataManagerByType(String type) throws Exception;

}
