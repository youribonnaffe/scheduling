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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.proactive.extensions.dataspaces.api.DataSpacesFileObject;
import org.objectweb.proactive.extensions.dataspaces.api.FileSelector;
import org.objectweb.proactive.extensions.dataspaces.api.FileType;
import org.objectweb.proactive.extensions.dataspaces.exceptions.DataSpacesException;
import org.objectweb.proactive.extensions.dataspaces.vfs.selector.fast.FastFileSelector;
import org.objectweb.proactive.extensions.dataspaces.vfs.selector.fast.FastSelector;
import org.objectweb.proactive.utils.OperatingSystem;
import org.objectweb.proactive.utils.StackTraceUtil;
import org.ow2.proactive.scheduler.common.task.dataspaces.FileSystemException;
import org.ow2.proactive.scheduler.common.task.dataspaces.RemoteSpace;
import org.apache.log4j.Logger;


/**
 * SpaceAdapter
 *
 * @author The ProActive Team
 **/
public class RemoteSpaceAdapter implements RemoteSpace {

    protected static final Logger logger = Logger.getLogger(RemoteSpaceAdapter.class);

    protected DataSpacesFileObject remoteDataSpace;

    protected DataSpacesFileObject localDataSpace;

    public RemoteSpaceAdapter(DataSpacesFileObject remoteDataSpace, DataSpacesFileObject localDataSpace) {
        this.remoteDataSpace = remoteDataSpace;
        this.localDataSpace = localDataSpace;
    }

    public static String stripLeadingSlash(String path) {
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        return path;
    }

    public static String addTrailingSlash(String path) {
        if (path.length() > 0 && !path.endsWith("/")) {
            return path + "/";
        }
        return path;
    }

    public static ArrayList<DataSpacesFileObject> getFilesFromPattern(DataSpacesFileObject root,
            String pattern)
            throws org.objectweb.proactive.extensions.dataspaces.exceptions.FileSystemException {
        FastFileSelector fast = new FastFileSelector();
        fast.setIncludes(new String[] { pattern });
        if (OperatingSystem.getOperatingSystem() == OperatingSystem.unix) {
            fast.setCaseSensitive(true);
        } else {
            fast.setCaseSensitive(false);
        }
        ArrayList<DataSpacesFileObject> results = new ArrayList<DataSpacesFileObject>();
        FastSelector.findFiles(root, fast, true, results);
        return results;
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

    private String copyFileToFile(DataSpacesFileObject source, DataSpacesFileObject destination)
            throws FileSystemException {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Copying " + source.getRealURI() + " to " + destination.getRealURI());
            }
            destination.copyFrom(source, FileSelector.SELECT_SELF);

            return convertDataSpaceToFileIfPossible(destination, false);

        } catch (Exception e) {
            throw new FileSystemException(StackTraceUtil.getStackTrace(e));
        }
    }

    private String copyFileToFolder(DataSpacesFileObject source, DataSpacesFileObject destination)
            throws FileSystemException {
        try {
            destination = destination.resolveFile(source.getBaseName());
            if (logger.isDebugEnabled()) {
                logger.debug("Copying " + source.getRealURI() + " to " + destination.getRealURI());
            }
            destination.copyFrom(source, FileSelector.SELECT_SELF);

            return convertDataSpaceToFileIfPossible(destination, false);
        } catch (Exception e) {
            throw new FileSystemException(StackTraceUtil.getStackTrace(e));
        }
    }

    private String copyFolderToFolder(DataSpacesFileObject source, DataSpacesFileObject destination)
            throws FileSystemException {
        try {
            if (!destination.getBaseName().equals(source.getBaseName())) {
                destination = destination.resolveFile(source.getBaseName());
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Copying " + source.getRealURI() + " to " + destination.getRealURI());
            }
            destination.copyFrom(source, FileSelector.SELECT_ALL);

            return convertDataSpaceToFileIfPossible(destination, false);

        } catch (Exception e) {
            throw new FileSystemException(StackTraceUtil.getStackTrace(e));
        }
    }

    private File convertToRelative(File absolutePath) throws URISyntaxException, DataSpacesException {
        String relPath = absolutePath.getPath().replace(
          convertDataSpaceToFileIfPossible(localDataSpace, true), "");
        if (relPath.startsWith(File.separator)) {
            relPath = relPath.substring(1);
        }
        return new File(relPath);
    }

    @Override
    public void pushFile(File localFile, String remotePath) throws FileSystemException {
        try {
            if (localFile.isAbsolute()) {
                localFile = convertToRelative(localFile);
            }
            DataSpacesFileObject destination = remoteDataSpace.resolveFile(stripLeadingSlash(remotePath));
            DataSpacesFileObject source = localDataSpace.resolveFile(localFile.toString());

            handleCopy(source, destination);

        } catch (Exception e) {
            throw new FileSystemException(StackTraceUtil.getStackTrace(e));
        }
    }

    public void pushFiles(String pattern, String remotePath) throws FileSystemException {
        try {
            remotePath = addTrailingSlash(stripLeadingSlash(remotePath));
            ArrayList<DataSpacesFileObject> sources = getFilesFromPattern(localDataSpace, pattern);
            DataSpacesFileObject destinationRoot = remoteDataSpace.resolveFile(remotePath);

            for (DataSpacesFileObject source : sources) {
                DataSpacesFileObject destination = destinationRoot.resolveFile(stripLeadingSlash(source
                        .getPath()));
                if (logger.isDebugEnabled()) {
                    logger.debug("Copying " + source.getRealURI() + " to " + destination.getRealURI());
                }
                destination.copyFrom(source, FileSelector.SELECT_SELF);
            }
        } catch (org.objectweb.proactive.extensions.dataspaces.exceptions.FileSystemException e) {
            throw new FileSystemException(StackTraceUtil.getStackTrace(e));
        }
    }

    private String handleCopy(DataSpacesFileObject source, DataSpacesFileObject destination)
            throws org.objectweb.proactive.extensions.dataspaces.exceptions.FileSystemException,
            FileSystemException {
        switch (source.getType()) {
            case FILE:
                switch (destination.getType()) {
                    case FILE:
                        return copyFileToFile(source, destination);
                    case FOLDER:
                        return copyFileToFolder(source, destination);
                    case ABSTRACT:
                        if (destination.getPath().endsWith("/")) {
                            return copyFileToFolder(source, destination);
                        } else {
                            return copyFileToFile(source, destination);
                        }
                    default:
                        throw new IllegalArgumentException("Illegal destination type : " +
                            destination.getType());
                }
            case FOLDER:
                switch (destination.getType()) {
                    case FILE:
                        throw new IllegalArgumentException("Illegal copy of Folder " + source.getRealURI() +
                            " to File" + destination.getRealURI());
                    case FOLDER:
                        return copyFolderToFolder(source, destination);
                    case ABSTRACT:
                        return copyFolderToFolder(source, destination);
                    default:
                        throw new IllegalArgumentException("Illegal destination type : " +
                            destination.getType());
                }
            default:
                throw new IllegalArgumentException("Illegal source type : " + source.getType());
        }
    }

    @Override
    public File pullFile(String remotePath, File localFile) throws FileSystemException {
        try {
            if (localFile.isAbsolute()) {
                localFile = convertToRelative(localFile);
            }
            DataSpacesFileObject source = remoteDataSpace.resolveFile(stripLeadingSlash(remotePath));
            DataSpacesFileObject destination = localDataSpace.resolveFile(localFile.toString());

            return new File(handleCopy(source, destination));
        } catch (Exception e) {
            throw new FileSystemException(StackTraceUtil.getStackTrace(e));
        }
    }

    @Override
    public Set<File> pullFiles(String pattern, String localPath) throws FileSystemException {
        HashSet<File> filePulled = new HashSet<File>();
        try {
            localPath = stripLeadingSlash(localPath);
            ArrayList<DataSpacesFileObject> sources = getFilesFromPattern(remoteDataSpace, pattern);
            DataSpacesFileObject destinationRoot = localDataSpace.resolveFile(localPath);

            for (DataSpacesFileObject source : sources) {
                DataSpacesFileObject destination = destinationRoot.resolveFile(stripLeadingSlash(source
                        .getPath()));
                if (logger.isDebugEnabled()) {
                    logger.debug("Copying " + source.getRealURI() + " to " + destination.getRealURI());
                }
                destination.copyFrom(source, FileSelector.SELECT_SELF);
                filePulled.add(new File(convertDataSpaceToFileIfPossible(destination, false)));
            }
        } catch (Exception e) {
            throw new FileSystemException(StackTraceUtil.getStackTrace(e));
        }
        return filePulled;
    }

    @Override
    public void deleteFile(String remotePath) throws FileSystemException {
        try {
            DataSpacesFileObject todelete = remoteDataSpace.resolveFile(stripLeadingSlash(remotePath));
            todelete.delete(FileSelector.SELECT_ALL);
        } catch (org.objectweb.proactive.extensions.dataspaces.exceptions.FileSystemException e) {
            throw new FileSystemException(StackTraceUtil.getStackTrace(e));
        }
    }

    @Override
    public void deleteFiles(String pattern) throws FileSystemException {
        try {
            ArrayList<DataSpacesFileObject> todelete = getFilesFromPattern(remoteDataSpace, pattern);
            for (DataSpacesFileObject dest : todelete) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Deleting " + dest.getRealURI());
                }
                dest.delete();
            }
        } catch (org.objectweb.proactive.extensions.dataspaces.exceptions.FileSystemException e) {
            throw new FileSystemException(StackTraceUtil.getStackTrace(e));
        }
    }

    @Override
    public String getSpaceURL() {
        return remoteDataSpace.getRealURI();
    }

    @Override
    public InputStream getInputStream(String remotePath) throws FileSystemException {
        try {
            DataSpacesFileObject tostream = remoteDataSpace.resolveFile(stripLeadingSlash(remotePath));
            if (!tostream.exists()) {
                throw new FileSystemException("File " + tostream.getRealURI() + " does not exist");
            }
            if (!(tostream.getType() == FileType.FILE)) {
                throw new FileSystemException("File " + tostream.getRealURI() + " is not a file (" +
                    tostream.getType() + ")");
            }
            return tostream.getContent().getInputStream();

        } catch (org.objectweb.proactive.extensions.dataspaces.exceptions.FileSystemException e) {
            throw new FileSystemException(StackTraceUtil.getStackTrace(e));
        }
    }

    @Override
    public OutputStream getOutputStream(String remotePath) throws FileSystemException {
        try {
            DataSpacesFileObject tostream = remoteDataSpace.resolveFile(stripLeadingSlash(remotePath));
            if (!tostream.exists()) {
                tostream.createFile();
                tostream = remoteDataSpace.resolveFile(stripLeadingSlash(remotePath));
            }
            if (!(tostream.getType() == FileType.FILE)) {
                throw new FileSystemException("File " + tostream.getRealURI() + " is not a file (" +
                    tostream.getType() + ")");
            }
            if (!tostream.isWritable()) {
                throw new FileSystemException("File " + tostream.getRealURI() + " is read-only.");
            }
            return tostream.getContent().getOutputStream();

        } catch (org.objectweb.proactive.extensions.dataspaces.exceptions.FileSystemException e) {
            throw new FileSystemException(StackTraceUtil.getStackTrace(e));
        }
    }

}
