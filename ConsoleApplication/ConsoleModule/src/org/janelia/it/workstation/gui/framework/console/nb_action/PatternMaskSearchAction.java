package org.janelia.it.workstation.gui.framework.console.nb_action;

import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@ActionID(
        category = "Search",
        id = "PatternMaskSearchAction"
)
@ActionRegistration(
        displayName = "#CTL_PatternMaskSearchAction"
)
@ActionReference(path = "Menu/Search", position = 1200)
@Messages("CTL_PatternMaskSearchAction=Pattern Mask Search")
public final class PatternMaskSearchAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        new SearchActionDelegate().maskSearch();
    }
}
