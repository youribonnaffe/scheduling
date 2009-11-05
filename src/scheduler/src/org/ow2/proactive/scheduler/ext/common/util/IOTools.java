/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2009 INRIA/University of 
 * 						   Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2. 
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s): ActiveEon Team - http://www.activeeon.com
 *
 * ################################################################
 * $$ACTIVEEON_CONTRIBUTOR$$
 */
package org.ow2.proactive.scheduler.ext.common.util;

import java.io.*;
import java.util.ArrayList;


/**
 * Utility class which performs some IO work
 *
 * @author The ProActive Team
 */
public class IOTools {

    public static ProcessResult blockingGetProcessResult(Process process) {

        final InputStream is = process.getInputStream();
        final InputStream es = process.getErrorStream();
        final ArrayList<String> out_lines = new ArrayList<String>();
        final ArrayList<String> err_lines = new ArrayList<String>();
        Thread t1 = new Thread(new Runnable() {
            public void run() {
                ArrayList<String> linesTemp = getContentAsList(is);
                out_lines.addAll(linesTemp);
            }
        });
        Thread t2 = new Thread(new Runnable() {
            public void run() {
                ArrayList<String> linesTemp = getContentAsList(es);
                err_lines.addAll(linesTemp);
            }
        });
        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();

        } catch (InterruptedException e) {
            e.printStackTrace(); //To change body of catch statement use File | Settings | File Templates.
        }

        int retValue = 0;
        try {
            retValue = process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace(); //To change body of catch statement use File | Settings | File Templates.
        }
        return new ProcessResult(retValue, out_lines.toArray(new String[0]), err_lines.toArray(new String[0]));
    }

    /**
     * Return the content read through the given text input stream as a list of file
     *
     * @param is input stream to read
     * @return content as list of strings
     */
    public static ArrayList<String> getContentAsList(final InputStream is) {
        final ArrayList<String> lines = new ArrayList<String>();
        final BufferedReader d = new BufferedReader(new InputStreamReader(new BufferedInputStream(is)));

        String line = null;

        try {
            line = d.readLine();
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        while (line != null) {
            lines.add(line);

            try {
                line = d.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                line = null;
            }
        }

        try {
            is.close();
        } catch (IOException e) {
        }

        return lines;
    }

    public static class RedirectionThread implements Runnable, Serializable {

        /**  */
        private static final long serialVersionUID = 20;
        private InputStream is;
        private OutputStream os;

        public RedirectionThread(InputStream is, OutputStream os) {
            this.is = is;
            this.os = os;
        }

        public void run() {
            BufferedReader br = new BufferedReader(new InputStreamReader(new BufferedInputStream(is)));
            PrintStream out = new PrintStream(new BufferedOutputStream(os));
            String s;
            try {
                while ((s = br.readLine()) != null) {
                    out.println(s);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * An utility class (Thread) which collects the output from a process and prints it on the JVM's standard output
     *
     * @author The ProActive Team
     */
    public static class LoggingThread implements Runnable, Serializable {

        /**  */
        private static final long serialVersionUID = 20;
        private String appendMessage;
        /**  */
        public Boolean goon = true;
        private PrintStream out;
        private BufferedReader br;

        /**  */
        public ArrayList<String> output = new ArrayList<String>();

        /**
         * Create a new instance of LoggingThread.
         */
        public LoggingThread() {

        }

        /**
         * Create a new instance of LoggingThread.
         *
         * @param is
         * @param appendMessage
         * @param err
         */
        public LoggingThread(InputStream is, String appendMessage, boolean err) {
            this.br = new BufferedReader(new InputStreamReader(is));
            this.appendMessage = appendMessage;
            if (err) {
                this.out = System.err;
            } else {
                this.out = System.out;
            }
        }

        /**
         * Create a new instance of LoggingThread.
         *
         * @param is
         * @param appendMessage
         * @param out
         */
        public LoggingThread(InputStream is, String appendMessage, PrintStream out) {
            this.br = new BufferedReader(new InputStreamReader(is));
            this.appendMessage = appendMessage;
            this.out = out;
        }

        private String getLineOrDie() {
            String answer = null;
            try {
                while (!br.ready()) {
                    Thread.sleep(10);
                }
                answer = br.readLine();

            } catch (IOException e) {

            } catch (InterruptedException e) {

            }
            return answer;
        }

        /**
         * @see java.lang.Runnable#run()
         */
        public void run() {
            String line = null;
            boolean first_line = true;
            while ((line = getLineOrDie()) != null && goon) {
                synchronized (out) {
                    if (first_line && line.trim().length() > 0) {
                        first_line = false;
                        out.println(appendMessage + line);
                        out.flush();
                    } else if (!first_line) {
                        out.println(appendMessage + line);
                        out.flush();
                    }
                }
            }

            //line = br.readLine();

            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void setStream(PrintStream st) {
            synchronized (out) {
                out = st;
            }
        }
    }
}
