package org.ow2.proactive.resourcemanager.gui.table;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;


public class TableLabelProvider extends ColumnLabelProvider {

    private static final int NS_COLUMN_NUMBER = 0;
    private static final int HOST_COLUMN_NUMBER = 1;
    private static final int STATE_COLUMN_NUMBER = 2;
    private static final int URL_COLUMN_NUMBER = 3;

    private int columnIndex;

    public TableLabelProvider(int columnNumber) {
        super();
        this.columnIndex = columnNumber;
    }

    public Image getImage(Object element) {
        if (element instanceof NodeTableItem && columnIndex == STATE_COLUMN_NUMBER) {
            switch (((NodeTableItem) element).getState()) {
                case DOWN:
                    return ImageDescriptor.createFromFile(RMTableViewer.class, "icons/down.gif")
                            .createImage();
                case FREE:
                    return ImageDescriptor.createFromFile(RMTableViewer.class, "icons/free.gif")
                            .createImage();
                case BUSY:
                    return ImageDescriptor.createFromFile(RMTableViewer.class, "icons/busy.gif")
                            .createImage();
                case TO_BE_RELEASED:
                    return ImageDescriptor.createFromFile(RMTableViewer.class, "icons/to_release.gif")
                            .createImage();
            }
        }
        return null;
    }

    public String getText(Object element) {
        if (element instanceof NodeTableItem) {
            NodeTableItem nodeItem = (NodeTableItem) element;
            String str = null;
            switch (columnIndex) {
                case NS_COLUMN_NUMBER:
                    str = nodeItem.getNodeSource();
                    break;
                case HOST_COLUMN_NUMBER:
                    str = nodeItem.getHost();
                    break;
                case URL_COLUMN_NUMBER:
                    str = nodeItem.getNodeUrl();
                    break;
            }
            return str;
        }
        return null;
    }

    public int getToolTipDisplayDelayTime(Object object) {
        return 800;
    }

    public int getToolTipTimeDisplayed(Object object) {
        return 3000;
    }

    public Point getToolTipShift(Object object) {
        return new Point(5, 5);
    }

    public boolean useNativeToolTip(Object object) {
        return false;
    }

    public String getToolTipText(Object element) {
        if (element instanceof NodeTableItem && columnIndex == STATE_COLUMN_NUMBER) {
            switch (((NodeTableItem) element).getState()) {
                case DOWN:
                    return "Node is down or unreachable";
                case FREE:
                    return "Node is ready to perform tasks";
                case BUSY:
                    return "Node is currently performing a task";
                case TO_BE_RELEASED:
                    return "Node is busy and will be removed at task's end";
            }
        }
        return null;
    }
}
