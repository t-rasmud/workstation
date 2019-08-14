package org.janelia.workstation.core.api;

import java.util.List;

import org.janelia.model.domain.DomainObject;
import org.janelia.model.domain.gui.alignment_board.AlignmentContext;
import org.janelia.workstation.core.api.facade.impl.rest.DomainFacadeImpl;
import org.janelia.workstation.core.api.facade.impl.rest.OntologyFacadeImpl;
import org.janelia.workstation.core.api.facade.impl.rest.SampleFacadeImpl;
import org.janelia.workstation.core.api.facade.impl.rest.SubjectFacadeImpl;
import org.janelia.workstation.core.api.facade.impl.rest.WorkspaceFacadeImpl;
import org.janelia.workstation.core.api.facade.interfaces.DomainFacade;
import org.janelia.workstation.core.api.facade.interfaces.OntologyFacade;
import org.janelia.workstation.core.api.facade.interfaces.SampleFacade;
import org.janelia.workstation.core.api.facade.interfaces.SubjectFacade;
import org.janelia.workstation.core.api.facade.interfaces.WorkspaceFacade;
import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * NOTE: this always fails at time of writing.
 *
 * @author fosterl
 */
public class DomainModelTest {
    private DomainFacade domainFacade;
    private DomainModel domainModel;
    
    public DomainModelTest() {
    }
    
    @Before
    public void setUp() {
        String testServerUrl = "testServerUrl";
        domainFacade = new DomainFacadeImpl(testServerUrl);
        OntologyFacade ontologyFacade = new OntologyFacadeImpl(testServerUrl);
        SampleFacade sampleFacade = new SampleFacadeImpl(testServerUrl, "testLegacyServerUrl");
        SubjectFacade subjectFacade = new SubjectFacadeImpl(testServerUrl);
        WorkspaceFacade workspaceFacade = new WorkspaceFacadeImpl(testServerUrl);
        domainModel = new DomainModel(domainFacade, ontologyFacade, sampleFacade, subjectFacade, workspaceFacade);
    }
    
    @After
    public void tearDown() {
    }

    // NOT YET WORKING. LLF @Test
    public void testAllInstanceFetch() throws Exception {
        List<DomainObject> contexts = domainModel.getAllDomainObjectsByClass(AlignmentContext.class.getName());
        assertTrue("Empty context collection.", ! contexts.isEmpty());
        for (DomainObject dobj: contexts) {
            assertTrue("Not expected domain object type.  Not Alignment Context.", dobj instanceof AlignmentContext);
            AlignmentContext ctx = (AlignmentContext)dobj;
            assertNotNull("Null Alignment Space name", ctx.getAlignmentSpace());
            assertNotNull("Null Image Size", ctx.getImageSize());
            assertNotNull("Null Optical Resolution", ctx.getOpticalResolution());
            System.out.println(String.format("Name=%s, ImageSize=%s, OpticalRes=%s", ctx.getAlignmentSpace(), ctx.getImageSize(), ctx.getOpticalResolution()));
        }
    }

}
