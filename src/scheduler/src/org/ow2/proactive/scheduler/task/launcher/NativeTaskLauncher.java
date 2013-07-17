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
package org.ow2.proactive.scheduler.task.launcher;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.log4j.Logger;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.config.CentralPAPropertyRepository;
import org.objectweb.proactive.core.runtime.ProActiveRuntimeImpl;
import org.objectweb.proactive.extensions.annotation.ActiveObject;
import org.objectweb.proactive.extensions.dataspaces.api.DataSpacesFileObject;
import org.objectweb.proactive.extensions.dataspaces.exceptions.DataSpacesException;
import org.objectweb.proactive.utils.OperatingSystem;
import org.ow2.proactive.scheduler.common.TaskTerminateNotification;
import org.ow2.proactive.scheduler.common.task.ExecutableInitializer;
import org.ow2.proactive.scheduler.common.task.TaskResult;
import org.ow2.proactive.scheduler.common.task.flow.FlowAction;
import org.ow2.proactive.scheduler.task.ExecutableContainer;
import org.ow2.proactive.scheduler.task.NativeExecutable;
import org.ow2.proactive.scheduler.task.NativeExecutableInitializer;
import org.ow2.proactive.scheduler.task.TaskResultImpl;


/**
 * This launcher is the class that will launch a native process.
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 0.9
 */
@ActiveObject
public class NativeTaskLauncher extends TaskLauncher {

    public static final Logger logger = Logger.getLogger(NativeTaskLauncher.class);

    private static final String SCRATCH_DATASPACE = "LOCALSPACE";
    private static final String SCRATCH_DATASPACE_TAG = "$" + SCRATCH_DATASPACE;
    private static final String USER_DATASPACE = "USERSPACE";
    private static final String USER_DATASPACE_TAG = "$" + USER_DATASPACE;
    private static final String GLOBAL_DATASPACE = "GLOBALSPACE";
    private static final String GLOBAL_DATASPACE_TAG = "$" + GLOBAL_DATASPACE;

    private static final String JAVA_CMD_VAR = "JAVA";
    private static final String JAVA_CMD_TAG = "$" + JAVA_CMD_VAR;
    private static final String JAVA_HOME_VAR = "JAVA_HOME";
    private static final String JAVA_HOME_TAG = "$" + JAVA_HOME_VAR;
    private static final String PROACTIVE_HOME_VAR = "PROACTIVE_HOME";
    private static final String PROACTIVE_HOME_TAG = "$" + PROACTIVE_HOME_VAR;
    private static String PROACTIVE_HOME = null;
    private static String JAVA_CMD = null;

    static {
        JAVA_CMD = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java" +
            ((OperatingSystem.getOperatingSystem() == OperatingSystem.windows) ? ".exe" : "");
    }

    // SCHEDULING-988 : only way to pass nodes to a runAsMe mutlinodes native task.
    /** Tag for identifying in command line tmp file that contains allocated resource for a multi-node task */
    private static final String NODESFILE_TAG = "$NODESFILE";
    /** Tag for identifying in command line the number of resources allocated to a multi-node task */
    private static final String NODESNUMBER_TAG = "$NODESNUMBER";

    /**
     * ProActive Empty Constructor
     */
    public NativeTaskLauncher() {
    }

    /**
     * Constructor of the native task launcher.
     * CONSTRUCTOR USED BY THE SCHEDULER CORE : do not remove.
     *
     * @param initializer represents the class that contains information to initialize this task launcher.
     */
    public NativeTaskLauncher(TaskLauncherInitializer initializer) {
        super(initializer);
    }

    /**
     * Execute the user task as an active object.
     *
     * @param core The scheduler core to be notify
     * @param executableContainer contains the task to execute
     * @param results the possible results from parent tasks.(if task flow)
     * @return a task result representing the result of this task execution.
     */
    @Override
    public void doTask(TaskTerminateNotification core, ExecutableContainer executableContainer,
            TaskResult... results) {
        long duration = -1;
        long sample = 0;
        long intervalms = 0;
        // Executable result (res or ex)
        Throwable exception = null;
        Serializable userResult = null;
        // TaskResult produced by doTask
        TaskResultImpl res;
        try {
            initProActiveHome();
            //init dataspace
            initDataSpaces();
            replaceTagsInDataspaces();

            sample = System.nanoTime();
            //copy datas from OUTPUT or INPUT to local scratch
            copyInputDataToScratch();
            intervalms = Math.round(Math.ceil((System.nanoTime() - sample) / 1000));
            logger.info("Time spent copying INPUT datas to SCRATCH : " + intervalms + " ms");

            if (!hasBeenKilled) {
                // set exported vars
                this.setPropagatedProperties(results);

                //get Executable before schedule timer
                currentExecutable = executableContainer.getExecutable();
                //start walltime if needed
                if (isWallTime()) {
                    scheduleTimer();
                }
            }

            //execute pre-script
            if (!hasBeenKilled && pre != null) {
                sample = System.nanoTime();
                this.executePreScript();
                duration = System.nanoTime() - sample;
            }

            if (!hasBeenKilled) {
                //init task
                ExecutableInitializer execInit = executableContainer.createExecutableInitializer();
                replaceWorkingDirPathsTags(execInit);
                replaceWorkingDirDSAllTags(execInit);
                replaceTagsInScript(((NativeExecutableInitializer) execInit).getGenerationScript());
                //decrypt credentials if needed
                if (executableContainer.isRunAsUser()) {
                    decrypter.setCredentials(executableContainer.getCredentials());
                    execInit.setDecrypter(decrypter);
                }
                // if an exception occurs in init method, unwrapp the InvocationTargetException
                // the result of the execution is the user level exception
                try {
                    callInternalInit(NativeExecutable.class, NativeExecutableInitializer.class, execInit);
                } catch (InvocationTargetException e) {
                    throw e.getCause() != null ? e.getCause() : e;
                }

                replaceIterationTags(execInit);
                // replace the JAVA tags in command
                replaceCommandPathsTags(execInit);
                //replace dataspace tags in command (if needed) by local scratch directory
                replaceCommandDSTags();
                // pass the nodesfile as parameter if needed...
                replaceCommandNodesInfosTags();

                addsDataSpaceEnvVar();

                // The JAVA env variable
                ((NativeExecutable) currentExecutable).addToEnvironmentVariables(JAVA_CMD_VAR, JAVA_CMD);
                ((NativeExecutable) currentExecutable).addToEnvironmentVariables(JAVA_HOME_VAR, System
                        .getProperty("java.home"));
                ((NativeExecutable) currentExecutable).addToEnvironmentVariables(PROACTIVE_HOME_VAR,
                        PROACTIVE_HOME);

                sample = System.nanoTime();
                try {
                    //launch task
                    logger.debug("Starting execution of task '" + taskId + "'");
                    userResult = currentExecutable.execute(results);
                } catch (Throwable t) {
                    exception = t;
                }
                duration += System.nanoTime() - sample;

            }

            //for the next two steps, task could be killed anywhere
            if (!hasBeenKilled && post != null) {
                sample = System.nanoTime();
                int retCode = -1;//< 0 means exception in the command itself (ie. command not found)
                if (userResult != null) {
                    retCode = Integer.parseInt(userResult.toString());
                }
                //launch post script
                this.executePostScript(retCode == 0 && exception == null);
                duration += System.nanoTime() - sample;
            }

            if (!hasBeenKilled) {
                sample = System.nanoTime();
                //copy output file
                copyScratchDataToOutput();
                intervalms = Math.round(Math.ceil((System.nanoTime() - sample) / 1000));
                logger.info("Time spent copying SCRATCH data to OUTPUT : " + intervalms + " ms");
            }
        } catch (Throwable ex) {
            logger.debug("Exception occured while running task " + this.taskId + ": ", ex);
            exception = ex;
            userResult = null;
        } finally {
            if (!hasBeenKilled) {
                // set the result
                if (exception != null) {
                    res = new TaskResultImpl(taskId, exception, null, duration / 1000000, null);
                } else {
                    res = new TaskResultImpl(taskId, userResult, null, duration / 1000000, null);
                }
                try {
                    // logs have to be retrieved after flowscript exec if any
                    if (flow != null) {
                        // *WARNING* : flow action is set in res UNLESS an exception is thrown !
                        // see FlowAction.getDefaultAction()
                        this.executeFlowScript(res);
                    }
                } catch (Throwable e) {
                    // task result is now the exception thrown by flowscript
                    // flowaction is set to default
                    res = new TaskResultImpl(taskId, e, null, duration / 1000000, null);
                    // action is set to default as the script was not evaluated
                    res.setAction(FlowAction.getDefaultAction(this.flow));
                }
                res.setPropagatedProperties(retreivePropagatedProperties());
                res.setLogs(this.getLogs());
            } else {
                res = null;
            }

            // kill all children processes

            killChildrenProcesses();

            // finalize task in any cases (killed or not)
            terminateDataSpace();
            if (isWallTime()) {
                cancelTimer();
            }
            this.finalizeTask(core, res);
        }
    }

    /**
     * Adds the dataspaces to the environment variables
     * @throws Exception
     */
    private void addsDataSpaceEnvVar() throws Exception {
        addDataSpaceEnvVar(SCRATCH, SCRATCH_DATASPACE);
        addDataSpaceEnvVar(GLOBAL, GLOBAL_DATASPACE);
        addDataSpaceEnvVar(USER, USER_DATASPACE);
    }

    private void initProActiveHome() {
        if (PROACTIVE_HOME == null) {
            if (CentralPAPropertyRepository.PA_HOME.isSet()) {

                PROACTIVE_HOME = CentralPAPropertyRepository.PA_HOME.getValue();
            } else {
                PROACTIVE_HOME = System.getProperty(CentralPAPropertyRepository.PA_HOME.getName());
            }

            if (PROACTIVE_HOME == null) {
                try {
                    PROACTIVE_HOME = ProActiveRuntimeImpl.getProActiveRuntime().getProActiveHome();
                } catch (ProActiveException e) {
                    throw new RuntimeException("Cannot find ProActive home", e);
                }
                PROACTIVE_HOME.replace("/", File.separator);

            }
            // cleaning the path
            try {
                PROACTIVE_HOME = (new File(PROACTIVE_HOME)).getCanonicalPath();
            } catch (IOException e) {
                throw new RuntimeException("Problem occurred when reading ProActive home", e);
            }

            System.setProperty(CentralPAPropertyRepository.PA_HOME.getName(), PROACTIVE_HOME);
        }

    }

    private void addDataSpaceEnvVar(DataSpacesFileObject fo, String varName) throws URISyntaxException {
        if (fo != null) {
            String foUri = (new URI(fo.getRealURI())).toString();
            if (foUri.startsWith("file:")) {
                foUri = (new File(new URI(foUri))).getAbsolutePath();
            }
            ((NativeExecutable) currentExecutable).addToEnvironmentVariables(varName, foUri);
        }
    }

    private void replaceWorkingDirDSAllTags(ExecutableInitializer execInit) throws Exception {
        replaceWorkingDirDSTag(execInit, SCRATCH_DATASPACE_TAG, SCRATCH);
        replaceWorkingDirDSTag(execInit, GLOBAL_DATASPACE_TAG, GLOBAL);
        replaceWorkingDirDSTag(execInit, USER_DATASPACE_TAG, USER);
    }

    private void replaceWorkingDirDSTag(ExecutableInitializer execInit, String tag, DataSpacesFileObject fo)
            throws URISyntaxException, DataSpacesException {
        String wd = ((NativeExecutableInitializer) execInit).getWorkingDir();
        if (wd != null && wd.contains(tag)) {
            if (fo != null) {
                if (!fo.getRealURI().startsWith("file://")) {
                    throw new DataSpacesException(tag + " in workingDir cannot be replaced, Space " +
                        fo.getRealURI() + " is not accessible via the file system.");
                }
                String fullPath = new File(new URI(fo.getRealURI())).getAbsolutePath();
                wd = wd.replace(tag, fullPath);
                ((NativeExecutableInitializer) execInit).setWorkingDir(wd);
            } else {
                throw new DataSpacesException(tag +
                    " in workingDir cannot be replaced : dataspaces configuration have failed (see task logs).");
            }
        }
    }

    private void replaceWorkingDirPathsTags(ExecutableInitializer execInit) throws Exception {
        String wd = ((NativeExecutableInitializer) execInit).getWorkingDir();
        if (wd != null) {
            wd = wd.replace(JAVA_HOME_TAG, System.getProperty("java.home"));
            wd = wd.replace(JAVA_CMD_TAG, JAVA_CMD);
            wd = wd.replace(PROACTIVE_HOME_TAG, PROACTIVE_HOME);
            ((NativeExecutableInitializer) execInit).setWorkingDir(wd);
        }
    }

    private void replaceCommandDSTags() throws Exception {
        String[] cmdElements = ((NativeExecutable) currentExecutable).getCommand();

        for (int i = 0; i < cmdElements.length; i++) {
            cmdElements[i] = replaceCommandDSTag(cmdElements[i], SCRATCH_DATASPACE_TAG, SCRATCH);
            cmdElements[i] = replaceCommandDSTag(cmdElements[i], GLOBAL_DATASPACE_TAG, GLOBAL);
            cmdElements[i] = replaceCommandDSTag(cmdElements[i], USER_DATASPACE_TAG, USER);
        }
    }

    private String replaceCommandDSTag(String commandElem, String tag, DataSpacesFileObject fo)
            throws Exception {
        if (commandElem.contains(tag)) {
            if (fo != null) {
                String fullPath = null;
                if (fo.getRealURI().startsWith("file:")) {
                    fullPath = new File(new URI(fo.getRealURI())).getAbsolutePath();
                } else {
                    fullPath = fo.getRealURI();
                }
                commandElem = commandElem.replace(tag, fullPath);
            } else {
                throw new DataSpacesException(tag +
                    " in command cannot be replaced : dataspaces configuration have failed (see task logs).");
            }
        }
        return commandElem;
    }

    private void replaceCommandPathsTags(ExecutableInitializer execInit) throws Exception {
        String[] cmdElements = ((NativeExecutable) currentExecutable).getCommand();

        for (int i = 0; i < cmdElements.length; i++) {
            cmdElements[i] = cmdElements[i].replace(JAVA_HOME_TAG, System.getProperty("java.home"));
            cmdElements[i] = cmdElements[i].replace(JAVA_CMD_TAG, JAVA_CMD);
            cmdElements[i] = cmdElements[i].replace(PROACTIVE_HOME_TAG, PROACTIVE_HOME);
        }
    }

    // SCHEDULING-988 : only way to pass nodes to a runAsMe mutlinodes native task.
    private void replaceCommandNodesInfosTags() throws Exception {
        NativeExecutable ne = ((NativeExecutable) currentExecutable);
        String[] cmdElements = ne.getCommand();

        // check if this replacement is actually needed
        boolean needed = false;
        for (int i = 0; i < cmdElements.length; i++) {
            if (cmdElements[i].contains(NODESFILE_TAG) || cmdElements[i].contains(NODESNUMBER_TAG)) {
                needed = true;
                break;
            }
        }

        if (needed) {
            // Arrrgl... don't want to change APIs now, to late.
            // to be implemented a little bit less ugly
            Field nodesfileField = ne.getClass().getDeclaredField("nodesFiles");
            Field nodesNumberField = ne.getClass().getDeclaredField("nodesNumber");
            nodesfileField.setAccessible(true);
            nodesNumberField.setAccessible(true);
            File nodesFile = (File) (nodesfileField.get(ne));
            int nodesNumber = nodesNumberField.getInt(ne);
            String nodesFilePath = nodesFile != null ? nodesFile.getAbsolutePath() : "";
            // no side effect hack : do the replacement even if no path is available
            for (int i = 0; i < cmdElements.length; i++) {
                cmdElements[i] = cmdElements[i].replace(NODESFILE_TAG, nodesFilePath);
                cmdElements[i] = cmdElements[i].replace(NODESNUMBER_TAG, "" + nodesNumber);
            }
        }
    }

    /**
     * Replaces iteration and replication index syntactic macros
     * in various string locations across the task descriptor
     * 
     * @param init the executable initializer containing the native command
     */
    protected void replaceIterationTags(ExecutableInitializer init) {
        NativeExecutable ne = (NativeExecutable) currentExecutable;
        String[] cmd = ne.getCommand();
        if (cmd != null) {
            for (int i = 0; i < cmd.length; i++) {
                cmd[i] = cmd[i].replace(ITERATION_INDEX_TAG, "" + this.iterationIndex);
                cmd[i] = cmd[i].replace(REPLICATION_INDEX_TAG, "" + this.replicationIndex);
            }
        }
    }

}
