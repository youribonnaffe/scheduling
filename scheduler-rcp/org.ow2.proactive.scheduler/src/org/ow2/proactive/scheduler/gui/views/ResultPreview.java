/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2011 INRIA/University of
 *                 Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s):
 *
 * ################################################################
 * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive.scheduler.gui.views;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.ow2.proactive.scheduler.Activator;
import org.ow2.proactive.scheduler.common.task.util.ResultPreviewTool.SimpleTextPanel;


/**
 * @author The ProActive Team
 */
public class ResultPreview extends ViewPart {

    /** an id */
    public static final String ID = "org.ow2.proactive.scheduler.gui.views.ResultPreview";

    // The shared instance
    private Composite parent = null;
    private static ResultPreview instance = null;
    private Composite root;
    private Frame container;
    private JPanel toBeDisplayed = null;
    private JScrollPane scrollableContainer;

    // -------------------------------------------------------------------- //
    // --------------------------- constructor ---------------------------- //
    // -------------------------------------------------------------------- //
    /**
     * The default constructor
     *
     */
    public ResultPreview() {
        super();
        instance = this;
    }

    // -------------------------------------------------------------------- //
    // ----------------------------- public ------------------------------- //
    // -------------------------------------------------------------------- //
    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static ResultPreview getInstance() {
        return instance;
    }

    /**
     * Update Result preview with JPanel representing a result description to display.
     * @param tbd JPanel object to be displayed.
     */
    public void update(JPanel tbd) {
        if (tbd != toBeDisplayed) {
            toBeDisplayed = tbd;
            container.removeAll();

            // If the tbd contains a child text area its contents will copied to the clipboard
            // from right click->"Copy to Clipboard" context menu on the component
            for (final Component c : tbd.getComponents()) {
                if (c instanceof JTextArea) {
                    final String text = ((JTextArea) c).getText();
                    if ("".equals(text)) {
                        return;
                    }

                    // Create the popup menu
                    final JPopupMenu popup = new JPopupMenu();
                    final JMenuItem menuItem = new JMenuItem("Copy to Clipboard");
                    menuItem.addActionListener(new ActionListener() {
                        public void actionPerformed(final ActionEvent e) {
                            StringSelection data = new StringSelection(text);
                            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                            clipboard.setContents(data, data);
                        }
                    });
                    popup.add(menuItem);

                    // Add listener to the text area so the popup menu can come up
                    c.addMouseListener(new MouseListener() {
                        public void mousePressed(final MouseEvent e) {
                            maybeShowPopup(e);
                        }

                        public void mouseReleased(final MouseEvent e) {
                            maybeShowPopup(e);
                        }

                        private void maybeShowPopup(final MouseEvent e) {
                            if (e.isPopupTrigger()) {
                                popup.show(e.getComponent(), e.getX(), e.getY());
                            }
                        }

                        public void mouseExited(MouseEvent e) {
                        }

                        public void mouseEntered(MouseEvent e) {
                        }

                        public void mouseClicked(MouseEvent e) {
                        }
                    });

                    // exit the loop
                    break;
                }
            }

            scrollableContainer = new JScrollPane(toBeDisplayed);

            container.add(scrollableContainer);
            toBeDisplayed.revalidate();
        }
        container.repaint();
        if (!root.isDisposed()) {
            root.pack();
            root.redraw();
            root.update();
            root.getParent().layout();
        }
    }

    /**
     * Ask to Perspective workbench to display this View, useful if this view is hidden
     * by another one in one of the workbench areas organized in tabs.
     */
    public void putOnTop() {
        try {
            this.getViewSite().getWorkbenchWindow().getActivePage().showView(ResultPreview.ID);
            //create view if not created
        } catch (PartInitException e) {
            Activator.log(IStatus.ERROR, "", e);
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------- //
    // ------------------------- extends viewPart ------------------------- //
    // -------------------------------------------------------------------- //
    /**
     * @see org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
     */
    @Override
    public void createPartControl(Composite theParent) {
        parent = theParent;
        root = new Composite(parent, SWT.EMBEDDED);
        root.setVisible(true);
        container = SWT_AWT.new_Frame(root);
        container.pack();
        container.setVisible(true);
        root.pack();
        parent.pack();
        update(new SimpleTextPanel("No task selected"));
    }

    /**
     * @see org.eclipse.ui.part.WorkbenchPart#setFocus()
     */
    @Override
    public void setFocus() {
    }

    /**
     * @see org.eclipse.ui.part.WorkbenchPart#dispose()
     */
    @Override
    public void dispose() {
        super.dispose();
    }
}