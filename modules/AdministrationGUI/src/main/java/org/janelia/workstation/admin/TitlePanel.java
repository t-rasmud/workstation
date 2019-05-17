package org.janelia.workstation.admin;

import java.awt.Font;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * All screens in the AdministrationGUI can reuse this component in order to get a consistent GUI for screen titles,
 * with back buttons.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class TitlePanel extends JPanel  {

    private static final Font TITLE_FONT = new Font("Sans Serif", Font.BOLD, 15);

    public TitlePanel(String titleText, String returnText, ActionListener returnAction) {

        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));

        String titleText1 = titleText;
        String returnText1 = returnText;

        JButton returnButton = new JButton(returnText);
        returnButton.addActionListener(returnAction);



        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(TITLE_FONT);

        add(returnButton);
        add(Box.createVerticalStrut(4));
        add(titleLabel);

    }
}
