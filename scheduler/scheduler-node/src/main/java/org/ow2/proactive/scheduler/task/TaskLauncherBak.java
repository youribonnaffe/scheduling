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
package org.ow2.proactive.scheduler.task;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.Body;
import org.objectweb.proactive.InitActive;
import org.objectweb.proactive.annotation.ImmediateService;
import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.core.config.CentralPAPropertyRepository;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeException;
import org.objectweb.proactive.core.util.ProActiveInet;
import org.objectweb.proactive.extensions.dataspaces.api.DataSpacesFileObject;
import org.objectweb.proactive.extensions.dataspaces.api.PADataSpaces;
import org.objectweb.proactive.extensions.dataspaces.core.DataSpacesNodes;
import org.objectweb.proactive.extensions.dataspaces.core.naming.NamingService;
import org.objectweb.proactive.extensions.dataspaces.exceptions.DataSpacesException;
import org.objectweb.proactive.extensions.dataspaces.exceptions.FileSystemException;
import org.objectweb.proactive.extensions.dataspaces.vfs.selector.fast.FastFileSelector;
import org.objectweb.proactive.extensions.dataspaces.vfs.selector.fast.FastSelector;
import org.objectweb.proactive.utils.NamedThreadFactory;
import org.objectweb.proactive.utils.StackTraceUtil;
import org.ow2.proactive.db.types.BigString;
import org.ow2.proactive.resourcemanager.core.properties.PAResourceManagerProperties;
import org.ow2.proactive.scheduler.common.SchedulerConstants;
import org.ow2.proactive.scheduler.common.TaskTerminateNotification;
import org.ow2.proactive.scheduler.common.exception.UserException;
import org.ow2.proactive.scheduler.common.task.Decrypter;
import org.ow2.proactive.scheduler.common.task.Log4JTaskLogs;
import org.ow2.proactive.scheduler.common.task.TaskId;
import org.ow2.proactive.scheduler.common.task.TaskLogs;
import org.ow2.proactive.scheduler.common.task.TaskResult;
import org.ow2.proactive.scheduler.common.task.dataspaces.FileSelector;
import org.ow2.proactive.scheduler.common.task.dataspaces.InputSelector;
import org.ow2.proactive.scheduler.common.task.dataspaces.OutputAccessMode;
import org.ow2.proactive.scheduler.common.task.dataspaces.OutputSelector;
import org.ow2.proactive.scheduler.common.task.executable.Executable;
import org.ow2.proactive.scheduler.common.task.executable.internal.ExecutableInitializer;
import org.ow2.proactive.scheduler.common.task.flow.FlowAction;
import org.ow2.proactive.scheduler.common.task.flow.FlowScript;
import org.ow2.proactive.scheduler.common.util.logforwarder.AppenderProvider;
import org.ow2.proactive.scheduler.common.util.logforwarder.LogForwardingException;
import org.ow2.proactive.scheduler.common.util.logforwarder.appenders.AsyncAppenderWithStorage;
import org.ow2.proactive.scheduler.common.util.logforwarder.util.LoggingOutputStream;
import org.ow2.proactive.scheduler.core.properties.PASchedulerProperties;
import org.ow2.proactive.scheduler.exception.IllegalProgressException;
import org.ow2.proactive.scheduler.exception.ProgressPingerException;
import org.ow2.proactive.scheduler.task.utils.Guard;
import org.ow2.proactive.scheduler.task.utils.KillTask;
import org.ow2.proactive.scheduler.task.utils.LocalSpaceAdapter;
import org.ow2.proactive.scheduler.task.utils.RemoteSpaceAdapter;
import org.ow2.proactive.scripting.PropertyUtils;
import org.ow2.proactive.scripting.Script;
import org.ow2.proactive.scripting.ScriptHandler;
import org.ow2.proactive.scripting.ScriptLoader;
import org.ow2.proactive.scripting.ScriptResult;
import org.ow2.proactive.utils.Formatter;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

import static org.ow2.proactive.scheduler.common.task.util.SerializationUtil.deserializeVariableMap;
import static org.ow2.proactive.scheduler.common.task.util.SerializationUtil.serializeVariableMap;
import static org.ow2.proactive.scheduler.common.util.VariablesUtil.filterAndUpdate;


/**
 * Abstract Task Launcher.
 * This is the most simple task launcher.
 * It is able to launch a java task only.
 * You can extend this launcher in order to create a specific launcher.
 * With this default launcher, you can get the node on which the task is running and kill the task.
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 0.9
 */
public abstract class TaskLauncherBak implements InitActive {

    public static final Logger logger = Logger.getLogger(TaskLauncherBak.class);

    //Scratch dir property : we cannot take the key property from DataSpaceNodeConfigurationAgent class in RM.
    //we should not depend from RM package in this class.
    public static final String NODE_DATASPACE_SCRATCHDIR = "node.dataspace.scratchdir";

    public static final long CLEAN_TIMEOUT = 21 * 1000; // timeout used to control max time for the cleaning operation

    private boolean dataspaceInitialized = false;

    public static final String EXECUTION_SUCCEED_BINDING_NAME = "success";
    public static final String DS_SCRATCH_BINDING_NAME = "localspace";
    public static final String DS_INPUT_BINDING_NAME = "input";
    public static final String DS_OUTPUT_BINDING_NAME = "output";
    public static final String DS_GLOBAL_BINDING_NAME = "global";
    public static final String DS_USER_BINDING_NAME = "user";

    public static final String MULTI_NODE_TASK_NODESET_BINDING_NAME = "nodeset";
    public static final String MULTI_NODE_TASK_NODESURL_BINDING_NAME = "nodesurl";

    /** The name used to access propagated variables map */
    public static final String VARIABLES_BINDING_NAME = "variables";

    // keep last 1024 lines ot job output by default
    private static final int KEY_SIZE = 1024;

    // to define the max line number of a task logs
    // DO NOT USE SCHEDULER PROPERTY FOR THAT (those properties are not available on node side)
    @Deprecated
    public static final String OLD_MAX_LOG_SIZE_PROPERTY = "proactive.scheduler.logs.maxsize";
    public static final String MAX_LOG_SIZE_PROPERTY = "pas.launcher.logs.maxsize";

    // default log size, counted in number of log events
    public static final int DEFAULT_LOG_MAX_SIZE = 1024;

    // the prefix for log file produced in localspace
    public static final String LOG_FILE_PREFIX = "TaskLogs";

    protected DataSpacesFileObject SCRATCH = null;
    protected DataSpacesFileObject INPUT = null;
    protected DataSpacesFileObject OUTPUT = null;
    protected DataSpacesFileObject GLOBAL = null;
    protected DataSpacesFileObject USER = null;

    protected NamingService namingService = null;
    protected List<InputSelector> inputFiles;
    protected List<OutputSelector> outputFiles;

    // buffered string to store logs destined to the client (for e.g. datapspaces error/warn messages)
    private StringBuffer clientLogs;

    protected Decrypter decrypter = null;

    /**
     * Thread pool used for input/output files parallel transfer
     */
    protected ExecutorService executorTransfer = Executors.newFixedThreadPool(5, new NamedThreadFactory(
        "FileTransferThreadPool"));

    private int pingPeriodMs = 20000; // ms
    private int pingAttempts = 1;

    protected TaskId taskId;
    protected Script<?> pre;
    protected Script<?> post;
    protected FlowScript flow;

    /** replication index: task was replicated in parallel */
    protected int replicationIndex = 0;
    /** iteration index: task was replicated sequentially */
    protected int iterationIndex = 0;

    // handle streams
    protected transient PrintStream outputSink;
    protected transient PrintStream errorSink;

    // default appender for log storage
    protected transient AsyncAppenderWithStorage logAppender;
    // if true, store logs in a file in LOCALSPACE
    @Deprecated
    protected boolean storeLogs;

    protected String logFileName;

    protected ExecutableGuard executableGuard = new ExecutableGuard();

    protected volatile TaskLauncherBak stubOnThis;
    protected volatile Body taskLauncherBody;

    // true if finalizeLoggers has been called
    private final AtomicBoolean loggersFinalized = new AtomicBoolean(false);
    // true if loggers are currently activated
    private final AtomicBoolean loggersActivated = new AtomicBoolean(false);

    /** Maximum execution time of the task (in milliseconds), the variable is only valid if isWallTime is true */
    protected long wallTime = 0;

    /** The timer that will terminate the launcher if the task doesn't finish before the walltime */
    protected KillTask killTaskTimer = null;

    /** Will be replaced in file paths by the task's iteration index */
    protected static final String ITERATION_INDEX_TAG = "$IT";

    /** Will be replaced in file paths by the task's replication index */
    protected static final String REPLICATION_INDEX_TAG = "$REP";

    /** Will be replaced in file paths by the job id */
    protected static final String JOBID_INDEX_TAG = "$JID";

    /**
     * Will be replaced by the matching third-party credential
     * Example: if one of the third-party credentials' key-value pairs is 'foo:bar',
     * then '$CREDENTIALS_foo' will be replaced by 'bar' in the arguments of the tasks.
     */
    public static final String CREDENTIALS_KEY_PREFIX = "$CREDENTIALS_";

    /** Propagated variables map */
    private Map<String, Serializable> propagatedVariables = new HashMap<String, Serializable>();

    /**
     * ProActive empty constructor.
     */
    public TaskLauncherBak() {
    }

    /**
     * Constructor with task identification.
     * CONSTRUCTOR USED BY THE SCHEDULER CORE : do not remove.
     *
     * @param initializer represents the class that contains information to initialize every task launcher.
     */
    public TaskLauncherBak(TaskLauncherInitializer initializer) {
        this.taskId = initializer.getTaskId();
        this.pre = initializer.getPreScript();
        this.post = initializer.getPostScript();
        this.flow = initializer.getControlFlowScript();
        if (initializer.getWalltime() > 0) {
            this.wallTime = initializer.getWalltime();
        }
        //keep input/output files descriptor in memory for further copy
        this.inputFiles = initializer.getTaskInputFiles();
        this.outputFiles = initializer.getTaskOutputFiles();
        this.namingService = initializer.getNamingService();
        this.replicationIndex = initializer.getReplicationIndex();
        this.iterationIndex = initializer.getIterationIndex();
        this.storeLogs = initializer.isPreciousLogs();
        this.clientLogs = new StringBuffer();

        // add job descriptor variables
        if (initializer.getVariables() != null) {
            this.propagatedVariables.putAll(initializer.getVariables());
        }

        this.pingAttempts = initializer.getPingAttempts();
        this.pingPeriodMs = initializer.getPingPeriod() * 1000;

        this.init();
    }

    protected static String replace(String input, Map<String, String> replacements) {
        String output = input;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            output = output.replace(replacement.getKey(), replacement.getValue());
        }
        return output;
    }

    private void init() {

        // plug stdout/err into a socketAppender
        this.initLoggers();

        // set scheduler defined env variables
        this.initEnv();

        logger.debug("TaskLauncher initialized for task " + taskId + " (" + taskId.getReadableName() + ")");
    }

    public void initActivity(Body body) {
        Node node;
        try {
            node = PAActiveObject.getNode();
            stubOnThis = (TaskLauncherBak) PAActiveObject.getStubOnThis();
            taskLauncherBody = PAActiveObject.getBodyOnThis();
            executableGuard.setNode(node);
        } catch (Exception e) {
            throw new IllegalStateException("Could not retrieve ProActive Node", e);

        }
    }

    /**
     * Call the internal init private method on the current executable using the given argument.<br>
     * This method first get the method "internalInit" (let's call it 'm') of the given <b>targetedClass</b>.<br>
     * <b>parameterType</b> is the type of the argument to find the internal init method.<br>
     * Then the targeted method of 'm' is switched to accessible, finally 'm' is invoked on the current executable
     * with the given <b>argument</b>.
     *
     * @param targetedClass the class on which to look for the private 'internal init' method
     * @param parameterType the type of the parameter describing the definition of the method to look for.
     * @param argument the argument passed to the invocation of the found method on the current executable.
     */
    protected void callInternalInit(Class<?> targetedClass, Class<?> parameterType,
            ExecutableInitializer argument) throws InvocationTargetException, NoSuchMethodException,
            IllegalAccessException {
        Method m = targetedClass.getDeclaredMethod("internalInit", parameterType);
        m.setAccessible(true);

        m.invoke(executableGuard.use(), argument);
    }

    /**
     * Generate a couple of key and return the public one
     *
     * @return the generated public key
     * @throws java.security.NoSuchAlgorithmException if RSA is unknown
     */
    public PublicKey generatePublicKey() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen;
        keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(KEY_SIZE, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();
        //connect to the authentication interface and ask for new cred
        decrypter = new Decrypter(keyPair.getPrivate());
        return keyPair.getPublic();
    }

    /**
     * Execute the user task as an active object.
     *
     * @param core The scheduler core to be notify
     * @param execContainer contains the user defined executable to execute
     * @param results the possible results from parent tasks.(if task flow)
     * @return a task result representing the result of this task execution.
     */
    public abstract void doTask(TaskTerminateNotification core, ExecutableContainer execContainer,
            TaskResult... results);

    /**
     * Redirect stdout/err in the buffered appender.
     */
    @SuppressWarnings("unchecked")
    private void initLoggers() {
        logger.debug("Init loggers");
        // error about log should not be logged
        LogLog.setQuietMode(true);
        // create logger
        Logger l = Logger.getLogger(Log4JTaskLogs.JOB_LOGGER_PREFIX + this.taskId.getJobId() + "." +
            taskId.value());
        l.setAdditivity(false);
        MDC.put(Log4JTaskLogs.MDC_TASK_ID, this.taskId.value());
        MDC.put(Log4JTaskLogs.MDC_TASK_NAME, this.taskId.getReadableName());
        MDC.put(Log4JTaskLogs.MDC_HOST, getHostname());
        l.removeAllAppenders();
        // create an async appender for multiplexing (storage plus redirect through socketAppender)
        String logMaxSizeProp = System.getProperty(TaskLauncherBak.MAX_LOG_SIZE_PROPERTY);
        if (logMaxSizeProp == null) {
            logMaxSizeProp = System.getProperty(TaskLauncherBak.OLD_MAX_LOG_SIZE_PROPERTY);
        }
        if (logMaxSizeProp == null || "".equals(logMaxSizeProp.trim())) {
            this.logAppender = new AsyncAppenderWithStorage(TaskLauncherBak.DEFAULT_LOG_MAX_SIZE);
        } else {
            try {
                int logMaxSize = Integer.parseInt(logMaxSizeProp);
                this.logAppender = new AsyncAppenderWithStorage(logMaxSize);
                logger.info("Logs are limited to " + logMaxSize + " lines for task " + this.taskId);
            } catch (NumberFormatException e) {
                logger.warn(MAX_LOG_SIZE_PROPERTY +
                    " property is not correctly defined. Logs size is bounded to default value " +
                    TaskLauncherBak.DEFAULT_LOG_MAX_SIZE + " for task " + this.taskId, e);
                this.logAppender = new AsyncAppenderWithStorage(TaskLauncherBak.DEFAULT_LOG_MAX_SIZE);
            }
        }
        l.addAppender(this.logAppender);

        // redirect stdout and err
        this.outputSink = new PrintStream(new LoggingOutputStream(l, Log4JTaskLogs.STDOUT_LEVEL), true);
        this.errorSink = new PrintStream(new LoggingOutputStream(l, Log4JTaskLogs.STDERR_LEVEL), true);

        if (isForkedTask()) {
            // output will be picked by forker (ThreadReader)
            this.outputSink = System.out;
            this.errorSink = System.err;
        }
    }

    /**
     * Create log file in $LOCALSPACE.
     * @throws java.io.IOException if the file cannot be created.
     */
    private void initLocalLogsFile() throws IOException {
        logFileName = TaskLauncherBak.LOG_FILE_PREFIX + "-" + this.taskId.getJobId() + "-" +
            this.taskId.value() + ".log";
        // if IS_FORKED is set, it means that the forker task has already created a log file,
        // and we just append to it
        if (!isForkedTask()) {
            DataSpacesFileObject outlog = SCRATCH.resolveFile(TaskLauncherBak.LOG_FILE_PREFIX + "-" +
                this.taskId.getJobId() + "-" + this.taskId.value() + ".log");
            String outPath;
            try {
                outPath = convertDataSpaceToFileIfPossible(outlog, true);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }

            File outFile = new File(outPath);
            outFile.createNewFile();
            outFile.setWritable(true, false);

            // fileAppender constructor needs a path and not a URI.
            FileAppender fap = new FileAppender(Log4JTaskLogs.getTaskLogLayout(), outPath, false);
            this.logAppender.addAppender(fap);
        }
    }

    /**
     * Set scheduler related variables for the current task.
     */
    protected void initEnv() {
        if (isForkedTask()) {
            // set task vars
            System.setProperty(SchedulerVars.JAVAENV_JOB_ID_VARNAME.toString(), this.taskId.getJobId()
                    .value());
            System.setProperty(SchedulerVars.JAVAENV_JOB_NAME_VARNAME.toString(), this.taskId.getJobId()
                    .getReadableName());
            System.setProperty(SchedulerVars.JAVAENV_TASK_ID_VARNAME.toString(), this.taskId.value());
            System.setProperty(SchedulerVars.JAVAENV_TASK_NAME_VARNAME.toString(), this.taskId
                    .getReadableName());
            System.setProperty(SchedulerVars.JAVAENV_TASK_ITERATION.toString(), "" + this.iterationIndex);
            System.setProperty(SchedulerVars.JAVAENV_TASK_REPLICATION.toString(), "" + this.replicationIndex);
            System.setProperty(PASchedulerProperties.SCHEDULER_HOME.getKey(),
                    CentralPAPropertyRepository.PA_HOME.getValue());
        }
    }

    /**
     * Set as Java Property all the properties that comes with incoming results, i.e.
     * properties that have been propagated in parent tasks.
     * @see org.ow2.proactive.scripting.PropertyUtils
     */
    protected void setPropagatedProperties(TaskResult[] incomingResults) {
        if (isForkedTask()) {
            for (TaskResult incomingResult : incomingResults) {
                Map<String, String> properties = incomingResult.getPropagatedProperties();
                if (properties != null) {
                    logger.info("Incoming properties for task " + this.taskId + " are " + properties);
                    for (String key : properties.keySet()) {
                        logger.debug("Value of Incoming property " + key + " is " + properties.get(key));
                        System.setProperty(key, properties.get(key));
                    }
                } else {
                    logger.info("No Incoming properties for task " + this.taskId);
                }
            }
        }
    }

    Map<String, byte[]> getPropagatedVariables(TaskResult[] incomingResults) {
        Map<String, byte[]> variableMap = new HashMap<String, byte[]>();
        for (TaskResult result : incomingResults) {
            if (result.getPropagatedVariables() != null) {
                variableMap.putAll(result.getPropagatedVariables());
            }
        }
        return variableMap;
    }

    /**
     * Extract name and value of all the properties that have been propagated during the execution
     * of this task launcher (on scripts and executable).
     * @see org.ow2.proactive.scripting.PropertyUtils
     * @return a map that contains [name->value] of all propagated properties.
     */
    protected Map<String, BigString> retreivePropagatedProperties() {
        // get all names of propagated vars
        String allVars = System.getProperty(PropertyUtils.PROPAGATED_PROPERTIES_VAR_NAME);
        if (allVars != null) {
            logger.debug("Propagated properties for task " + this.taskId + " are : " + allVars);
            StringTokenizer parser = new StringTokenizer(allVars, PropertyUtils.VARS_VAR_SEPARATOR);
            Map<String, BigString> exportedVars = new Hashtable<String, BigString>();
            while (parser.hasMoreTokens()) {
                String key = parser.nextToken();
                String value = System.getProperty(key);
                if (value != null) {
                    logger.debug("Value of Propagated property " + key + " is " + value);
                    exportedVars.put(key, new BigString(value));
                    System.clearProperty(key);
                } else {
                    logger.warn("Propagated property " + key + " is not set !");
                }
            }
            System.clearProperty(PropertyUtils.PROPAGATED_PROPERTIES_VAR_NAME);
            return exportedVars;
        } else {
            logger.debug("No Propagated properties for task " + this.taskId);
            return null;
        }
    }

    public ExecutableInitializer createExecutableInitializer(ExecutableContainer executableContainer) {
        ExecutableInitializer initializer = executableContainer.createExecutableInitializer();

        initializer.setLocalSpaceFileObject(SCRATCH);
        initializer.setInputSpaceFileObject(INPUT);
        initializer.setOutputSpaceFileObject(OUTPUT);
        initializer.setGlobalSpaceFileObject(GLOBAL);
        initializer.setUserSpaceFileObject(USER);
        initializer.setLocalSpace(new LocalSpaceAdapter(SCRATCH));
        initializer.setInputSpace(new RemoteSpaceAdapter(INPUT, SCRATCH));
        initializer.setOutputSpace(new RemoteSpaceAdapter(OUTPUT, SCRATCH));
        initializer.setGlobalSpace(new RemoteSpaceAdapter(GLOBAL, SCRATCH));
        initializer.setUserSpace(new RemoteSpaceAdapter(USER, SCRATCH));
        initializer.setOutputSink(outputSink);
        initializer.setErrorSink(errorSink);

        return initializer;
    }

    @ImmediateService
    public void getStoredLogs(AppenderProvider logSink) {
        resetLogContextForImmediateService();
        Appender appender;
        try {
            appender = logSink.getAppender();
        } catch (LogForwardingException e) {
            logger.error("Cannot create log appender.", e);
            return;
        }
        this.logAppender.appendStoredEvents(appender);
    }

    /**
     * Activate the logs on this host and port.
     * @param logSink the provider for the appender to write in.
     */
    @SuppressWarnings("unchecked")
    @ImmediateService
    public void activateLogs(AppenderProvider logSink) {
        synchronized (this.loggersFinalized) {
            resetLogContextForImmediateService();

            logger.info("Activating logs for task " + this.taskId + " (" + taskId.getReadableName() + ")");
            if (this.loggersActivated.get()) {
                logger.info("Logs for task " + this.taskId + " are already activated");
                return;
            }
            this.loggersActivated.set(true);

            // create appender
            Appender appender;
            try {
                appender = logSink.getAppender();
            } catch (LogForwardingException e) {
                logger.error("Cannot create log appender.", e);
                return;
            }
            // fill appender
            if (!this.loggersFinalized.get()) {
                this.logAppender.addAppender(appender);
            } else {
                logger.info("Logs for task " + this.taskId + " are closed. Flushing buffer...");
                // Everything is closed: reopen and close...
                for (LoggingEvent e : this.logAppender.getStorage()) {
                    appender.doAppend(e);
                }
                appender.close();
                this.loggersActivated.set(false);
                return;
            }
            logger.info("Activated logs for task " + this.taskId);
        }
    }

    // need to reset MDC because calling thread is not active thread (immediate service)
    protected void resetLogContextForImmediateService() {
        MDC.put(Log4JTaskLogs.MDC_TASK_ID, this.taskId.value());
        MDC.put(Log4JTaskLogs.MDC_TASK_NAME, this.taskId.getReadableName());
        MDC.put(Log4JTaskLogs.MDC_HOST, getHostname());
    }

    /**
     * Flush out and err streams.
     */
    protected void flushStreams() {
        if (this.outputSink != null) {
            this.outputSink.flush();
        }
        if (this.errorSink != null) {
            this.errorSink.flush();
        }
    }

    /**
     * Return a TaskLogs object that contains the logs produced by the executed tasks
     *
     * @return a TaskLogs object that contains the logs produced by the executed tasks
     */
    @ImmediateService
    public TaskLogs getLogs() {
        resetLogContextForImmediateService();
        this.flushStreams();
        return new Log4JTaskLogs(this.logAppender.getStorage(), this.taskId.getJobId().value());
    }

    /**
     * Return the latest progress value set by the launched executable.<br/>
     * If the value returned by user is not in [0:100], the closest bound (0 or 100) is returned.
     *
     * @return the latest progress value set by the launched executable.
     * @throws org.ow2.proactive.scheduler.exception.IllegalProgressException if the userExecutable.getProgress() method throws an exception
     */
    @ImmediateService
    public int getProgress() throws ProgressPingerException {
        resetLogContextForImmediateService();
        return executableGuard.getProgress();
    }

    /**
     * Execute the preScript on the local node.
     *
     * @throws org.objectweb.proactive.ActiveObjectCreationException if the script handler cannot be created
     * @throws org.objectweb.proactive.core.node.NodeException if the script handler cannot be created
     * @throws org.ow2.proactive.scheduler.common.exception.UserException if an error occurred during the execution of the script
     */
    @SuppressWarnings("unchecked")
    protected void executePreScript() throws ActiveObjectCreationException, NodeException, UserException {
        // update script variables with updated values
        filterAndUpdate(this.pre, this.propagatedVariables);
        replaceTagsInScript(pre);
        logger.info("Executing pre-script");
        ScriptHandler handler = ScriptLoader.createLocalHandler();
        setPropagatedVariableBinding(this.propagatedVariables, handler);
        this.addDataspaceBinding(handler);
        ScriptResult<String> res = handler.handle((Script<String>) pre, outputSink, errorSink);

        if (res.errorOccured()) {
            res.getException().printStackTrace();
            logger.error("Error on pre-script occured : ", res.getException());
            this.flushStreams();
            throw new UserException("Pre-script has failed on the current node", res.getException());
        }
        // flush prescript output
        this.flushStreams();
    }

    /**
     * Execute the postScript on the local node.
     *
     * @param executionSucceed a boolean describing the state of the task execution.(true if execution succeed, false if not)
     * @throws org.objectweb.proactive.ActiveObjectCreationException if the script handler cannot be created
     * @throws org.objectweb.proactive.core.node.NodeException if the script handler cannot be created
     * @throws org.ow2.proactive.scheduler.common.exception.UserException if an error occurred during the execution of the script
     */
    @SuppressWarnings("unchecked")
    protected void executePostScript(boolean executionSucceed) throws ActiveObjectCreationException,
            NodeException, UserException {
        // replace script variables with updated values
        filterAndUpdate(this.post, this.propagatedVariables);
        replaceTagsInScript(post);
        logger.info("Executing post-script");
        ScriptHandler handler = ScriptLoader.createLocalHandler();
        setPropagatedVariableBinding(this.propagatedVariables, handler);
        this.addDataspaceBinding(handler);
        handler.addBinding(EXECUTION_SUCCEED_BINDING_NAME, executionSucceed);
        ScriptResult<String> res = handler.handle((Script<String>) post, outputSink, errorSink);

        if (res.errorOccured()) {
            res.getException().printStackTrace();
            logger.error("Error on post-script occured : ", res.getException());
            throw new UserException("Post-script has failed on the current node", res.getException());
        }
        // flush postscript output
        this.flushStreams();
    }

    /**
     * Execute the control flow script on the local node and set the flow action in res.
     *
     * @param res TaskResult of this launcher's task, input of the script.
     * @throws Throwable if an exception occurred in the flow script. 
     *      TaskResult#setAction(FlowAction) will NOT be called on res 
     */
    protected void executeFlowScript(TaskResult res) throws Throwable {
        // replace script variables with updated values
        filterAndUpdate(this.flow, this.propagatedVariables);
        replaceTagsInScript(flow);
        logger.info("Executing flow-script");
        ScriptHandler handler = ScriptLoader.createLocalHandler();
        setPropagatedVariableBinding(this.propagatedVariables, handler);
        this.addDataspaceBinding(handler);
        handler.addBinding(FlowScript.resultVariable, res.value());
        ScriptResult<FlowAction> sRes = handler.handle(flow, outputSink, errorSink);
        this.flushStreams();

        if (sRes.errorOccured()) {
            Throwable ee = sRes.getException();
            if (ee != null) {
                // stacktraced on user logs
                ee.printStackTrace();
                logger.error("Error on flow-script occured : ", ee);
                throw new UserException("Flow-script has failed on the current node", ee);
            }
        } else {
            FlowAction action = sRes.getResult();
            ((TaskResultImpl) res).setAction(action);
        }
    }

    /**
     * Adds in the given ScriptHandler bindings for this Launcher's Dataspace handlers
     *
     * @param script the ScriptHandler in which bindings will be added
     */
    private void addDataspaceBinding(ScriptHandler script) {
        script.addBinding(DS_SCRATCH_BINDING_NAME, this.SCRATCH);
        script.addBinding(DS_INPUT_BINDING_NAME, this.INPUT);
        script.addBinding(DS_OUTPUT_BINDING_NAME, this.OUTPUT);
        script.addBinding(DS_GLOBAL_BINDING_NAME, this.GLOBAL);
        script.addBinding(DS_USER_BINDING_NAME, this.USER);
    }

    public static String convertDataSpaceToFileIfPossible(DataSpacesFileObject fo, boolean errorIfNotFile)
            throws URISyntaxException, DataSpacesException {
        URI foUri = new URI(fo.getRealURI());
        String answer;
        if (foUri.getScheme() == null || foUri.getScheme().equals("file")) {
            answer = (new File(foUri)).getAbsolutePath();
        } else {
            if (errorIfNotFile) {
                throw new DataSpacesException("Space " + fo.getRealURI() +
                    " is not accessible via the file system.");
            }
            answer = foUri.toString();
        }
        return answer;
    }

    /*
     * Sets the propagated variables map as a script binding called 'variables'.
     */
    private void setPropagatedVariableBinding(Map<String, Serializable> propagatedVariables,
            ScriptHandler scriptHandler) {
        scriptHandler.addBinding(VARIABLES_BINDING_NAME, propagatedVariables);
    }

    /**
     * Close scheduler task logger and reset stdout/err
     */
    protected void finalizeLoggers() {
        synchronized (this.loggersFinalized) {
            if (!loggersFinalized.get()) {
                logger.info("Terminating loggers for task " + this.taskId + " (" + taskId.getReadableName() +
                    ")" + "...");
                this.flushStreams();

                this.loggersFinalized.set(true);
                this.loggersActivated.set(false);
                //Unhandle loggers
                if (this.logAppender != null) {
                    this.logAppender.close();
                }
                logger.info("Terminated loggers for task " + this.taskId);
            }
        }
    }

    /**
     * Common final behavior for any type of task launcher.
     * @param terminateNotificationStub reference to the scheduler.
     */
    protected void finalizeTask(TaskTerminateNotification terminateNotificationStub, TaskResult taskResult) {
        logger.info("Finalizing task " + taskId);

        // clean the task launcher unless it's been done already by the kill mechanism
        executableGuard.clean(TaskLauncherBak.CLEAN_TIMEOUT);

        /*
         * send back the result (if the task was killed, the core is not accessible, but it is accessible
         * if the task was walltimed)
         */
        boolean notifiedScheduler = false;
        if (!executableGuard.wasKilled() || executableGuard.wasWalltimed()) {
            if (terminateNotificationStub != null) {
                // callback to scheduler core sending the result
                for (int i = 0; i < pingAttempts; i++) {
                    try {
                        terminateNotificationStub.terminate(taskId, taskResult);
                        logger.debug("Successfully set results of task " + taskId);
                        notifiedScheduler = true;
                        break;
                    } catch (Throwable th) {
                        logger.error("Cannot set results of task " + taskId, th);
                        try {
                            Thread.sleep(pingPeriodMs);
                        } catch (InterruptedException e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }
            }
        }

        if(!notifiedScheduler){
            logger.info("Failed to notify set results of task " + taskId + ", terminating task launcher now");
            terminate(false);
        }

        logger.info("Task " + taskId + " finalized");
    }

    /**
     * This method is called by the scheduler server to terminate the task launcher active object,
     * either via a kill message or a normal termination.
     */
    @ImmediateService
    public void terminate(boolean normalTermination) {
        resetLogContextForImmediateService();
        if (normalTermination) {
            logger.info("Terminate message received for task " + taskId);
        } else {
            logger.info("Kill message received for task " + taskId);
        }
        if (!normalTermination) {
            // If this is a kill message, perfom all cleaning normally done by the finalize method
            try {
                executableGuard.kill(false);

            } catch (Throwable e) {
                logger.warn("Exception occurred while executing kill on task " + taskId.value(), e);
            }

            executableGuard.clean(TaskLauncherBak.CLEAN_TIMEOUT);
        }
        try {
            if (taskLauncherBody != null) {
                taskLauncherBody.terminate(!normalTermination);
            }
        } catch (Exception e) {
            logger.info("Exception when terminating task launcher active object", e);
        }
        logger.info("TaskLauncher terminated");
    }

    /**
     * If user specified walltime for the particular task, the timer will be scheduled to kill the task
     * if it does not finish before the walltime. If it does finish before the walltime then the timer will be canceled
     */
    protected void scheduleTimer() {
        if (isWallTime() && !isForkedTask()) {
            logger.info("Execute timer because task '" + taskId + "' is walltimed (" + wallTime + " ms)");
            killTaskTimer = new KillTask(executableGuard, wallTime);
            killTaskTimer.schedule();
        }
    }

    protected boolean isForkedTask() {
        return false;
    }

    /**
     * Canceling timer for killing the task, if we cancel the timer this means that the task finished before the walltime
     */
    protected void cancelTimer() {
        if (isWallTime() && killTaskTimer != null) {
            killTaskTimer.cancel();
        }
    }

    /**
     * @return the isWallTime
     */
    public boolean isWallTime() {
        return wallTime > 0;
    }

    protected void initDataSpaces() {
        // configure node for application
        long id = getDataSpacesApplicationId();

        //prepare scratch, input, output
        try {
            DataSpacesNodes.configureApplication(PAActiveObject.getActiveObjectNode(PAActiveObject
                    .getStubOnThis()), id, namingService);

            SCRATCH = PADataSpaces.resolveScratchForAO();
            logger.info("SCRATCH space is " + SCRATCH.getRealURI());

            // create a log file in local space if the node is configured
            logger.info("logfile is enabled for task " + taskId);
            initLocalLogsFile();

        } catch (Throwable t) {
            logger.error("There was a problem while initializing dataSpaces, they are not activated", t);
            this.logDataspacesStatus(
                    "There was a problem while initializing dataSpaces, they are not activated",
                    DataspacesStatusLevel.ERROR);
            this.logDataspacesStatus(Formatter.stackTraceToString(t), DataspacesStatusLevel.ERROR);
            return;
        }

        try {
            INPUT = PADataSpaces.resolveDefaultInput();
            INPUT = resolveToExisting(INPUT, "INPUT", true);
            INPUT = createTaskIdFolder(INPUT, "INPUT");
        } catch (Throwable t) {
            logger.warn("INPUT space is disabled");
            logger.warn("", t);
            this.logDataspacesStatus("INPUT space is disabled", DataspacesStatusLevel.WARNING);
            this.logDataspacesStatus(Formatter.stackTraceToString(t), DataspacesStatusLevel.WARNING);
        }
        try {
            OUTPUT = PADataSpaces.resolveDefaultOutput();
            OUTPUT = resolveToExisting(OUTPUT, "OUTPUT", false);
            OUTPUT = createTaskIdFolder(OUTPUT, "OUTPUT");
        } catch (Throwable t) {
            logger.warn("OUTPUT space is disabled");
            logger.warn("", t);
            this.logDataspacesStatus("OUTPUT space is disabled", DataspacesStatusLevel.WARNING);
            this.logDataspacesStatus(Formatter.stackTraceToString(t), DataspacesStatusLevel.WARNING);
        }

        try {
            GLOBAL = PADataSpaces.resolveOutput(SchedulerConstants.GLOBALSPACE_NAME);
            GLOBAL = resolveToExisting(GLOBAL, "GLOBAL", false);
            GLOBAL = createTaskIdFolder(GLOBAL, "GLOBAL");
        } catch (Throwable t) {
            logger.warn("GLOBAL space is disabled");
            logger.warn("", t);
            this.logDataspacesStatus("GLOBAL space is disabled", DataspacesStatusLevel.WARNING);
            this.logDataspacesStatus(Formatter.stackTraceToString(t), DataspacesStatusLevel.WARNING);
        }
        try {
            USER = PADataSpaces.resolveOutput(SchedulerConstants.USERSPACE_NAME);
            USER = resolveToExisting(USER, "USER", false);
            USER = createTaskIdFolder(USER, "USER");
        } catch (Throwable t) {
            logger.warn("USER space is disabled");
            logger.warn("", t);
            this.logDataspacesStatus("USER space is disabled", DataspacesStatusLevel.WARNING);
            this.logDataspacesStatus(Formatter.stackTraceToString(t), DataspacesStatusLevel.WARNING);
        }
        dataspaceInitialized = true;
    }

    protected int getDataSpacesApplicationId() {
        return taskId.getJobId().hashCode();
    }

    protected DataSpacesFileObject resolveToExisting(DataSpacesFileObject space, String spaceName,
            boolean input) {
        if (space == null) {
            logger.info(spaceName + " space is disabled");
            return null;
        }
        // ensure that the remote folder exists (in case we didn't replace any pattern)
        try {
            space = space.ensureExistingOrSwitch(!input);
        } catch (Exception e) {
            logger.info("Error occurred when switching to alternate space root", e);
            logger.info(spaceName + " space is disabled");
            return null;
        }
        if (space == null) {
            logger.info("No existing " + spaceName + " space found");
            logger.info(spaceName + " space is disabled");
        } else {
            logger.info(spaceName + " space is " + space.getRealURI());
            logger.info("(other available urls for " + spaceName + " space are " + space.getAllRealURIs() +
                " )");
        }
        return space;
    }

    protected DataSpacesFileObject createTaskIdFolder(DataSpacesFileObject space, String spaceName) {
        if (space != null) {
            String realURI = space.getRealURI();
            // Look for the TASKID pattern at the end of the dataspace URI
            if (realURI.contains(SchedulerConstants.TASKID_DIR_DEFAULT_NAME)) {
                // resolve the taskid subfolder
                DataSpacesFileObject tidOutput;
                try {
                    tidOutput = space.resolveFile(taskId.toString());
                    // create this subfolder
                    tidOutput.createFolder();
                } catch (FileSystemException e) {
                    logger.info("Error when creating the TASKID folder in " + realURI, e);
                    logger.info(spaceName + " space is disabled");
                    return null;
                }
                // assign it to the space
                space = tidOutput;
                logger.info(SchedulerConstants.TASKID_DIR_DEFAULT_NAME + " pattern found, changed " +
                    spaceName + " space to : " + space.getRealURI());
            }
        }
        return space;
    }

    protected void terminateDataSpace() {
        if (dataspaceInitialized) {
            try {
                // in dataspace debug mode, scratch directory are not cleaned after task execution
                if (!logger.isDebugEnabled()) {
                    DataSpacesNodes.tryCloseNodeApplicationConfig(PAActiveObject
                            .getActiveObjectNode(PAActiveObject.getStubOnThis()));
                }
            } catch (Exception e) {
                logger
                        .warn(
                                "There was a problem while terminating dataSpaces. Dataspaces on this node might not work anymore.",
                                e);
                // cannot add this message to dataspaces status as it is called in finally block
            }
        }
    }

    protected void copyInputDataToScratch() throws FileSystemException {
        try {
            if (inputFiles == null) {
                logger.debug("Input selector is empty, no file to copy");
                return;
            }

            // will contain all files coming from input space
            ArrayList<DataSpacesFileObject> inResults = new ArrayList<DataSpacesFileObject>();
            // will contain all files coming from output space
            ArrayList<DataSpacesFileObject> outResults = new ArrayList<DataSpacesFileObject>();
            // will contain all files coming from global space
            ArrayList<DataSpacesFileObject> globResults = new ArrayList<DataSpacesFileObject>();
            // will contain all files coming from user space
            ArrayList<DataSpacesFileObject> userResults = new ArrayList<DataSpacesFileObject>();

            FileSystemException toBeThrown = null;
            for (InputSelector is : inputFiles) {
                //fill fast file selector
                FastFileSelector fast = new FastFileSelector();
                fast.setIncludes(is.getInputFiles().getIncludes());
                fast.setExcludes(is.getInputFiles().getExcludes());
                fast.setCaseSensitive(is.getInputFiles().isCaseSensitive());
                switch (is.getMode()) {
                    case TransferFromInputSpace:
                        if (!checkInputSpaceConfigured(INPUT, "INPUT", is))
                            continue;

                        //search in INPUT
                        try {
                            int s = inResults.size();
                            FastSelector.findFiles(INPUT, fast, true, inResults);
                            if (s == inResults.size()) {
                                // we detected that there was no new file in the list
                                this.logDataspacesStatus("No file is transferred from INPUT space at " +
                                    INPUT.getRealURI() + "  for selector " + is,
                                        DataspacesStatusLevel.WARNING);
                                logger.warn("No file is transferred from INPUT space at " +
                                    INPUT.getRealURI() + " for selector " + is);
                            }
                        } catch (FileSystemException fse) {

                            toBeThrown = new FileSystemException("Could not contact INPUT space at " +
                                INPUT.getRealURI() + ". An error occured while resolving selector " + is);
                            this.logDataspacesStatus("Could not contact INPUT space at " +
                                INPUT.getRealURI() + " for selector " + is, DataspacesStatusLevel.ERROR);
                            this.logDataspacesStatus(Formatter.stackTraceToString(fse),
                                    DataspacesStatusLevel.ERROR);
                            logger.error("Could not contact INPUT space at " + INPUT.getRealURI() +
                                ". An error occured while resolving selector " + is, fse);
                        } catch (NullPointerException npe) {
                            // nothing to do
                        }
                        break;
                    case TransferFromOutputSpace:
                        if (!checkInputSpaceConfigured(OUTPUT, "OUTPUT", is))
                            continue;
                        //search in OUTPUT
                        try {
                            int s = outResults.size();
                            FastSelector.findFiles(OUTPUT, fast, true, outResults);
                            if (s == outResults.size()) {
                                // we detected that there was no new file in the list
                                this.logDataspacesStatus("No file is transferred from OUPUT space at " +
                                    OUTPUT.getRealURI() + "  for selector " + is,
                                        DataspacesStatusLevel.WARNING);
                                logger.warn("No file is transferred from OUPUT space at " +
                                    OUTPUT.getRealURI() + "  for selector " + is);
                            }
                        } catch (FileSystemException fse) {
                            toBeThrown = new FileSystemException("Could not contact OUTPUT space at " +
                                OUTPUT.getRealURI() + ". An error occured while resolving selector " + is);
                            this.logDataspacesStatus("Could not contact OUTPUT space at " +
                                OUTPUT.getRealURI() + " for selector " + is, DataspacesStatusLevel.ERROR);
                            this.logDataspacesStatus(Formatter.stackTraceToString(fse),
                                    DataspacesStatusLevel.ERROR);
                            logger.error("Could not contact OUTPUT space at " + OUTPUT.getRealURI() +
                                ". An error occured while resolving selector " + is, fse);
                        } catch (NullPointerException npe) {
                            // nothing to do
                        }
                        break;
                    case TransferFromGlobalSpace:
                        if (!checkInputSpaceConfigured(GLOBAL, "GLOBAL", is))
                            continue;
                        try {
                            int s = globResults.size();
                            FastSelector.findFiles(GLOBAL, fast, true, globResults);
                            if (s == globResults.size()) {
                                // we detected that there was no new file in the list
                                this.logDataspacesStatus("No file is transferred from GLOBAL space at " +
                                    GLOBAL.getRealURI() + "  for selector " + is,
                                        DataspacesStatusLevel.WARNING);
                                logger.warn("No file is transferred from GLOBAL space at " +
                                    GLOBAL.getRealURI() + "  for selector " + is);
                            }
                        } catch (FileSystemException fse) {
                            logger.info("", fse);
                            toBeThrown = new FileSystemException("Could not contact GLOBAL space at " +
                                GLOBAL.getRealURI() + ". An error occurred while resolving selector " + is);
                            this.logDataspacesStatus("Could not contact GLOBAL space at " +
                                GLOBAL.getRealURI() + ". An error occurred while resolving selector " + is,
                                    DataspacesStatusLevel.ERROR);
                            this.logDataspacesStatus(Formatter.stackTraceToString(fse),
                                    DataspacesStatusLevel.ERROR);
                            logger.error("Could not contact GLOBAL space at " + GLOBAL.getRealURI() +
                                ". An error occurred while resolving selector " + is, fse);

                        } catch (NullPointerException npe) {
                            // nothing to do
                        }
                        break;
                    case TransferFromUserSpace:
                        if (!checkInputSpaceConfigured(USER, "USER", is))
                            continue;
                        try {
                            int s = userResults.size();
                            FastSelector.findFiles(USER, fast, true, userResults);
                            if (s == userResults.size()) {
                                // we detected that there was no new file in the list
                                this
                                        .logDataspacesStatus("No file is transferred from USER space at " +
                                            USER.getRealURI() + "  for selector " + is,
                                                DataspacesStatusLevel.WARNING);
                                logger.warn("No file is transferred from USER space at " + USER.getRealURI() +
                                    "  for selector " + is);
                            }
                        } catch (FileSystemException fse) {
                            logger.info("", fse);
                            toBeThrown = new FileSystemException("Could not contact USER space at " +
                                USER.getRealURI() + ". An error occurred while resolving selector " + is);
                            this.logDataspacesStatus("Could not contact USER space at " + USER.getRealURI() +
                                ". An error occurred while resolving selector " + is,
                                    DataspacesStatusLevel.ERROR);
                            this.logDataspacesStatus(Formatter.stackTraceToString(fse),
                                    DataspacesStatusLevel.ERROR);
                            logger.error("Could not contact USER space at " + USER.getRealURI() +
                                ". An error occurred while resolving selector " + is, fse);

                        } catch (NullPointerException npe) {
                            // nothing to do
                        }
                        break;
                    case none:
                        //do nothing
                        break;
                }
            }

            if (toBeThrown != null) {
                throw toBeThrown;
            }

            String outuri = (OUTPUT == null) ? "" : OUTPUT.getVirtualURI();
            String globuri = (GLOBAL == null) ? "" : GLOBAL.getVirtualURI();
            String useruri = (USER == null) ? "" : USER.getVirtualURI();
            String inuri = (INPUT == null) ? "" : INPUT.getVirtualURI();

            Set<String> relPathes = new HashSet<String>();

            ArrayList<DataSpacesFileObject> results = new ArrayList<DataSpacesFileObject>();
            results.addAll(inResults);
            results.addAll(outResults);
            results.addAll(globResults);
            results.addAll(userResults);

            ArrayList<Future> transferFutures = new ArrayList<Future>();
            for (DataSpacesFileObject dsfo : results) {
                String relativePath;
                if (inResults.contains(dsfo)) {
                    relativePath = dsfo.getVirtualURI().replaceFirst(inuri + "/?", "");
                } else if (outResults.contains(dsfo)) {
                    relativePath = dsfo.getVirtualURI().replaceFirst(outuri + "/?", "");
                } else if (globResults.contains(dsfo)) {
                    relativePath = dsfo.getVirtualURI().replaceFirst(globuri + "/?", "");
                } else if (userResults.contains(dsfo)) {
                    relativePath = dsfo.getVirtualURI().replaceFirst(useruri + "/?", "");
                } else {
                    // should never happen
                    throw new IllegalStateException();
                }
                logger.debug("* " + relativePath);
                if (!relPathes.contains(relativePath)) {
                    logger.debug("------------ resolving " + relativePath);
                    final String finalRelativePath = relativePath;
                    final DataSpacesFileObject finaldsfo = dsfo;
                    transferFutures.add(executorTransfer.submit(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws FileSystemException {
                            logger.info("Copying " + finaldsfo.getRealURI() + " to " + SCRATCH.getRealURI() +
                                "/" + finalRelativePath);
                            SCRATCH
                                    .resolveFile(finalRelativePath)
                                    .copyFrom(
                                            finaldsfo,
                                            org.objectweb.proactive.extensions.dataspaces.api.FileSelector.SELECT_SELF);
                            return true;
                        }
                    }));

                }
                relPathes.add(relativePath);
            }

            StringBuilder exceptionMsg = new StringBuilder();
            String nl = System.getProperty("line.separator");
            for (Future f : transferFutures) {
                try {
                    f.get();
                } catch (InterruptedException e) {
                    logger.error("", e);
                    exceptionMsg.append(StackTraceUtil.getStackTrace(e)).append(nl);
                } catch (ExecutionException e) {
                    logger.error("", e);
                    exceptionMsg.append(StackTraceUtil.getStackTrace(e)).append(nl);
                }
            }
            if (exceptionMsg.length() > 0) {
                toBeThrown = new FileSystemException(
                    "Exception(s) occurred when transferring input files : " + nl + exceptionMsg.toString());
            }

            if (toBeThrown != null) {
                throw toBeThrown;
            }
        } finally {
            // display dataspaces error and warns if any
            displayDataspacesStatus();
        }
    }

    private boolean checkInputSpaceConfigured(DataSpacesFileObject space, String spaceName, InputSelector is) {
        if (space == null) {
            logger.error("Job " + spaceName +
                " space is not defined or not properly configured while input files are specified : ");

            this.logDataspacesStatus("Job " + spaceName +
                " space is not defined or not properly configured while input files are specified : ",
                    DataspacesStatusLevel.ERROR);

            logger.error("--> " + is);
            this.logDataspacesStatus("--> " + is, DataspacesStatusLevel.ERROR);

            return false;
        }
        return true;
    }

    private boolean checkOuputSpaceConfigured(DataSpacesFileObject space, String spaceName, OutputSelector os) {
        if (space == null) {
            logger.debug("Job " + spaceName +
                " space is not defined or not properly configured, while output files are specified :");
            this.logDataspacesStatus("Job " + spaceName +
                " space is not defined or not properly configured, while output files are specified :",
                    DataspacesStatusLevel.ERROR);
            this.logDataspacesStatus("--> " + os, DataspacesStatusLevel.ERROR);
            return false;
        }
        return true;
    }

    protected void copyScratchDataToOutput(List<OutputSelector> outputFiles) throws FileSystemException {
        try {
            if (outputFiles == null) {
                logger.debug("Output selector is empty, no file to copy");
                return;
            }

            // We check that the spaces used are properly configured, we show a message in the log output to the user if not
            for (OutputSelector os1 : outputFiles) {
                switch (os1.getMode()) {
                    case TransferToOutputSpace:
                        checkOuputSpaceConfigured(OUTPUT, "OUTPUT", os1);
                        break;
                    case TransferToGlobalSpace:
                        checkOuputSpaceConfigured(GLOBAL, "GLOBAL", os1);
                        break;
                    case TransferToUserSpace:
                        checkOuputSpaceConfigured(USER, "USER", os1);
                        break;
                }
            }

            // flush and close stdout/err
            try {
                this.finalizeLoggers();
            } catch (RuntimeException e) {
                // exception should not be thrown to the scheduler core
                // the result has been computed and must be returned !
                logger.warn("Loggers are not shutdown !", e);
            }

            ArrayList<DataSpacesFileObject> results = new ArrayList<DataSpacesFileObject>();
            FileSystemException toBeThrown = null;

            for (OutputSelector os : outputFiles) {
                //fill fast file selector
                FastFileSelector fast = new FastFileSelector();
                fast.setIncludes(os.getOutputFiles().getIncludes());
                fast.setExcludes(os.getOutputFiles().getExcludes());
                fast.setCaseSensitive(os.getOutputFiles().isCaseSensitive());
                switch (os.getMode()) {
                    case TransferToOutputSpace:
                        if (OUTPUT != null) {
                            try {
                                int s = results.size();
                                handleOutput(OUTPUT, fast, results);
                                if (results.size() == s) {
                                    this.logDataspacesStatus("No file is transferred to OUTPUT space at " +
                                        OUTPUT.getRealURI() + " for selector " + os,
                                            DataspacesStatusLevel.WARNING);
                                    logger.warn("No file is transferred to OUTPUT space at " +
                                        OUTPUT.getRealURI() + " for selector " + os);
                                }
                            } catch (FileSystemException fse) {
                                toBeThrown = fse;
                                this.logDataspacesStatus("Error while transferring to OUTPUT space at " +
                                    OUTPUT.getRealURI() + " for selector " + os, DataspacesStatusLevel.ERROR);
                                this.logDataspacesStatus(Formatter.stackTraceToString(fse),
                                        DataspacesStatusLevel.ERROR);
                                logger.error("Error while transferring to OUTPUT space at " +
                                    OUTPUT.getRealURI() + " for selector " + os, fse);
                            }
                        }
                        break;
                    case TransferToGlobalSpace:
                        if (GLOBAL != null) {
                            try {
                                int s = results.size();
                                handleOutput(GLOBAL, fast, results);
                                if (results.size() == s) {
                                    this.logDataspacesStatus("No file is transferred to GLOBAL space at " +
                                        GLOBAL.getRealURI() + " for selector " + os,
                                            DataspacesStatusLevel.WARNING);
                                    logger.warn("No file is transferred to GLOBAL space at " +
                                        GLOBAL.getRealURI() + " for selector " + os);
                                }
                            } catch (FileSystemException fse) {
                                toBeThrown = fse;
                                this.logDataspacesStatus("Error while transferring to GLOBAL space at " +
                                    GLOBAL.getRealURI() + " for selector " + os, DataspacesStatusLevel.ERROR);
                                this.logDataspacesStatus(Formatter.stackTraceToString(fse),
                                        DataspacesStatusLevel.ERROR);
                                logger.error("Error while transferring to GLOBAL space at " +
                                    GLOBAL.getRealURI() + " for selector " + os, fse);
                            }
                        }
                        break;
                    case TransferToUserSpace:
                        if (USER != null) {
                            try {
                                int s = results.size();
                                handleOutput(USER, fast, results);
                                if (results.size() == s) {
                                    this.logDataspacesStatus("No file is transferred to USER space at " +
                                        USER.getRealURI() + " for selector " + os,
                                            DataspacesStatusLevel.WARNING);
                                    logger.warn("No file is transferred to USER space at " +
                                        USER.getRealURI() + " for selector " + os);
                                }
                            } catch (FileSystemException fse) {
                                toBeThrown = fse;
                                this.logDataspacesStatus("Error while transferring to USER space at " +
                                    USER.getRealURI() + " for selector " + os, DataspacesStatusLevel.ERROR);
                                this.logDataspacesStatus(Formatter.stackTraceToString(fse),
                                        DataspacesStatusLevel.ERROR);
                                logger.error("Error while transferring to USER space at " +
                                    USER.getRealURI() + " for selector " + os, fse);
                            }
                            break;
                        }
                    case none:
                        break;
                }
                results.clear();
            }

            if (toBeThrown != null) {
                throw toBeThrown;
            }

        } finally {
            // display dataspaces error and warns if any
            displayDataspacesStatus();
        }
    }

    protected void copyScratchDataToOutput() throws FileSystemException {

        // Handling traditional output files
        copyScratchDataToOutput(outputFiles);

        // flushing and closing stdout/err even if it's not dataspace aware task
        try {
            this.finalizeLoggers();
        } catch (RuntimeException e) {
            // exception should not be thrown to the scheduler core
            // the result has been computed and must be returned !
            logger.warn("Loggers are not shutdown !", e);
        }

        // Handling logFile separately
        if (this.storeLogs) {
            copyScratchDataToOutput(getTaskLogsSelectors(OutputAccessMode.TransferToOutputSpace));
        }

        try {
            copyScratchDataToOutput(getTaskLogsSelectors(OutputAccessMode.TransferToUserSpace));
        } catch (FileSystemException th) {
            // ignore the exception if cannot copy logs to user data space
            logger.warn("Cannot copy logs of task to user data spaces", th);
        }
    }

    protected List<OutputSelector> getTaskLogsSelectors(OutputAccessMode transferTo) {
        List<OutputSelector> result = new ArrayList<OutputSelector>(1);
        // Log file will be transferred by this task to the user or output space, only if it's not a forked task
        if (!isForkedTask()) {
            OutputSelector logFiles = new OutputSelector(
                new FileSelector(TaskLauncherBak.LOG_FILE_PREFIX + "*"), transferTo);
            result.add(logFiles);
        }
        return result;
    }

    private void handleOutput(DataSpacesFileObject out, FastFileSelector fast,
            ArrayList<DataSpacesFileObject> results) throws FileSystemException {
        FastSelector.findFiles(SCRATCH, fast, true, results);
        if (logger.isDebugEnabled()) {
            if (results == null || results.size() == 0) {
                logger.debug("No file found to copy from LOCAL space to OUTPUT space");
            } else {
                logger.debug("Files that will be copied from LOCAL space to OUTPUT space :");
            }
        }
        String buri = SCRATCH.getVirtualURI();
        ArrayList<Future> transferFutures = new ArrayList<Future>();

        if (results != null) {
            for (DataSpacesFileObject dsfo : results) {
                String relativePath = dsfo.getVirtualURI().replaceFirst(buri + "/?", "");
                logger.debug("* " + relativePath);

                final String finalRelativePath = relativePath;
                final DataSpacesFileObject finaldsfo = dsfo;
                final DataSpacesFileObject finalout = out;
                transferFutures.add(executorTransfer.submit(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws FileSystemException {
                        logger.info("Copying " + finaldsfo.getRealURI() + " to " + finalout.getRealURI() +
                            "/" + finalRelativePath);

                        finalout.resolveFile(finalRelativePath).copyFrom(finaldsfo,
                                org.objectweb.proactive.extensions.dataspaces.api.FileSelector.SELECT_SELF);
                        return true;
                    }
                }));
            }
        }

        StringBuilder exceptionMsg = new StringBuilder();
        String nl = System.getProperty("line.separator");
        for (Future f : transferFutures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                logger.error("", e);
                exceptionMsg.append(StackTraceUtil.getStackTrace(e)).append(nl);
            } catch (ExecutionException e) {
                logger.error("", e);
                exceptionMsg.append(StackTraceUtil.getStackTrace(e)).append(nl);
            }
        }
        if (exceptionMsg.length() > 0) {
            throw new FileSystemException("Exception(s) occurred when transferring input files : " + nl +
                exceptionMsg.toString());
        }
    }

    /**
     * Replace iteration and replication helper tags in the dataspace's input and output descriptions
     */
    protected void replaceTagsInDataspaces() {
        if (inputFiles != null) {
            for (InputSelector is : inputFiles) {
                String[] inc = is.getInputFiles().getIncludes();
                String[] exc = is.getInputFiles().getExcludes();

                if (inc != null) {
                    for (int i = 0; i < inc.length; i++) {
                        inc[i] = replaceAllTags(inc[i]);
                    }
                }
                if (exc != null) {
                    for (int i = 0; i < exc.length; i++) {
                        exc[i] = replaceAllTags(exc[i]);
                    }
                }

                is.getInputFiles().setIncludes(inc);
                is.getInputFiles().setExcludes(exc);
            }
        }
        if (outputFiles != null) {
            for (OutputSelector os : outputFiles) {
                String[] inc = os.getOutputFiles().getIncludes();
                String[] exc = os.getOutputFiles().getExcludes();

                if (inc != null) {
                    for (int i = 0; i < inc.length; i++) {
                        inc[i] = replaceAllTags(inc[i]);
                    }
                }
                if (exc != null) {
                    for (int i = 0; i < exc.length; i++) {
                        exc[i] = replaceAllTags(exc[i]);
                    }
                }

                os.getOutputFiles().setIncludes(inc);
                os.getOutputFiles().setExcludes(exc);
            }
        }
    }

    /**
     * Replace iteration and replication helper tags in the scripts' contents and parameters
     *
     * @param script the script where tags should be replaced
     */
    protected void replaceTagsInScript(Script<?> script) {
        if (script == null) {
            return;
        }
        String code = script.getScript();
        Serializable[] args = script.getParameters();

        code = replaceAllTags(code);
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                args[i] = replaceAllTags(args[i].toString());
            }
        }

        script.setScript(code);
    }

    /**
     * Replace all tags ($IT, $REP and $JID) in s by the current value.
     * @return the string with all tags replaced.
     */
    private String replaceAllTags(String s) {
        s = s.replace(ITERATION_INDEX_TAG, "" + this.iterationIndex);
        s = s.replace(REPLICATION_INDEX_TAG, "" + this.replicationIndex);
        s = s.replace(JOBID_INDEX_TAG, this.taskId.getJobId().value());
        return s;
    }

    /**
     * Add a message to the dataspaces status buffer. This buffer is displayed at the end 
     * of the task if non empty.
     */
    protected void logDataspacesStatus(String message, DataspacesStatusLevel level) {
        final String eol = System.getProperty("line.separator");
        final boolean hasEol = message.endsWith(eol);
        if (level == DataspacesStatusLevel.ERROR) {
            this.clientLogs.append("[DATASPACES-ERROR] ").append(message).append(hasEol ? "" : eol);
        } else if (level == DataspacesStatusLevel.WARNING) {
            this.clientLogs.append("[DATASPACES-WARNING] ").append(message).append(hasEol ? "" : eol);
        } else if (level == DataspacesStatusLevel.INFO) {
            this.clientLogs.append("[DATASPACES-INFO] ").append(message).append(hasEol ? "" : eol);
        }
    }

    /**
     * Log level for dataspaces messages.
     */
    protected enum DataspacesStatusLevel {
        ERROR, WARNING, INFO
    }

    /**
     * Display the content of the dataspaces status buffer on stderr if non empty.
     */
    protected void displayDataspacesStatus() {
        if (this.clientLogs.length() != 0) {
            System.err.println("");
            System.err.println(this.clientLogs);
            System.err.flush();
            this.clientLogs = new StringBuffer();
        }
    }

    /**
     * Use ProActive API to get the hostname of this JVM.
     * Avoid using ActiveObject API which can be not started at initialization time.<br/>
     * <br/>
     * This method don't need the activeObject to exist to be called.
     *
     * @return the hostname of the local JVM
     */
    private String getHostname() {
        return ProActiveInet.getInstance().getInetAddress().getHostName();
    }

    /*
     * Retrieve propagated variables from previous tasks (hooked in the task
     * result object).
     */
    protected void updatePropagatedVariables(TaskResult... results) throws Exception {
        if (results != null && results.length > 0) {
            Map<String, byte[]> variables = getPropagatedVariables(results);
            if (propagatedVariables == null) {
                propagatedVariables = new HashMap<String, Serializable>();
            }
            propagatedVariables.putAll(deserializeVariableMap(variables));
        }
        this.propagatedVariables.putAll(contextVariables());
    }

    private Map<? extends String, ? extends Serializable> contextVariables() {
        Map<String, String> variables = new HashMap<String, String>();
        variables.put(SchedulerVars.JAVAENV_JOB_ID_VARNAME.toString(), this.taskId.getJobId().value());
        variables.put(SchedulerVars.JAVAENV_JOB_NAME_VARNAME.toString(), this.taskId.getJobId()
                .getReadableName());
        variables.put(SchedulerVars.JAVAENV_TASK_ID_VARNAME.toString(), this.taskId.value());
        variables.put(SchedulerVars.JAVAENV_TASK_NAME_VARNAME.toString(), this.taskId.getReadableName());
        variables.put(SchedulerVars.JAVAENV_TASK_ITERATION.toString(), String.valueOf(this.iterationIndex));
        variables.put(SchedulerVars.JAVAENV_TASK_REPLICATION.toString(), String
                .valueOf(this.replicationIndex));
        variables.put(PASchedulerProperties.SCHEDULER_HOME.getKey(), CentralPAPropertyRepository.PA_HOME
                .getValue());
        variables.put(PAResourceManagerProperties.RM_HOME.getKey(), PAResourceManagerProperties.RM_HOME
                .getValueAsString());
        variables.put(CentralPAPropertyRepository.PA_HOME.getName(), CentralPAPropertyRepository.PA_HOME
                .getValueAsString());
        return variables;
    }

    protected void attachPropagatedVariables(TaskResultImpl resultImpl) {
        if (propagatedVariables != null) {
            resultImpl.setPropagatedVariables(serializeVariableMap(propagatedVariables));
        }
    }

    protected Map<String, Serializable> getPropagatedVariables() {
        return propagatedVariables;
    }

    protected void setPropagatedVariables(Map<String, Serializable> propagatedVariables) {
        this.propagatedVariables = propagatedVariables;
    }

    /**
     * This class acts as a proxy/guard to the executable usage
     * The proxy can be called concurrently by :
     * - TaskLaunchers main doTask method
     * - terminate (kill) calls
     * - getProgress calls
     */
    public class ExecutableGuard extends Guard<Executable> {

        int lastProgress = 0;

        @Override
        protected void internalKill() {
            if (targetInitialized) {
                target.kill();
            }
        }

        @Override
        protected void internalClean() {

            // finalize task in any cases (killed or not)
            terminateDataSpace();

            if (isWallTime()) {
                cancelTimer();
            }

            try {
                finalizeLoggers();
            } catch (RuntimeException e) {
                // exception should not be thrown to the scheduler core
                // the result has been computed and must be returned !
                logger.warn("Loggers are not shutdown !", e);
            }

            // close executors
            if (!executorTransfer.isShutdown()) {
                executorTransfer.shutdownNow();
            }
        }

        /**
         * Executes a preScript in a cancellable separated thread
         * @throws Exception
         */
        public void initDataSpaces() throws Throwable {
            submitACallable(new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    try {
                        TaskLauncherBak.this.initDataSpaces();
                        return true;
                    } catch (Throwable throwable) {
                        throw new ToUnwrapException(throwable);
                    }
                }
            }, false);
            waitCallable();
        }

        /**
         * Executes a preScript in a cancellable separated thread
         * @throws Exception
         */
        public void executePreScript() throws Throwable {
            submitACallable(new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    try {
                        TaskLauncherBak.this.executePreScript();
                        return true;
                    } catch (Throwable throwable) {
                        throw new ToUnwrapException(throwable);
                    }
                }
            }, true);
            waitCallable();
        }

        /**
         * Executes a postScript in a cancellable separated thread
         * @throws Exception
         */
        public void executePostScript(final boolean executionSucceed) throws Throwable {
            submitACallable(new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    try {
                        TaskLauncherBak.this.executePostScript(executionSucceed);
                        return true;
                    } catch (Throwable throwable) {
                        throw new ToUnwrapException(throwable);
                    }
                }
            }, true);
            waitCallable();
        }

        /**
         * Executes a flowScript in a cancellable separated thread
         * @throws Exception
         */
        public void executeFlowScript(final TaskResult res) throws Throwable {
            submitACallable(new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    try {
                        TaskLauncherBak.this.executeFlowScript(res);
                        return true;
                    } catch (Throwable throwable) {
                        throw new ToUnwrapException(throwable);
                    }
                }
            }, true);
            waitCallable();
        }

        /**
         * copy input files to scratch in a cancellable separated thread
         * @throws Exception
         */
        public void copyInputDataToScratch() throws Throwable {
            long sample = System.nanoTime();
            submitACallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    try {
                        TaskLauncherBak.this.copyInputDataToScratch();
                        return true;
                    } catch (Throwable throwable) {
                        throw new ToUnwrapException(throwable);
                    }
                }
            }, true);
            waitCallable();
            logger.info("Time spent copying INPUT datas to SCRATCH : " +
                timeElapsedSinceInMilliseconds(sample) + " ms");
        }

        /**
         * copy local files to output in a cancellable separated thread
         * @throws Exception
         */
        public void copyScratchDataToOutput() throws Throwable {
            long sample = System.nanoTime();
            submitACallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    try {
                        TaskLauncherBak.this.copyScratchDataToOutput();
                        return true;
                    } catch (Throwable throwable) {
                        throw new ToUnwrapException(throwable);
                    }
                }
            }, true);
            waitCallable();
            logger.info("Time spent copying SCRATCH datas to OUTPUT : " +
                timeElapsedSinceInMilliseconds(sample) + " ms");
        }

        /**
         * copy local files to output in a cancellable separated thread
         * @throws Exception
         */
        public void copyScratchDataToOutput(final List<OutputSelector> outputFiles) throws Throwable {
            submitACallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    try {
                        TaskLauncherBak.this.copyScratchDataToOutput(outputFiles);
                        return true;
                    } catch (Throwable throwable) {
                        throw new ToUnwrapException(throwable);
                    }
                }
            }, true);
            waitCallable();
        }

        public void callInternalInit(final Class<?> targetedClass, final Class<?> parameterType,
                final ExecutableInitializer argument) throws Throwable {
            submitACallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    try {
                        TaskLauncherBak.this.callInternalInit(targetedClass, parameterType, argument);
                        return true;
                    } catch (Throwable throwable) {
                        throw new ToUnwrapException(throwable);
                    }
                }
            }, true);
            waitCallable();
        }

        /**
         * execute the task in a cancellable separated thread
         * @throws Exception
         */
        public Serializable execute(final TaskResult... results) throws Throwable {

            submitACallable(new Callable<Serializable>() {

                @Override
                public Serializable call() throws Exception {
                    try {
                        return target.execute(results);
                    } catch (Throwable throwable) {
                        throw new ToUnwrapException(throwable);
                    }
                }
            }, true);
            return waitCallable();
        }

        /**
         * @return current progress of the executable
         */
        public synchronized int getProgress() {
            if (state == GuardState.NOT_INITIALIZED) {
                //not yet started
                return 0;
            } else if (state == GuardState.KILLED || state == GuardState.CLEANED) {
                // return last value computed before the kill
                return lastProgress;
            } else {
                try {
                    lastProgress = target.getProgress();
                    if (lastProgress < 0) {
                        logger
                                .warn("Returned progress (" + lastProgress +
                                    ") is negative, return 0 instead.");
                        lastProgress = 0;
                    } else if (lastProgress > 100) {
                        logger.warn("Returned progress (" + lastProgress +
                            ") is greater than 100, return 100 instead.");
                        lastProgress = 100;
                    }
                    return lastProgress;
                } catch (Throwable t) {
                    //protect call to user getProgress() if overridden
                    throw new IllegalProgressException(
                        "executable.getProgress() method has thrown an exception", t);
                }
            }
        }
    }

    private long timeElapsedSinceInMilliseconds(long sinceInNanoSeconds) {
        return Math.round(Math.ceil((System.nanoTime() - sinceInNanoSeconds) / 1000000));
    }
}
