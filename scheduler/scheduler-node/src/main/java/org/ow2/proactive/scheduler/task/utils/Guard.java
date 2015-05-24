/*
 *  *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2013 INRIA/University of
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
package org.ow2.proactive.scheduler.task.utils;

import java.io.Serializable;
import java.util.concurrent.Callable;

import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.api.PAFuture;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeException;
import org.ow2.proactive.scheduler.newimpl.TaskLauncher;
import org.apache.log4j.Logger;


/**
 * Guard : this class acts as a proxy used to control the execution of long operations inside the TaskLauncher
 * chain of execution.
 *
 * The guard is used either inside the TaskLauncher class or the JavaExecutableForker
 *
 * Depending on which context it is used, the guard controls the access to a non-thread safe target.
 *
 *
 * It uses an ActiveObject as an executor, this choice was necessary to allow the dataspace framework
 * to work properly (dataspaces require to be initialized and used within an active object)
 *
 * @author The ProActive Team
 **/
public abstract class Guard<T> {

    protected static final Logger logger = Logger.getLogger(TaskLauncher.class);

    protected GuardState state = GuardState.NOT_INITIALIZED;

    // true if the ProActive Node initialized (to handle kill messages received before the TaskLauncher initilalization)
    protected boolean nodeInitialized = false;

    // true if the target of the guard has been initialized
    protected boolean targetInitialized = false;

    // true if the guard received a kill message (controls how the cleaning is performed)
    protected boolean killMessageReceived = false;

    // true if the guard received a walltime message (controls how the cleaning is performed)
    protected boolean walltimeMessageReceived = false;

    // target of the guard, i.e. the non thread-safe object to protect
    protected T target = null;

    /**
     * Active Object used to execute long operations
     */
    // this is the reified active object
    protected ActiveObjectExecutor activeExecutor;
    // stub to the active object
    protected ActiveObjectExecutor stubActiveExecutor;

    // ProActive Node used by the active object
    protected Node node;

    // future object used to wait for completion of tasks
    protected Serializable taskExecutionFuture;

    /**
     * Initialize the Node that will be used by the guard
     */
    public synchronized void setNode(Node node) throws ActiveObjectCreationException, NodeException {
        checkNotKilledOrCleaned();
        this.node = node;
        this.activeExecutor = new ActiveObjectExecutor();
        this.stubActiveExecutor = PAActiveObject.turnActive(activeExecutor, node);
        stubActiveExecutor.ping(); // make sure the AO is started
        this.nodeInitialized = true;
    }

    private synchronized void checkNotKilledOrCleaned() {
        if (state == GuardState.KILLED) {
            throw new IllegalStateException("Task has been killed");
        }
        if (state == GuardState.CLEANED) {
            throw new IllegalStateException("Guard has been cleaned");
        }
    }

    private synchronized void checkNodeInitialized() {
        if (!nodeInitialized) {
            throw new IllegalStateException("Guard Node is not initialized");
        }
    }

    /**
     * Initialize the guard with its target
     */
    public synchronized void initialize(T target) {
        checkNotKilledOrCleaned();
        checkNodeInitialized();
        this.state = GuardState.TARGET_INITIALIZED;
        this.target = target;
        // set the context class loader of the ActiveObjectExecutor with the context class loader of the current thread.
        // (used for jobclasspath)
        activeExecutor.setContextClassLoader(Thread.currentThread().getContextClassLoader());
        this.targetInitialized = true;
    }

    /**
     * Submit a callable object to the executor
     * @param callable callable object to execute
     * @param checkInitialized true if we must check that the guard has its target initialized
     */
    protected synchronized void submitACallable(Callable callable, boolean checkInitialized) {
        check(checkInitialized); // check not killed

        activeExecutor.setCallable(callable); // Callable is not serializable so we shortcut the active object
        taskExecutionFuture = stubActiveExecutor.call(); // submit the active object to the executor
    }

    /**
     * waits until the callable completes or the guard is killed
     * @return result of computation
     * @throws Exception
     */
    protected Serializable waitCallable() throws Throwable {
        return waitCallable(-1);
    }

    /**
     * waits until the callable completes or the guard is killed or the timeout expires
     * @param timeout
     * @return result of computation
     * @throws Exception
     */
    protected Serializable waitCallable(long timeout) throws Throwable {
        Serializable answer = null;
        try {
            if (timeout > 0) {
                answer = (Serializable) PAFuture.getFutureValue(taskExecutionFuture, timeout);
            } else {
                answer = (Serializable) PAFuture.getFutureValue(taskExecutionFuture);
            }
        } catch (Exception e) {
            Throwable wrappedException = e;
            while (wrappedException != null && !(wrappedException instanceof ToUnwrapException)) {
                wrappedException = wrappedException.getCause();
            }
            if (wrappedException != null) {
                throw wrappedException.getCause();
            }
            // this should never occur, but if ever it occurs, throw the original exception
            throw e;
        }
        synchronized (this) {
            taskExecutionFuture = null;
        }
        return answer;
    }

    /**
     * check that the guard is not killed, cleaned or not initialized
     * @param checkInitialized true if we must check that the guard has its target initialized
     */
    public synchronized void check(boolean checkInitialized) {
        checkNotKilledOrCleaned();

        if (checkInitialized && state != GuardState.TARGET_INITIALIZED) {
            throw new IllegalStateException("Guard not initialized");
        }
    }

    /**
     * use the executable for other usage than executing long calls
     * @return target
     */
    public synchronized T use() {
        check(true);
        return target;
    }

    /**
     * Return true if the guard was killed
     * @return
     */
    public synchronized boolean wasKilled() {
        return killMessageReceived;
    }

    /**
     * Return true if the guard was walltimed
     * @return
     */
    public synchronized boolean wasWalltimed() {
        return walltimeMessageReceived;
    }

    /**
     * abstract kill method to be overridden by implementations, for example when the target is an Executable,
     * it will call the genereic or user-defined kill method of the Executable class
     */
    abstract protected void internalKill();

    /**
     * abstract clean method to be overridden by implementations
     */
    abstract protected void internalClean();

    /**
     * Kill the executable, this will interrupt current tasks.
     * Normally, clean should be called right afterwards
     */
    public synchronized void kill(boolean isWalltime) {
        checkNotKilledOrCleaned();
        logger.info("Kill message received...");

        try {
            if (targetInitialized) {
                internalKill();
            }
        } catch (Exception e) {
            logger.warn(e);
        }

        if (nodeInitialized) {
            // this will send an interrupt message to the executor, this message will unblock
            // wait, sleep or join calls, the unblocking of I/O is OS-dependant and will in most cases not work.
            // In the case of a forked java task this would not be a problem, as the process would be destroyed brutally,
            // in case of a standard java task, maybe a solution could be to check the state of the thread after the interrupt and if it is not interrupted properly,
            // kill the Node (System.exit)
            activeExecutor.cancel();
        }

        this.state = GuardState.KILLED;
        killMessageReceived = true;
        walltimeMessageReceived = isWalltime;
    }

    /**
     * Cleans the guard, no other call will be possible afterwards, the clean operation is timed to avoid blocking the node
     */
    public synchronized void clean(long timeout) {
        if (this.state != GuardState.CLEANED) {
            if (activeExecutor != null) {

                activeExecutor.setCallable(new Callable<Serializable>() {
                    @Override
                    public Serializable call() throws Exception {
                        try {
                            internalClean();
                        } catch (Exception e) {
                            logger.warn(e);
                        }
                        return null;
                    }
                });
                try {
                    taskExecutionFuture = stubActiveExecutor.call();
                    waitCallable(timeout);
                } catch (Throwable e) {
                    logger.warn(e);
                }
                shutdown(true);
            }

            this.state = GuardState.CLEANED;
        }
    }

    public synchronized void shutdown(boolean immediate) {
        if (activeExecutor != null) {
            try {
                PAActiveObject.terminateActiveObject(stubActiveExecutor, immediate);
            } catch (Throwable t) {
                logger.warn("Could not terminate guard active object", t);
                // we eat any termination exceptions
            }
        }
    }

    public static class ToUnwrapException extends RuntimeException {
        public ToUnwrapException(Throwable e) {
            super(e);
        }
    }

    /**
     * State of the Guard
     */
    protected enum GuardState {
        /**
         * Guard not initialized
         */
        NOT_INITIALIZED,

        /**
         * Guard initialized (can be used)
         */
        TARGET_INITIALIZED,

        /**
         * Guard killed (cannot be used)
         */
        KILLED,
        /**
         * Guard cleaned (cannot be used, nor killed)
         */
        CLEANED
    }

}
