/*
 *  *
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
 *  * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive_grid_cloud_portal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;

import org.objectweb.proactive.core.config.CentralPAPropertyRepository;
import Acme.Serve.Serve;
import org.jboss.resteasy.plugins.server.tjws.TJWSEmbeddedJaxrsServer;
import org.jboss.resteasy.plugins.server.tjws.TJWSServletServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;


public class RestTestServer {
    protected static int port;
    private static TJWSEmbeddedJaxrsServer server;

    protected static ByteArrayOutputStream serverLogs = new ByteArrayOutputStream();

    @BeforeClass
    public static void startServer() throws IOException, NoSuchFieldException, IllegalAccessException {
        bypassProActiveLogger();
        preventProActiveToChangeSecurityManager();
        server = new TJWSEmbeddedJaxrsServer();
        silentServerError();

        port = findFreePort();
        server.setPort(port);
        server.setRootResourcePath("/");
        server.start();
    }

    private static void bypassProActiveLogger() {
        System.setProperty("log4j.configuration", "log4j.properties");
    }

    private static void preventProActiveToChangeSecurityManager() {
        CentralPAPropertyRepository.PA_CLASSLOADING_USEHTTP.setValue(false);
    }

    /**
     * Use reflection to access private fields of the underlying server.
     */
    private static void silentServerError() throws NoSuchFieldException, IllegalAccessException {
        Field f = TJWSServletServer.class.getDeclaredField("server");
        f.setAccessible(true);
        TJWSServletServer.FileMappingServe serve = (TJWSServletServer.FileMappingServe) f.get(server);

        Field streamField = Serve.class.getDeclaredField("logStream");
        streamField.setAccessible(true);

        streamField.set(serve, new PrintStream(serverLogs));
    }

    protected static void addResource(Object restResource) {
        server.getDeployment().getDispatcher().getRegistry().addSingletonResource(restResource);
    }

    @AfterClass
    public static void stopServer() {
        server.stop();
    }

    private static int findFreePort() throws IOException {
        ServerSocket server = new ServerSocket(0);
        int port = server.getLocalPort();
        server.close();
        return port;
    }

}
