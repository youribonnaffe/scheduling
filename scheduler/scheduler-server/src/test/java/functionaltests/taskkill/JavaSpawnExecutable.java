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
package functionaltests.taskkill;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.Map;

import org.ow2.proactive.scheduler.common.task.TaskResult;
import org.ow2.proactive.scheduler.common.task.executable.JavaExecutable;
import org.ow2.proactive.rm.util.process.OperatingSystem;
import org.ow2.proactive.rm.util.process.OperatingSystemFamily;
import org.ow2.proactive.rm.util.process.ProcessTreeKiller;
import org.ow2.proactive.scheduler.task.utils.ThreadReader;
import org.apache.log4j.Level;


/**
 * JavaSpawnExecutable
 *
 * @author The ProActive Team
 */
public class JavaSpawnExecutable extends JavaExecutable {

    public static String launchersDir = "/functionaltests/executables/TestSleep.exe";

    public static String nativeLinuxExecLauncher = "/functionaltests/executables/PTK_launcher.sh";
    public static String nativeWindowsExecLauncher = "/functionaltests/executables/PTK_launcher.bat";
    public static String nativeLinuxExecLauncher2 = "/functionaltests/executables/PTK_launcher2.sh";
    public static String nativeWindowsExecLauncher2 = "/functionaltests/executables/PTK_launcher2.bat";

    Integer sleep;

    String tname;

    String tmpdir = System.getProperty("java.io.tmpdir");

    File killFile;

    String home;

    @Override
    public void init(Map<String, Serializable> args) throws Exception {
        super.init(args);
        tname = (String) args.get("tname");
        killFile = new File(tmpdir, tname + ".tmp");
        if (killFile.exists()) {
            killFile.delete();
        }
    }

    @Override
    public Serializable execute(TaskResult... results) throws Throwable {

        org.apache.log4j.Logger.getLogger(ProcessTreeKiller.class).setLevel(Level.DEBUG);
        Process process = null;

        process = Runtime.getRuntime().exec(getNativeExecLauncher(false), null,
                getExecutablePath(launchersDir).getParentFile().getCanonicalFile());

        // redirect streams
        BufferedReader sout = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader serr = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        Thread tsout = new Thread(new ThreadReader(sout, System.out));
        Thread tserr = new Thread(new ThreadReader(serr, System.err));
        tsout.setDaemon(true);
        tserr.setDaemon(true);
        tsout.start();
        tserr.start();

        process.waitFor();

        Thread.sleep(sleep * 10000); // we sleep 2 sec

        return true;
    }

    /**
     * Returns the path to the native launcher script
     * In case of Native Executable normal termination test, we use a set of alternate scripts which will not run a
     * detached executable
     *
     * @param alternate to use alternate scripts
     */
    public String[] getNativeExecLauncher(boolean alternate) throws Exception {
        String osName = System.getProperty("os.name");
        OperatingSystem operatingSystem = OperatingSystem.resolveOrError(osName);
        OperatingSystemFamily family = operatingSystem.getFamily();
        String[] nativeExecLauncher = null;
        switch (family) {
            case LINUX:
            case UNIX:
            case MAC:
                String executable = null;
                if (alternate) {
                    executable = getExecutablePath(nativeLinuxExecLauncher2).getName();
                } else {
                    executable = getExecutablePath(nativeLinuxExecLauncher).getName();
                }
                // TODO runAsMe mode for this Test
                nativeExecLauncher = new String[] { "/bin/sh", executable };

                break;
            case WINDOWS:
                if (alternate) {
                    nativeExecLauncher = new String[] { "cmd.exe", "/C",
                            getExecutablePath(nativeWindowsExecLauncher2).getName() };
                } else {
                    nativeExecLauncher = new String[] { "cmd.exe", "/C",
                            getExecutablePath(nativeWindowsExecLauncher).getName() };

                }

        }
        return nativeExecLauncher;
    }

    private File getExecutablePath(String launcherPath) throws URISyntaxException {
        try {
            return new File(new File(home, "scheduler/scheduler-server/src/test/resources"), launcherPath);
        } catch (Exception e) {
            File addonsFolder = new File(home, "addons");
            return new File(addonsFolder, launcherPath);
        }
    }

    public static void main(String[] args) throws Throwable {
        JavaSpawnExecutable jse = new JavaSpawnExecutable();
        jse.sleep = 1;
        jse.execute(new TaskResult[0]);
    }
}
