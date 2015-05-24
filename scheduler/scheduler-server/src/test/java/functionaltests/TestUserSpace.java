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
package functionaltests;

import org.apache.commons.io.FileUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.junit.Assert;
import org.objectweb.proactive.extensions.dataspaces.vfs.VFSFactory;
import org.ow2.proactive.scheduler.common.Scheduler;
import org.ow2.proactive.scheduler.common.job.JobId;
import org.ow2.proactive.scheduler.common.job.JobResult;
import org.ow2.proactive.scheduler.common.job.TaskFlowJob;
import org.ow2.proactive.scheduler.common.task.ForkEnvironment;
import org.ow2.proactive.scheduler.common.task.JavaTask;
import org.ow2.proactive.scheduler.common.task.dataspaces.InputAccessMode;
import org.ow2.proactive.scheduler.common.task.dataspaces.OutputAccessMode;
import org.ow2.proactive.scripting.SimpleScript;
import org.ow2.tests.FunctionalTest;

import java.io.*;
import java.net.URI;


/**
 * Submits a job using the default USER Space, it checks at the end that the output files can be accessed in the USER Space
 * <p/>
 * This test does :
 * <ul><li>write inFiles to INPUT
 * <li>task A: transfer inFiles from INPUT to SCRATCH
 * <li>task A: copy SCRATCH/inFiles to SCRATCH/inFiles.glob.A in pre-script
 * <li>task A: transfer inFiles.glob.A from SCRATCH to USER
 * <li>task B: transfer inFiles.glob.A from USER to SCRATCH
 * <li>task B: copy SCRATCH/inFiles.glob.A to SCRATCH/inFiles.out in pre-script
 * <li>task B: transfer inFiles.out from SCRATCH to OUTPUT
 * </ul>
 * Then, the test checks that the USER space has been cleared; and that inFiles.out have
 * been copied to OUTPUT and are identical to the ones written in the INPUT.
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 2.2
 */
public class TestUserSpace extends FunctionalTest {

    private static final String[][] inFiles = { { "A", "Content of A" }, { "B", "not much" },
            { "_1234", "!@#%$@%54vc54\b\t\\\\\nasd123!@#", "res1", "one of the output files" },
            { "res2", "second\noutput\nfile" }, { "__.res_3", "third\toutput\nfile\t&^%$$#@!\n" } };

    private static final String pathReplaceFile = "TestPathReplace";

    private static String inFileArr = "";

    static {
        inFileArr += "[";
        for (int i = 0; i < inFiles.length; i++) {
            inFileArr += "\"" + inFiles[i][0] + "\"";
            if (i < inFiles.length - 1) {
                inFileArr += ",";
            }
        }
        inFileArr += "]";
    }

    private static final String scriptA = "" + //
        "def out;                                              \n" + //
        "def arr = " + inFileArr + ";                          \n" + //
        "for (def i=0; i < arr.size(); i++) {                  \n" + //
        "  def input = new File(arr[i]);         \n" + //
        "  def br = input.newInputStream();       \n" + //
        "  def ff = new File(                    \n" + //
        "     arr[i] + \".glob.A\");\n                         \n" + //
        "  ff.text = input.text;                                        \n" + //
        "}                                                     \n" + //
        "                                                      \n" + //
        "";

    private static final String scriptB = "" + //
        "def out;                                              \n" + //
        "def arr = " + inFileArr + ";                          \n" + //
        "for (def i=0; i < arr.size(); i++) {                  \n" + //
        "  def input =new File(                 \n" + //
        "      arr[i] + \".glob.A\");                          \n" + //
        "  def ff = new File(                    \n" + //
        "     arr[i] + \".out\");\n                            \n" + //
        "  ff.text = input.text;            \n" + //
        "}                                                     \n" + //
        "                                                      \n" + //
        "";

    @org.junit.Test
    public void run() throws Throwable {

        File in = File.createTempFile("input", "space");
        in.delete();
        in.mkdir();
        String inPath = in.getAbsolutePath();

        File out = File.createTempFile("output", "space");
        out.delete();
        out.mkdir();
        String outPath = out.getAbsolutePath();

        FileSystemManager fsManager = null;

        {
            try {
                fsManager = VFSFactory.createDefaultFileSystemManager();
            } catch (FileSystemException e) {
                e.printStackTrace();
                logger.error("Could not create Default FileSystem Manager", e);
            }
        }

        Scheduler sched = SchedulerTHelper.getSchedulerInterface();
        String userURI = sched.getUserSpaceURIs().get(0);
        Assert.assertTrue(userURI.startsWith("file:"));
        System.out.println("User URI is " + userURI);
        String userPath = new File(new URI(userURI)).getAbsolutePath();

        FileObject pathReplaceFO = fsManager.resolveFile(userURI + "/" + pathReplaceFile);

        if (pathReplaceFO.exists()) {
            pathReplaceFO.delete();
        }

        /**
         * Writes inFiles in INPUT
         */
        writeFiles(inFiles, inPath);
        File testPathRepl = new File(inPath + File.separator + pathReplaceFile);
        testPathRepl.createNewFile();
        PrintWriter out2 = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
            testPathRepl))));
        out2.print(pathReplaceFile);
        out2.close();

        TaskFlowJob job = new TaskFlowJob();
        job.setName(this.getClass().getSimpleName());
        job.setInputSpace(in.toURI().toURL().toString());
        job.setOutputSpace(out.toURL().toString());

        JavaTask A = new JavaTask();
        A.setExecutableClassName("org.ow2.proactive.scheduler.examples.EmptyTask");
        A.setForkEnvironment(new ForkEnvironment());
        A.setName("A");
        for (String[] file : inFiles) {
            A.addInputFiles(file[0], InputAccessMode.TransferFromInputSpace);
            A.addOutputFiles(file[0] + ".glob.A", OutputAccessMode.TransferToUserSpace);
        }
        A.setPreScript(new SimpleScript(scriptA, "groovy"));
        job.addTask(A);

        JavaTask B = new JavaTask();
        B.setExecutableClassName("org.ow2.proactive.scheduler.examples.EmptyTask");
        B.setForkEnvironment(new ForkEnvironment());
        B.setName("B");
        B.addDependence(A);
        for (String[] file : inFiles) {
            B.addInputFiles(file[0] + ".glob.A", InputAccessMode.TransferFromUserSpace);
            B.addOutputFiles(file[0] + ".out", OutputAccessMode.TransferToOutputSpace);
        }
        B.setPreScript(new SimpleScript(scriptB, "groovy"));
        job.addTask(B);

        JobId id = sched.submit(job);
        while (true) {
            try {
                if (SchedulerTHelper.getJobResult(id) != null) {
                    break;
                }
                Thread.sleep(2000);
            } catch (Throwable exc) {
            }
        }

        JobResult jr = SchedulerTHelper.getJobResult(id);
        Assert.assertFalse(jr.hadException());

        /**
         * check: inFiles > IN > LOCAL A > GLOBAL > LOCAL B > OUT
         */
        for (int i = 0; i < inFiles.length; i++) {
            File f = new File(outPath + File.separator + inFiles[i][0] + ".out");
            Assert.assertTrue("File does not exist: " + f.getAbsolutePath(), f.exists());
            Assert.assertEquals("Original and copied files differ", inFiles[i][1], FileUtils
                    .readFileToString(f));
            f.delete();
            File inf = new File(inPath + File.separator + inFiles[i][0]);
            inf.delete();
        }

        /**
         * check that the file produced is accessible in the global user space via the scheduler API
         */

        for (String[] file : inFiles) {
            FileObject outFile = fsManager.resolveFile(userURI + "/" + file[0] + ".glob.A");
            System.out.println("Checking existence of " + outFile.getURL());
            Assert.assertTrue(outFile.getURL() + " exists", outFile.exists());
            File outFile2 = new File(userPath, file[0] + ".glob.A");
            System.out.println("Checking existence of " + outFile2);
            Assert.assertTrue(outFile2 + " exists", outFile2.exists());
        }

    }

    /**
     * @param files Writes files: {{filename1, filecontent1},...,{filenameN, filecontentN}}
     * @param path  in this director
     * @throws java.io.IOException
     */
    private void writeFiles(String[][] files, String path) throws IOException {
        for (String[] file : files) {
            File f = new File(path + File.separator + file[0]);
            f.createNewFile();

            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
                f))));
            out.print(file[1]);
            out.close();
        }
    }
}
