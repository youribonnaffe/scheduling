/*
 *  *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2014 INRIA/University of
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
 *  * $$ACTIVEEON_INITIAL_DEV$$
 */
package org.ow2.proactive.scheduler.task;

import com.google.common.base.Stopwatch;
import org.ow2.proactive.scheduler.common.task.TaskResult;
import org.ow2.proactive.scheduler.common.task.flow.FlowAction;
import org.ow2.proactive.scheduler.common.task.flow.FlowScript;
import org.ow2.proactive.scheduler.common.task.util.SerializationUtil;
import org.ow2.proactive.scheduler.task.script.ScriptExecutableContainer;
import org.ow2.proactive.scripting.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


/**
 * Run a task through a script handler.
 * Responsible for:
 *  - running the different scripts
 *  - variable propagation
 *  - replacement for variables/
 *  - getting the task result or user code exceptions
 */
public class NonForkedTaskExecutor implements TaskExecutor {

    private static final String DS_SCRATCH_BINDING_NAME = "localspace";
    private static final String DS_INPUT_BINDING_NAME = "inputspace";
    private static final String DS_OUTPUT_BINDING_NAME = "outputspace";
    private static final String DS_GLOBAL_BINDING_NAME = "globalspace";
    private static final String DS_USER_BINDING_NAME = "userspace";

    private static final String MULTI_NODE_TASK_NODESET_BINDING_NAME = "nodeset";
    public static final String MULTI_NODE_TASK_NODESURL_BINDING_NAME = "nodesurl";

    public static final String VARIABLES_BINDING_NAME = "variables";

    /**
     * Will be replaced by the matching third-party credential
     * Example: if one of the third-party credentials' key-value pairs is 'foo:bar',
     * then '$CREDENTIALS_foo' will be replaced by 'bar' in the arguments of the tasks.
     */
    private static final String CREDENTIALS_KEY_PREFIX = "$CREDENTIALS_";

    @Override
    public TaskResultImpl execute(TaskContext container, PrintStream output, PrintStream error) {
        ScriptHandler scriptHandler = ScriptLoader.createLocalHandler();

        try {
            Map<String, Serializable> variables = taskVariables(container);
            Map<String, String> thirdPartyCredentials = thirdPartyCredentials(container);
            createBindings(container, scriptHandler, variables, thirdPartyCredentials);

            Stopwatch stopwatch = Stopwatch.createUnstarted();
            TaskResultImpl taskResult;
            try {
                stopwatch.start();
                Serializable result = execute(container, output, error, scriptHandler, thirdPartyCredentials,
                        variables);
                stopwatch.stop();
                taskResult = new TaskResultImpl(
                        container.getTaskId(), result, null, stopwatch.elapsed(TimeUnit.MILLISECONDS));
            } catch (Throwable e) {
                stopwatch.stop();
                e.printStackTrace(error);
                taskResult = new TaskResultImpl(
                        container.getTaskId(), e, null, stopwatch.elapsed(TimeUnit.MILLISECONDS));
            }

            executeFlowScript(container.getControlFlowScript(), scriptHandler, output, error, taskResult);

            taskResult.setPropagatedVariables(SerializationUtil.serializeVariableMap(variables));

            return taskResult;
        } catch (Throwable e) {
            e.printStackTrace(error);
            return new TaskResultImpl(container.getTaskId(), e);
        }
    }

    static void createBindings(TaskContext container, ScriptHandler scriptHandler, Map<String, Serializable> variables, Map<String, String> thirdPartyCredentials) {
        scriptHandler.addBinding(VARIABLES_BINDING_NAME, variables);

        TaskResult[] results = tasksResults(container);
        scriptHandler.addBinding(TaskScript.RESULTS_VARIABLE, results);

        scriptHandler.addBinding(TaskScript.CREDENTIALS_VARIABLE, thirdPartyCredentials);

        scriptHandler.addBinding(DS_SCRATCH_BINDING_NAME, container.getScratchURI());
        scriptHandler.addBinding(DS_INPUT_BINDING_NAME, container.getInputURI());
        scriptHandler.addBinding(DS_OUTPUT_BINDING_NAME, container.getOutputURI());
        scriptHandler.addBinding(DS_GLOBAL_BINDING_NAME, container.getGlobalURI());
        scriptHandler.addBinding(DS_USER_BINDING_NAME, container.getUserURI());

        Set<String> nodesUrls = container.getNodesURLs();
        scriptHandler.addBinding(MULTI_NODE_TASK_NODESET_BINDING_NAME, nodesUrls);
        scriptHandler.addBinding(MULTI_NODE_TASK_NODESURL_BINDING_NAME, nodesUrls);
    }

    static String writeNodesFile(Set<String> nodesHosts) throws IOException {
        if (nodesHosts.isEmpty()) {
            return "";
        } else { // TODO check why not deleted, check why it is there even if empty
            File nodesFiles = File.createTempFile("pa_nodes", null);
            FileWriter outputWriter = new FileWriter(nodesFiles);
            for (String nodeHost : nodesHosts) {
                outputWriter.append(nodeHost).append(System.getProperty("line.separator"));
            }
            outputWriter.close();
            return nodesFiles.getAbsolutePath();
        }
    }

    static Map<String, Serializable> taskVariables(TaskContext container) throws Exception {
        Map<String, Serializable> variables = new HashMap<>();

        // variables from workflow definition
        if (container.getInitializer().getVariables() != null) {
            variables.putAll(container.getInitializer().getVariables());
        }

        // variables from previous tasks
        try {
            if (container.getPreviousTasksResults() != null) {
                for (TaskResult taskResult : container.getPreviousTasksResults()) {
                    if (taskResult.getPropagatedVariables() != null) {
                        variables.putAll(SerializationUtil.deserializeVariableMap(taskResult
                                .getPropagatedVariables()));
                    }
                }
            }
        } catch (Exception e) {
            throw new Exception("Could deserialize variables", e);
        }

        // variables from current job/task context
        variables.putAll(contextVariables(container.getInitializer()));

        variables.put(SchedulerVars.PA_NODESNUMBER.toString(), container.getNodesURLs().size());
        variables.put(SchedulerVars.PA_NODESFILE.toString(), writeNodesFile(container.getNodesHosts()));

        variables.put(SchedulerVars.PA_SCHEDULER_HOME.toString(), container.getSchedulerHome());
        variables.put(SchedulerVars.PA_TASK_PROGRESS_FILE.toString(), container.getProgressFilePath());

        return variables;
    }

    static TaskResult[] tasksResults(TaskContext container) {
        TaskResult[] previousTasksResults = container.getPreviousTasksResults();
        if (previousTasksResults != null) {
            return previousTasksResults;
        } else {
            return new TaskResult[0];
        }
    }

    static Map<String, Serializable> contextVariables(TaskLauncherInitializer initializer) {
        Map<String, Serializable> variables = new HashMap<>();
        variables.put(SchedulerVars.PA_JOB_ID.toString(), initializer.getTaskId().getJobId().value());
        variables.put(SchedulerVars.PA_JOB_NAME.toString(), initializer.getTaskId().getJobId()
                .getReadableName());
        variables.put(SchedulerVars.PA_TASK_ID.toString(), initializer.getTaskId().value());
        variables.put(SchedulerVars.PA_TASK_NAME.toString(), initializer.getTaskId().getReadableName());
        variables.put(SchedulerVars.PA_TASK_ITERATION.toString(), initializer.getIterationIndex());
        variables.put(SchedulerVars.PA_TASK_REPLICATION.toString(), initializer.getReplicationIndex());
        return variables;
    }

    static Map<String, String> thirdPartyCredentials(TaskContext container) throws Exception {
        try {
            Map<String, String> thirdPartyCredentials = new HashMap<>();
            if (container.getDecrypter() != null) {
                thirdPartyCredentials.putAll(container.getDecrypter().decrypt().getThirdPartyCredentials());
            }
            return thirdPartyCredentials;
        } catch (Exception e) {
            throw new Exception("Could read encrypted third party credentials", e);
        }
    }

    static void replaceScriptParameters(Script script, Map<String, String> thirdPartyCredentials,
      Map<String, Serializable> variables) {

        Map<String, String> replacements = buildReplacements(variables);

        for (Map.Entry<String, String> credentialEntry : thirdPartyCredentials.entrySet()) {
            replacements.put(CREDENTIALS_KEY_PREFIX + credentialEntry.getKey(), credentialEntry.getValue());
        }

        performReplacements(script, replacements);
    }

    // TODO to extract
    public static Map<String, String> buildReplacements(Map<String, Serializable> variables) {
        Map<String, String> replacements = new HashMap<>();
        if (variables != null) {
            for (Map.Entry<String, Serializable> variable : variables.entrySet()) {
                replacements.put("$" + variable.getKey(), variable.getValue().toString());
                replacements.put("${" + variable.getKey() + "}", variable.getValue().toString());
            }
        }
        return replacements;
    }

    static void performReplacements(Script script, Map<String, String> replacements) {
        if (script != null) {
            if ("java".equals(script.getEngineName())) {
                try {
                    Map<String, Serializable> deserializedArgs = SerializationUtil
                            .deserializeVariableMap((Map<String, byte[]>) script.getParameters()[0]);
                    for (Map.Entry<String, Serializable> deserializedArg : deserializedArgs.entrySet()) {
                        if (deserializedArg.getValue() instanceof String) {
                            deserializedArg.setValue(
                              replace((String) deserializedArg.getValue(), replacements));
                        }
                    }
                    script.getParameters()[0] = new HashMap<>(
                            SerializationUtil.serializeVariableMap(deserializedArgs));
                } catch (Exception e) {
                    System.err.println("Could read Java parameters");
                    e.printStackTrace(System.err);
                }
            } else if ("native".equals(script.getEngineName())) { // to replace script arguments
                script.setScript(replace(script.getScript(), replacements));
            } else {
                Serializable[] args = script.getParameters();

                if (args != null) {
                    for (int i = 0; i < args.length; i++) {
                        if (args[i] instanceof String) {
                            args[i] = replace((String) args[i], replacements);
                        }
                    }
                }
            }
        }
    }

    // TODO to extract
    public static String replace(String input, Map<String, String> replacements) {
        String output = input;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            output = output.replace(replacement.getKey(), replacement.getValue());
        }
        return output;
    }

    private Serializable execute(TaskContext container, PrintStream output, PrintStream error,
            ScriptHandler scriptHandler, Map<String, String> thirdPartyCredentials,
            Map<String, Serializable> variables) throws Exception {
        if (container.getPreScript() != null) {
            executeScript(output, error, scriptHandler, thirdPartyCredentials, variables, container.getPreScript());
        }

        Script<Serializable> script = ((ScriptExecutableContainer) container.getExecutableContainer())
                .getScript();
        replaceScriptParameters(script, thirdPartyCredentials, variables);
        ScriptResult<Serializable> scriptResult = scriptHandler.handle(script, output, error);

        if (scriptResult.errorOccured()) {
            throw new Exception("Failed to execute task: " + scriptResult.getException().getMessage(),
                scriptResult.getException());
        }

        if (container.getPostScript() != null) {
            replaceScriptParameters(container.getPostScript(), thirdPartyCredentials, variables);
            ScriptResult postScriptResult = scriptHandler.handle(container.getPostScript(), output, error);
            if (postScriptResult.errorOccured()) {
                throw new Exception("Failed to execute post script", postScriptResult.getException());
            }
        }
        return scriptResult.getResult();
    }

    static ScriptResult executeScript(PrintStream output, PrintStream error, ScriptHandler scriptHandler, Map<String, String> thirdPartyCredentials, Map<String, Serializable> variables, Script<?> script) throws Exception {
        replaceScriptParameters(script, thirdPartyCredentials, variables);
        ScriptResult scriptResult = scriptHandler.handle(script, output, error);
        if (scriptResult.errorOccured()) {
            throw new Exception("Failed to execute script", scriptResult.getException());
        }
        return scriptResult;
    }

    private void executeFlowScript(FlowScript flowScript, ScriptHandler scriptHandler, PrintStream output,
            PrintStream error, TaskResultImpl taskResult) {
        if (flowScript != null) {
            try {
                scriptHandler.addBinding(FlowScript.resultVariable, taskResult.value());
            } catch (Throwable throwable) {
                scriptHandler.addBinding(FlowScript.resultVariable, throwable);
            }

            ScriptResult<FlowAction> flowScriptResult = scriptHandler.handle(flowScript, output, error);

            if (flowScriptResult.errorOccured()) {
                flowScriptResult.getException().printStackTrace(error);
                taskResult.setException(flowScriptResult.getException());
                taskResult.setAction(FlowAction.getDefaultAction(flowScript));
            } else {
                taskResult.setAction(flowScriptResult.getResult());
            }
        }
    }

}
