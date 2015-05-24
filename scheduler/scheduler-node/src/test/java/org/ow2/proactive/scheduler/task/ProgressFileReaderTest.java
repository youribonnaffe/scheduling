package org.ow2.proactive.scheduler.task;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.ow2.proactive.scripting.helper.progress.ProgressFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Test cases associated to {@link ProgressFileReader}.
 *
 * @author The ProActive Team
 */
public class ProgressFileReaderTest {

    private static final int NB_UPDATES = 3;

    private static final int SLEEP_TIMEOUT = 1000; // in milliseconds

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private ProgressFileReader progressFileReader = new ProgressFileReader();

    private ProgressFileReaderHistory listener = new ProgressFileReaderHistory();

    @Before
    public void setup() throws IOException {
        String progressFileName = "test";
        progressFileReader.register(listener);
        progressFileReader.start(folder.getRoot(), progressFileName);
    }

    @Test
    public void testProgressMultipleUpdates() throws IOException, InterruptedException {
        Path progressFile = progressFileReader.getProgressFile();

        int lastProgressValue = -1;

        for (int i=1; i <= NB_UPDATES; i++) {
            lastProgressValue = i * (100 / NB_UPDATES);
            ProgressFile.setProgress(progressFile, lastProgressValue);
            TimeUnit.MILLISECONDS.sleep(SLEEP_TIMEOUT);
        }

        assertThat(listener.getValues(), hasSize(NB_UPDATES));
        assertThat(ProgressFile.getProgress(progressFile), is(lastProgressValue));
    }

    @Test
    public void testProgressMultipleInvalidUpdates() throws IOException, InterruptedException {
        Path progressFile = progressFileReader.getProgressFile();

        int lastProgressValue = -1;
        int nbValidValues = 0;

        for (int i=1; i <= NB_UPDATES; i++) {
            if (i % 2 == 0) {
                ProgressFile.setProgress(progressFile, i * (100 / NB_UPDATES));
                nbValidValues++;
            } else {
                ProgressFile.setProgress(progressFile, "invalid" + i);
            }

            TimeUnit.MILLISECONDS.sleep(SLEEP_TIMEOUT);
        }

        assertThat(listener.getValues(), hasSize(nbValidValues));
        assertThat(ProgressFile.getProgress(progressFile), is(lastProgressValue));
    }

    private static class ProgressFileReaderHistory implements ProgressFileReader.Listener {

        private final List<Integer> values = new ArrayList<>(NB_UPDATES);

        @Override
        public void onProgressUpdate(int newValue) {
            values.add(newValue);
        }

        public List<Integer> getValues() {
            return values;
        }

        public void clear() {
            values.clear();
        }

    }

    @After
    public void teardown() {
        listener.clear();
        progressFileReader.stop();
        progressFileReader.unregister(listener);
    }

}
