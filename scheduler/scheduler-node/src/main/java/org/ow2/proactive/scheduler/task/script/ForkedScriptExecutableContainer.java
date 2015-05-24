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
package org.ow2.proactive.scheduler.task.script;

import java.io.Serializable;
import java.util.Collections;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.ow2.proactive.scheduler.common.exception.ExecutableCreationException;
import org.ow2.proactive.scheduler.common.task.executable.internal.JavaExecutableInitializerImpl;
import org.ow2.proactive.scheduler.task.forked.ForkedJavaExecutableContainer;
import org.ow2.proactive.scheduler.task.forked.JavaForkerExecutable;
import org.ow2.proactive.scheduler.task.java.JavaExecutableContainer;
import org.ow2.proactive.scripting.InvalidScriptException;
import org.ow2.proactive.scripting.Script;
import org.ow2.proactive.scripting.TaskScript;
import org.apache.log4j.Logger;


/**
 * This class is a container for forked Script executable. The actual executable is instantiated on the worker node
 * .<br>
 * In this case an other JVM is started from the current one, and the task itself is deployed on the new JVM.<br>
 * As a consequence, we keep control on the forked JVM, can kill the process or give a new brand environment to the user
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 3.4
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ForkedScriptExecutableContainer extends ForkedJavaExecutableContainer {

    public static final Logger logger = Logger.getLogger(ForkedScriptExecutableContainer.class);

    private TaskScript script;

    public ForkedScriptExecutableContainer(TaskScript script) {
        super(JavaForkerExecutable.class.getName(), Collections.<String, byte[]> emptyMap());
        this.script = script;
    }

    /**
     * Copy constructor
     *
     * @param cont original object to copy
     */
    public ForkedScriptExecutableContainer(ForkedScriptExecutableContainer cont)
            throws ExecutableCreationException {
        super(JavaForkerExecutable.class.getName(), Collections.<String, byte[]> emptyMap());
        try {
            this.script = new TaskScript(cont.getScript());
        } catch (InvalidScriptException e) {
            throw new ExecutableCreationException("Could not copy script", e);
        }
    }

    /**
     * @see org.ow2.proactive.scheduler.task.ExecutableContainer#createExecutableInitializer()
     */
    @Override
    public ScriptExecutableInitializer createExecutableInitializer() {
        JavaExecutableInitializerImpl jei = super.createExecutableInitializer();
        ScriptExecutableInitializer fjei = new ScriptExecutableInitializer(jei);
        fjei.setScript(script);
        JavaExecutableContainer newjec = new ScriptExecutableContainer(script);
        newjec.setCredentials(this.getCredentials());
        newjec.setRunAsUser(this.isRunAsUser());
        fjei.setJavaExecutableContainer(newjec);
        return fjei;
    }

    public Script<Serializable> getScript() {
        return script;
    }
}
