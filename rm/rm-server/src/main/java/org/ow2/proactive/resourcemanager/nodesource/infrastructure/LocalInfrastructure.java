package org.ow2.proactive.resourcemanager.nodesource.infrastructure;

import java.io.IOException;
import java.security.KeyException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.proactive.core.config.CentralPAPropertyRepository;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.util.ProActiveCounter;
import org.ow2.proactive.authentication.crypto.Credentials;
import org.ow2.proactive.process.ProcessExecutor;
import org.ow2.proactive.resourcemanager.core.properties.PAResourceManagerProperties;
import org.ow2.proactive.resourcemanager.exception.RMException;
import org.ow2.proactive.resourcemanager.nodesource.common.Configurable;
import org.ow2.proactive.resourcemanager.utils.CommandLineBuilder;
import org.ow2.proactive.resourcemanager.utils.OperatingSystem;
import org.ow2.proactive.utils.Tools;


public class LocalInfrastructure extends InfrastructureManager {

    @Configurable(description = "Absolute path to credentials file\nused to add the node to the Resource Manager", credential = true)
    private Credentials credentials;
    @Configurable(description = "Maximum number of nodes to\nbe deployed on Resource Manager machine")
    private int maxNodes = 4;
    private AtomicInteger atomicMaxNodes;
    @Configurable(description = "in ms. After this timeout expired\nthe node is considered to be lost")
    private int nodeTimeout = 5000;
    @Configurable(description = "Additional ProActive properties")
    private String paProperties = "";

    /** To link a nodeName with its process */
    private Hashtable<String, ProcessExecutor> nodeNameToProcess;
    /** To link a deploying node url with a boolean, used to notify deploying node removal */
    private Hashtable<String, Boolean> isDeployingNodeLost;
    /** Notifies the acquisition loop that the node has been acquired and that it can exit the loop */
    private Hashtable<String, Boolean> isNodeAcquired;

    public LocalInfrastructure() {
    }

    public String getDescription() {
        return "Deploys nodes on Resource Manager's machine";
    }

    @Override
    public void acquireAllNodes() {
        while (this.atomicMaxNodes.getAndDecrement() >= 1) {
            this.nodeSource.executeInParallel(new Runnable() {
                public void run() {
                    LocalInfrastructure.this.acquireNodeImpl();
                }
            });
        }
        //one decremented once too many
        this.atomicMaxNodes.getAndIncrement();
        logger.debug("Cannot acquire more nodes");
    }

    @Override
    public void acquireNode() {
        if (this.atomicMaxNodes.getAndDecrement() >= 1) {
            this.nodeSource.executeInParallel(new Runnable() {
                public void run() {
                    LocalInfrastructure.this.acquireNodeImpl();
                }
            });
        } else {
            //one decremented once too many
            this.atomicMaxNodes.getAndIncrement();
            logger.warn("Cannot acquire a new node");
        }
    }

    private void acquireNodeImpl() {
        String nodeName = "local-" + this.nodeSource.getName() + "-" + ProActiveCounter.getUniqID();
        OperatingSystem os = OperatingSystem.UNIX;
        //assuming no cygwin, windows or the "others"...
        if (System.getProperty("os.name").contains("Windows")) {
            os = OperatingSystem.WINDOWS;
        }
        String rmHome = PAResourceManagerProperties.RM_HOME.getValueAsString();
        if (!rmHome.endsWith(os.fs)) {
            rmHome += os.fs;
        }
        CommandLineBuilder clb = this.getDefaultCommandLineBuilder(os);
        //RM_Home set in bin/unix/env script
        clb.setRmHome(rmHome);
        ArrayList<String> paPropList = new ArrayList<String>();
        if (!this.paProperties.contains(CentralPAPropertyRepository.JAVA_SECURITY_POLICY.getName())) {
            paPropList.add(CentralPAPropertyRepository.JAVA_SECURITY_POLICY.getCmdLine() + rmHome + "config" +
                os.fs + "security.java.policy-client");
        }
//        if (!this.paProperties.contains(CentralPAPropertyRepository.LOG4J.getName())) {
//            StringBuilder sb = new StringBuilder(CentralPAPropertyRepository.LOG4J.getCmdLine());
//
//            // log4j only understands urls
//            try {
//                sb.append((new File(rmHome)).toURI().toURL().toString()).append("config").append("/").append(
//                        "log").append("/").append("node.properties");
//            } catch (MalformedURLException e) {
//                throw new IllegalStateException(e);
//            }
//            paPropList.add(sb.toString());
//        }
        if (!this.paProperties.contains(CentralPAPropertyRepository.PA_CONFIGURATION_FILE.getName())) {
            paPropList.add(CentralPAPropertyRepository.PA_CONFIGURATION_FILE.getCmdLine() + rmHome +
                "config" + os.fs + "network" + os.fs + "node.ini");
        }
        if (!this.paProperties.contains(PAResourceManagerProperties.RM_HOME.getKey())) {
            paPropList.add(PAResourceManagerProperties.RM_HOME.getCmdLine() + rmHome);
        }
        if (!paProperties.isEmpty()) {
            Collections.addAll(paPropList, this.paProperties.split(" "));
        }
        clb.setPaProperties(paPropList);
        clb.setNodeName(nodeName);
        try {
            clb.setCredentialsValueAndNullOthers(new String(this.credentials.getBase64()));
        } catch (KeyException e) {
            createLostNode(nodeName, "Cannot decrypt credentials value", e);
            return;
        }
        List<String> cmd;
        try {
            cmd = clb.buildCommandLineAsList(false);
        } catch (IOException e) {
            createLostNode(nodeName, "Cannot build command line", e);
            return;
        }

        // The printed cmd with obfuscated credentials
        final String obfuscatedCmd = Tools.join(cmd, " ");

        String depNodeURL = null;
        ProcessExecutor processExecutor;
        try {
            this.isNodeAcquired.put(nodeName, false);
            depNodeURL = this.addDeployingNode(nodeName, obfuscatedCmd, "Node launched locally",
                    this.nodeTimeout);

            // Deobfuscate the cred value
            Collections.replaceAll(cmd, CommandLineBuilder.OBFUSC, clb.getCredentialsValue());

            this.isDeployingNodeLost.put(depNodeURL, false);
            processExecutor = new ProcessExecutor(nodeName, cmd, false, true);
            processExecutor.start();

            this.nodeNameToProcess.put(nodeName, processExecutor);
        } catch (IOException e) {
            String lf = System.getProperty("line.separator");
            String mess = "Cannot launch rm node " + nodeName + lf + Utils.getStacktrace(e);
            this.declareDeployingNodeLost(depNodeURL, mess);
            return;
        }

        //watching process
        int threshold = 5;
        Boolean isLost, isAcquired;
        while (((isLost = this.isDeployingNodeLost.get(depNodeURL)) != null) && !isLost &&
            ((isAcquired = this.isNodeAcquired.get(nodeName)) != null) && !isAcquired) {
            if (processExecutor.isProcessFinished()) {
                int exit = processExecutor.getExitCode();
                if (exit != 0) {
                    String lf = System.getProperty("line.separator");
                    String message = "RMNode exit code == " + exit + lf;
                    message += "Command: " + obfuscatedCmd + lf;
                    String out = Tools.join(processExecutor.getOutput(), "\n");
                    String err = Tools.join(processExecutor.getErrorOutput(), "\n");
                    message += "output: " + out + lf + "errput: " + err;
                    this.declareDeployingNodeLost(depNodeURL, message);
                }
                //nodeNameToProcess will be clean up when exiting the loop
            } else {
                logger.debug("Waiting for node " + nodeName + " acquisition");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for local process status", e);
                threshold--;
                if (threshold <= 0) {
                    break;
                }
            }
        }

        logger.debug("Local Infrastructure manager exits watching loop for node " + nodeName);
        logNodeOutput(nodeName + " output: ", processExecutor.getOutput());
        logNodeOutput(nodeName + " errput: ", processExecutor.getErrorOutput());

        if (isLost) {
            //clean up the process
            processExecutor.killProcess();
            this.nodeNameToProcess.remove(nodeName);
        }
        this.isDeployingNodeLost.remove(depNodeURL);
        this.isNodeAcquired.remove(nodeName);
    }

    private void logNodeOutput(final String prefix, List<String> nodeOutputLines) {
        if (nodeOutputLines != null) {
            for (String processOutputLine : nodeOutputLines) {
                logger.debug(prefix + processOutputLine);
            }
        }
    }

    /**
     * Creates a lost node. The deployment has failed while building the command line
     * @param message a message
     * @param e the cause
     */
    private void createLostNode(String name, String message, Throwable e) {
        this.isNodeAcquired.remove(name);
        String lf = System.getProperty("line.separator");
        String url = super.addDeployingNode(name, "deployed as daemon",
                "Deploying a new windows azure insance", this.nodeTimeout);
        String st = Utils.getStacktrace(e);
        super.declareDeployingNodeLost(url, message + lf + st);
    }

    /**
     * args[0] = credentials
     * args[1] = max nodes
     * args[2] = timeout
     * args[3] = pa props
     */
    @Override
    protected void configure(Object... args) {
        this.isDeployingNodeLost = new Hashtable<String, Boolean>();
        this.nodeNameToProcess = new Hashtable<String, ProcessExecutor>();
        this.isNodeAcquired = new Hashtable<String, Boolean>();
        int index = 0;
        try {
            this.credentials = Credentials.getCredentialsBase64((byte[]) args[index++]);
        } catch (KeyException e1) {
            throw new IllegalArgumentException("Cannot decrypt credentials", e1);
        }

        try {
            this.maxNodes = Integer.parseInt(args[index++].toString());
            this.atomicMaxNodes = new AtomicInteger(this.maxNodes);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot determine max node");
        }

        try {
            this.nodeTimeout = Integer.parseInt(args[index++].toString());
        } catch (Exception e) {
            logger.warn("Cannot determine node timeout, using default:" + this.nodeTimeout, e);
        }

        this.paProperties = args[index++].toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void notifyDeployingNodeLost(String pnURL) {
        //notifies the watching loop to exit... it will clean
        //isDeployingNodeLost & isNodeAcquired collections up.
        this.isDeployingNodeLost.put(pnURL, true);
    }

    @Override
    protected void notifyAcquiredNode(Node arg0) throws RMException {
        this.isNodeAcquired.put(arg0.getNodeInformation().getName(), true);
    }

    @Override
    public void removeNode(Node node) throws RMException {
        String nodeName = node.getNodeInformation().getName();
        logger.debug("Removing node " + node.getNodeInformation().getURL() + " from infrastructure");
        this.atomicMaxNodes.incrementAndGet();
        ProcessExecutor proc = this.nodeNameToProcess.remove(nodeName);
        if (proc != null) {
            proc.killProcess();
            logger.info("Process associated to node " + nodeName + " destroyed");
        } else {
            logger.warn("No process associated to node " + nodeName);
        }
    }

    @Override
    public String toString() {
        return "Local Infrastructure";
    }
}
