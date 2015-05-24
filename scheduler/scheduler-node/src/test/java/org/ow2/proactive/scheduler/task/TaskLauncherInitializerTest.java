package org.ow2.proactive.scheduler.task;

import org.junit.Before;
import org.junit.Test;
import org.ow2.proactive.scheduler.common.task.dataspaces.*;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;

public class TaskLauncherInitializerTest {

    private TaskLauncherInitializer initializer;

    @Before
    public void setUp() {
        initializer = new TaskLauncherInitializer();
    }

    @Test
    public void input_files_can_be_filtered() throws Exception {
        initializer.setTaskInputFiles(inputSelectors("$TEST/a", "b"));
        initializer.getTaskInputFiles().get(0).getInputFiles().setExcludes("$TEST/excluded");

        List<InputSelector> filteredInputFiles = initializer.getFilteredInputFiles(
                Collections.<String, Serializable>singletonMap("TEST", "folder"));

        InputSelector selector = filteredInputFiles.get(0);
        assertArrayEquals(new String[]{"folder/a", "b"}, selector.getInputFiles().getIncludes());
        assertArrayEquals(new String[]{"folder/excluded"}, selector.getInputFiles().getExcludes());
    }

    @Test
    public void output_files_can_be_filtered() throws Exception {
        initializer.setTaskOutputFiles(outputSelectors("${TEST}/a", "b"));
        initializer.getTaskOutputFiles().get(0).getOutputFiles().setExcludes("$TEST/excluded");

        List<OutputSelector> filteredOutputFiles = initializer.getFilteredOutputFiles(
                Collections.<String, Serializable>singletonMap("TEST", "folder"));

        OutputSelector selector = filteredOutputFiles.get(0);
        assertArrayEquals(new String[]{"folder/a", "b"}, selector.getOutputFiles().getIncludes());
        assertArrayEquals(new String[]{"folder/excluded"}, selector.getOutputFiles().getExcludes());
    }

    @Test
    public void output_empty_filters() throws Exception {
        initializer.setTaskOutputFiles(outputSelectors("$TEST/a", "b"));

        List<OutputSelector> filteredOutputFiles = initializer.getFilteredOutputFiles(
                Collections.<String, Serializable>emptyMap());

        OutputSelector selector = filteredOutputFiles.get(0);
        assertArrayEquals(new String[]{"$TEST/a", "b"}, selector.getOutputFiles().getIncludes());
    }

    @Test
    public void input_empty_filters() throws Exception {
        initializer.setTaskInputFiles(inputSelectors("$TEST/a", "b"));

        List<InputSelector> filteredInputFiles = initializer.getFilteredInputFiles(
                Collections.<String, Serializable>emptyMap());

        InputSelector selector = filteredInputFiles.get(0);
        assertArrayEquals(new String[]{"$TEST/a", "b"}, selector.getInputFiles().getIncludes());
    }

    @Test
    public void input_null_filters() throws Exception {
        initializer.setTaskInputFiles(inputSelectors("$TEST/a", "b"));

        List<InputSelector> filteredInputFiles = initializer.getFilteredInputFiles(null);

        InputSelector selector = filteredInputFiles.get(0);
        assertArrayEquals(new String[]{"$TEST/a", "b"}, selector.getInputFiles().getIncludes());
    }

    @Test
    public void output_null_filters() throws Exception {
        initializer.setTaskOutputFiles(outputSelectors("$TEST/a", "b"));

        List<OutputSelector> filteredOutputFiles = initializer.getFilteredOutputFiles(null);

        OutputSelector selector = filteredOutputFiles.get(0);
        assertArrayEquals(new String[]{"$TEST/a", "b"}, selector.getOutputFiles().getIncludes());
    }

    private List<InputSelector> inputSelectors(String... selectors) {
        return Collections.singletonList(new InputSelector(new FileSelector(selectors),
                InputAccessMode.TransferFromUserSpace));
    }

    private List<OutputSelector> outputSelectors(String... selectors) {
        return Collections.singletonList(new OutputSelector(new FileSelector(selectors),
                OutputAccessMode.TransferToUserSpace));
    }
}