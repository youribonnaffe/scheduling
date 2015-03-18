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
package org.ow2.proactive.scheduler.task.java;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.ow2.proactive.scheduler.common.task.executable.Executable;
import org.ow2.proactive.scheduler.common.task.util.ByteArrayWrapper;
import org.ow2.proactive.scheduler.task.ExecutableContainer;
import org.ow2.proactive.scheduler.util.classloading.TaskClassServer;


/**
 * This class is a container for Java executable. The actual executable is instantiated on the worker node
 * in a dedicated classloader, which can download classes from the associated classServer.
 *
 * @see TaskClassServer
 * @author The ProActive Team
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class JavaExecutableContainer extends ExecutableContainer {

    /** Arguments of the task as a map */
    protected final Map<String, ByteArrayWrapper> serializedArguments = new HashMap<String, ByteArrayWrapper>();

    // instanciated on demand : not DB managed
    protected Executable userExecutable;

    // can be null : not DB managed
    protected TaskClassServer classServer;

    /**
     * Create a new container for JavaExecutable
     * @param args the serialized arguments for Executable.init() method.
     */
    public JavaExecutableContainer(Map<String, byte[]> args) {
        for (Entry<String, byte[]> e : args.entrySet()) {
            this.serializedArguments.put(e.getKey(), new ByteArrayWrapper(e.getValue()));
        }
    }

    /**
     * Copy constructor
     * 
     * @param cont original object to copy
     */
    public JavaExecutableContainer(JavaExecutableContainer cont) {
        for (Entry<String, ByteArrayWrapper> e : cont.serializedArguments.entrySet()) {
            this.serializedArguments.put(new String(e.getKey()), new ByteArrayWrapper(e.getValue()
                    .getByteArray()));
        }
    }

    public Map<String, ByteArrayWrapper> getSerializedArguments() {
        return serializedArguments;
    }

}
