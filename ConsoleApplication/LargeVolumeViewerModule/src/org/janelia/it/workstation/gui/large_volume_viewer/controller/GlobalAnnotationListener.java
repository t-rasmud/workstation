package org.janelia.it.workstation.gui.large_volume_viewer.controller;

import org.janelia.it.jacs.model.domain.tiledMicroscope.TmNeuronMetadata;
import org.janelia.it.jacs.model.domain.tiledMicroscope.TmWorkspace;
import org.janelia.it.workstation.gui.large_volume_viewer.style.NeuronStyle;

/**
 * Implement this to hear about workspace/all-annotation-scoped changes.
 * 
 * @author fosterl
 */
public interface GlobalAnnotationListener {
    void workspaceLoaded(TmWorkspace workspace);
    void neuronSelected(TmNeuronMetadata neuron);
    void neuronStyleChanged(TmNeuronMetadata neuron, NeuronStyle style);
}
