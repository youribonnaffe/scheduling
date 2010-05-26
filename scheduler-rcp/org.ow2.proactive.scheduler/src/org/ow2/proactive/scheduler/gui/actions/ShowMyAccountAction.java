/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2010 INRIA/University of 
 * 				Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 
 * or a different license than the GPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s):
 *
 * ################################################################
 * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive.scheduler.gui.actions;

import java.net.URL;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Display;
import org.objectweb.proactive.ic2d.chartit.data.provider.IDataProvider;
import org.objectweb.proactive.ic2d.chartit.data.resource.IResourceDescriptor;
import org.objectweb.proactive.ic2d.chartit.editor.ChartItDataEditor;
import org.ow2.proactive.scheduler.Activator;


/**
 * This class represents an action that corresponds to a chartable resource from
 * the My Account MBean. 
 *  
 * @author The ProActive Team 
 */
public final class ShowMyAccountAction extends Action {

    public static final String NAME = "My Account";

    /** The actions manager */
    private final JMXActionsManager manager;

    /** The name of the runtime data MBean */
    private final ObjectName mBeanName;

    /** The URL of the configuration file */
    private final URL configFileURL;

    /**
     * Creates a new instance of this class.
     */
    ShowMyAccountAction(final JMXActionsManager manager) throws Exception {

        // Set a descriptive icon
        super.setImageDescriptor(ImageDescriptor.createFromURL(FileLocator.find(Activator.getDefault()
                .getBundle(), new Path("icons/account.gif"), null)));
        // This action is disabled by default
        super.setEnabled(false);
        super.setText("Show " + NAME);
        super.setToolTipText("Show " + NAME);

        this.manager = manager;
        this.mBeanName = new ObjectName("ProActiveScheduler:name=MyAccount");
        this.configFileURL = FileLocator.find(Activator.getDefault().getBundle(), new Path(
            "config/MyAccountChartItConf.xml"), null);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        // Each time the action is disabled close the corresponding chart editor
        if (!enabled) {
            try {
                // activateIfFound method with false to close the editor
                JMXActionsManager.activateIfFound(NAME, false);
            } catch (Exception t) {
                MessageDialog.openError(Display.getDefault().getActiveShell(), "Unable to close the " + NAME,
                        t.getMessage());
                t.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        // Try to connect the JMX client    	
        if (!this.manager.getJMXClientHelper().isConnected()) {
            return;
        }
        try {
            // true for activate
            if (!JMXActionsManager.activateIfFound(NAME, true)) {
                // Acquire the connection
                final MBeanServerConnection con = this.manager.getJMXClientHelper().getConnector()
                        .getMBeanServerConnection();
                // Open new editor based on the descriptor
                ChartItDataEditor
                        .openNewFromResourceDescriptor((IResourceDescriptor) new MyAccountResourceDescriptor(
                            con));
            }
        } catch (Exception e) {
            MessageDialog.openError(Display.getDefault().getActiveShell(), "Unable to open the " + NAME, e
                    .getMessage());
            e.printStackTrace();
        }
    }

    //////////////////

    private final class MyAccountResourceDescriptor implements IResourceDescriptor {
        private final MBeanServerConnection connection;

        private MyAccountResourceDescriptor(final MBeanServerConnection connection) {
            this.connection = connection;
        }

        public IDataProvider[] getCustomDataProviders() {
            return new IDataProvider[] {
            // FormattedTotalTaskDuration AS STRING 		
                    new IDataProvider() {
                        public Object provideValue() throws Throwable {
                            final long s = (Long) connection.getAttribute(mBeanName, "TotalTaskDuration");
                            final long ss = s / 1000;
                            return String
                                    .format("%d h %02d m %02d s", ss / 3600, (ss % 3600) / 60, (ss % 60));
                        }

                        public String getType() {
                            return "String";
                        }

                        public String getName() {
                            return "FormattedTotalTaskDuration";
                        }

                        public String getDescription() {
                            return "Formatted total task duration";
                        }
                    },
                    // ComputedAverageTaskDuration AS LONG
                    new IDataProvider() {
                        public Object provideValue() throws Throwable {
                            final AttributeList attList = connection.getAttributes(mBeanName, new String[] {
                                    "TotalTaskDuration", "TotalTaskCount" });
                            final long totalTaskDuration = (Long) ((Attribute) attList.get(0)).getValue();
                            final int totalTaskCount = (Integer) ((Attribute) attList.get(1)).getValue();
                            if (totalTaskCount == 0) {
                                return 0;
                            }
                            // Average task duration (in seconds)
                            return (long) ((totalTaskDuration / totalTaskCount) / 1000);
                        }

                        public String getType() {
                            return "long";
                        }

                        public String getName() {
                            return "ComputedAverageTaskDuration";
                        }

                        public String getDescription() {
                            return "Computed average task duration value in seconds: (TotalTaskDuration / TotalTaskCount) / 1000";
                        }
                    },
                    // ComputedAverageJobDuration AS LONG
                    new IDataProvider() {
                        public Object provideValue() throws Throwable {
                            final AttributeList attList = connection.getAttributes(mBeanName, new String[] {
                                    "TotalJobDuration", "TotalJobCount" });
                            final long totalJobDuration = (Long) ((Attribute) attList.get(0)).getValue();
                            final int totalJobCount = (Integer) ((Attribute) attList.get(1)).getValue();
                            if (totalJobCount == 0) {
                                return 0;
                            }
                            // Average job duration (in seconds)
                            return (long) ((totalJobDuration / totalJobCount) / 1000);
                        }

                        public String getType() {
                            return "long";
                        }

                        public String getName() {
                            return "ComputedAverageJobDuration";
                        }

                        public String getDescription() {
                            return "Computed average job duration value in seconds: (TotalJobDuration / TotalJobCount) / 1000";
                        }
                    },
                    // FormattedComputedAverageTaskDuration AS STRING
                    new IDataProvider() {
                        public Object provideValue() throws Throwable {
                            final AttributeList attList = connection.getAttributes(mBeanName, new String[] {
                                    "TotalTaskDuration", "TotalTaskCount" });
                            final long totalTaskDuration = (Long) ((Attribute) attList.get(0)).getValue();
                            final int totalTaskCount = (Integer) ((Attribute) attList.get(1)).getValue();
                            // Average task duration (in seconds)                	
                            long atd = 0;
                            if (totalTaskCount != 0) {
                                atd = (totalTaskDuration / totalTaskCount) / 1000;
                            }
                            return String.format("%d h %02d m %02d s", atd / 3600, (atd % 3600) / 60,
                                    (atd % 60));
                        }

                        public String getType() {
                            return "String";
                        }

                        public String getName() {
                            return "FormattedComputedAverageTaskDuration";
                        }

                        public String getDescription() {
                            return "Formatted computed average task duration value in seconds: (TotalTaskDuration / TotalTaskCount) / 1000";
                        }
                    },
                    // FormattedComputedAverageJobDuration AS STRING
                    new IDataProvider() {
                        public Object provideValue() throws Throwable {
                            final AttributeList attList = connection.getAttributes(mBeanName, new String[] {
                                    "TotalJobDuration", "TotalJobCount" });
                            final long totalJobDuration = (Long) ((Attribute) attList.get(0)).getValue();
                            final int totalJobCount = (Integer) ((Attribute) attList.get(1)).getValue();
                            // Average job duration (in seconds)
                            long ajd = 0;
                            if (totalJobCount != 0) {
                                ajd = (totalJobDuration / totalJobCount) / 1000;
                            }
                            return String.format("%d h %02d m %02d s", ajd / 3600, (ajd % 3600) / 60,
                                    (ajd % 60));
                        }

                        public String getType() {
                            return "String";
                        }

                        public String getName() {
                            return "FormattedComputedAverageJobDuration";
                        }

                        public String getDescription() {
                            return "Formatted computed average job duration value in seconds: (TotalJobDuration / TotalJobCount) / 1000";
                        }
                    } };
        }

        public URL getConfigFileURL() {
            return ShowMyAccountAction.this.configFileURL;
        }

        public String getHostUrlServer() {
            return ShowMyAccountAction.this.manager.getJMXClientHelper().getConnector().toString();
        }

        public MBeanServerConnection getMBeanServerConnection() {
            return this.connection;
        }

        public String getName() {
            return ShowMyAccountAction.NAME;
        }

        public ObjectName getObjectName() {
            return ShowMyAccountAction.this.mBeanName;
        }
    }

    public static void main(String[] args) {
        long time = 0;
        int nb = 2;

        long res = (time / nb) / 1000;

        System.out.println("ShowMyAccountAction.main() --> " + res);
    }
}