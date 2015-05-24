package org.ow2.proactive.scheduler.task;

import org.junit.Test;
import org.ow2.proactive.scheduler.common.task.dataspaces.FileSelector;
import org.ow2.proactive.scheduler.common.task.dataspaces.InputAccessMode;
import org.ow2.proactive.scheduler.common.task.dataspaces.InputSelector;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TaskLauncherInitializerTest {

    // TODO write more tests
    @Test
    public void testName() throws Exception {

        TaskLauncherInitializer initializer = new TaskLauncherInitializer();
        initializer.setTaskInputFiles(Collections.singletonList(new InputSelector(new FileSelector("$TEST/a", "b"),
                InputAccessMode.TransferFromInputSpace)));

        List<InputSelector> filteredInputFiles = initializer.getFilteredInputFiles(Collections.<String, Serializable>singletonMap("TEST", "folder"));

        InputSelector inputSelector = filteredInputFiles.get(0);
        assertEquals("folder/a", inputSelector.getInputFiles().getIncludes()[0]);
    }
}