package org.apache.catalina.webresources;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.compat.JreCompat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * Represents a {@link org.apache.catalina.WebResourceSet} based on a JAR file
 * that is nested inside a packed WAR file. This is only intended for internal
 * use within Tomcat and therefore cannot be created via configuration.
 */
public class JarWarResourceSet extends AbstractArchiveResourceSet {

    private final String archivePath;

    /**
     * Creates a new {@link org.apache.catalina.WebResourceSet} based on a JAR
     * file that is nested inside a WAR.
     *
     * @param root          The {@link WebResourceRoot} this new
     *                          {@link org.apache.catalina.WebResourceSet} will
     *                          be added to.
     * @param webAppMount   The path within the web application at which this
     *                          {@link org.apache.catalina.WebResourceSet} will
     *                          be mounted.
     * @param base          The absolute path to the WAR file on the file system
     *                          in which the JAR is located.
     * @param archivePath   The path within the WAR file where the JAR file is
     *                          located.
     * @param internalPath  The path within this new {@link
     *                          org.apache.catalina.WebResourceSet} where
     *                          resources will be served from. E.g. for a
     *                          resource JAR, this would be "META-INF/resources"
     *
     * @throws IllegalArgumentException if the webAppMount or internalPath is
     *         not valid (valid paths must start with '/')
     */
    public JarWarResourceSet(WebResourceRoot root, String webAppMount,
            String base, String archivePath, String internalPath)
            throws IllegalArgumentException {
        setRoot(root);
        setWebAppMount(webAppMount);
        setBase(base);
        this.archivePath = archivePath;
        setInternalPath(internalPath);

        if (getRoot().getState().isAvailable()) {
            try {
                start();
            } catch (LifecycleException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    protected WebResource createArchiveResource(JarEntry jarEntry,
            String webAppPath, Manifest manifest) {
        return new JarWarResource(this, webAppPath, getBaseUrlString(), jarEntry, archivePath);
    }


    /**
     * {@inheritDoc}
     * <p>
     * JarWar can't optimise for a single resource so the Map is always
     * returned.
     */
    @Override
    protected Map<String,JarEntry> getArchiveEntries(boolean single) {
        synchronized (archiveLock) {
            if (archiveEntries == null) {
                JarFile warFile = null;
                InputStream jarFileIs = null;
                archiveEntries = new HashMap<>();
                boolean multiRelease = false;
                try {
                    // 兼容基座部署模块，模块依赖的包能够加载到所依赖jar的资源目录
                    // 模块执行到此处，拿到的 warFile 是基座的，但 archivePath 是 BOOT-INF/classes/SOFA-ARK/biz/module-ark-biz.jar!/lib/module-support.jar
                    // 将无法拿到正确的目录
                    // 修改：通过先获取到模块jar，即 warFile 是模块，再拆分出 lib/module-support.jar 为 archivePath，从而拿到依赖jar的资源目录
                    String archivePathTemp = archivePath;
                    if (archivePathTemp.indexOf("!/") > 0) {
                        String base = getBase();
                        int directoryIndex = archivePathTemp.lastIndexOf("!/");
                        int realArchivePathStart = directoryIndex + 2;
                        archivePathTemp = archivePath.substring(realArchivePathStart);
                        String warPath = "jar:file:" + base + "!/" + archivePath.substring(0, directoryIndex) + "!/";
                        try {
                            URL url = new URL(warPath);
                            JarURLConnection connection = (JarURLConnection) url.openConnection();
                            warFile = connection.getJarFile();
                            JarEntry jarFileInWar = warFile.getJarEntry(archivePathTemp);
                            jarFileIs = warFile.getInputStream(jarFileInWar);
                        } catch (Exception e) {
                            throw new IllegalArgumentException(e);
                        }
                    } else {
                        // else 块为 源码
                        warFile = openJarFile();
                        JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
                        jarFileIs = warFile.getInputStream(jarFileInWar);
                    }

                    try (TomcatJarInputStream jarIs = new TomcatJarInputStream(jarFileIs)) {
                        JarEntry entry = jarIs.getNextJarEntry();
                        while (entry != null) {
                            archiveEntries.put(entry.getName(), entry);
                            entry = jarIs.getNextJarEntry();
                        }
                        Manifest m = jarIs.getManifest();
                        setManifest(m);
                        if (m != null && JreCompat.isJre9Available()) {
                            String value = m.getMainAttributes().getValue("Multi-Release");
                            if (value != null) {
                                multiRelease = Boolean.parseBoolean(value);
                            }
                        }
                        // Hack to work-around JarInputStream swallowing these
                        // entries. TomcatJarInputStream is used above which
                        // extends JarInputStream and the method that creates
                        // the entries over-ridden so we can a) tell if the
                        // entries are present and b) cache them so we can
                        // access them here.
                        entry = jarIs.getMetaInfEntry();
                        if (entry != null) {
                            archiveEntries.put(entry.getName(), entry);
                        }
                        entry = jarIs.getManifestEntry();
                        if (entry != null) {
                            archiveEntries.put(entry.getName(), entry);
                        }
                    }
                    if (multiRelease) {
                        processArchivesEntriesForMultiRelease();
                    }
                } catch (IOException ioe) {
                    // Should never happen
                    archiveEntries = null;
                    throw new IllegalStateException(ioe);
                } finally {
                    if (warFile != null) {
                        closeJarFile();
                    }
                    if (jarFileIs != null) {
                        try {
                            jarFileIs.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                }
            }
            return archiveEntries;
        }
    }


    protected void processArchivesEntriesForMultiRelease() {

        int targetVersion = JreCompat.getInstance().jarFileRuntimeMajorVersion();

        Map<String,VersionedJarEntry> versionedEntries = new HashMap<>();
        Iterator<Entry<String,JarEntry>> iter = archiveEntries.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String,JarEntry> entry = iter.next();
            String name = entry.getKey();
            if (name.startsWith("META-INF/versions/")) {
                // Remove the multi-release version
                iter.remove();

                // Get the base name and version for this versioned entry
                int i = name.indexOf('/', 18);
                if (i > 0) {
                    String baseName = name.substring(i + 1);
                    int version = Integer.parseInt(name.substring(18, i));

                    // Ignore any entries targeting for a later version than
                    // the target for this runtime
                    if (version <= targetVersion) {
                        VersionedJarEntry versionedJarEntry = versionedEntries.get(baseName);
                        if (versionedJarEntry == null) {
                            // No versioned entry found for this name. Create
                            // one.
                            versionedEntries.put(baseName,
                                    new VersionedJarEntry(version, entry.getValue()));
                        } else {
                            // Ignore any entry for which we have already found
                            // a later version
                            if (version > versionedJarEntry.getVersion()) {
                                // Replace the entry targeted at an earlier
                                // version
                                versionedEntries.put(baseName,
                                        new VersionedJarEntry(version, entry.getValue()));
                            }
                        }
                    }
                }
            }
        }

        for (Entry<String,VersionedJarEntry> versionedJarEntry : versionedEntries.entrySet()) {
            archiveEntries.put(versionedJarEntry.getKey(),
                    versionedJarEntry.getValue().getJarEntry());
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * Should never be called since {@link #getArchiveEntries(boolean)} always
     * returns a Map.
     */
    @Override
    protected JarEntry getArchiveEntry(String pathInArchive) {
        throw new IllegalStateException(sm.getString("jarWarResourceSet.codingError"));
    }


    @Override
    protected boolean isMultiRelease() {
        // This always returns false otherwise the superclass will call
        // #getArchiveEntry(String)
        return false;
    }


    //-------------------------------------------------------- Lifecycle methods
    @Override
    protected void initInternal() throws LifecycleException {

        String archivePathTemp = archivePath;
        // 兼容基座部署模块，模块依赖的包能够加载到所依赖jar的资源目录
        // 模块执行到此处，拿到的 warFile 是基座的，但 archivePath 是 BOOT-INF/classes/SOFA-ARK/biz/module-ark-biz.jar!/lib/module-support.jar
        // 将无法拿到正确的目录
        // 修改：通过先获取到模块jar，即 warFile 是模块，再拆分出 lib/module-support.jar 为 archivePath，从而拿到依赖jar的资源目录
        if (archivePathTemp.indexOf("!/") > 0) {
            String base = getBase();
            int directoryIndex = archivePathTemp.lastIndexOf("!/");
            int realArchivePathStart = directoryIndex + 2;
            archivePathTemp = archivePath.substring(realArchivePathStart);
            String warPath = "jar:file:" + base + "!/" + archivePath.substring(0, directoryIndex) + "!/";
            try {
                URL url = new URL(warPath);
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                JarFile warFile = connection.getJarFile();
                JarEntry jarFileInWar = warFile.getJarEntry(archivePathTemp);
                InputStream jarFileIs = warFile.getInputStream(jarFileInWar);
                try (JarInputStream jarIs = new JarInputStream(jarFileIs)) {
                    setManifest(jarIs.getManifest());
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }

            try {
                setBaseUrl(UriUtil.buildJarSafeUrl(new File(getBase())));
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            // else 块为 源码
            try (JarFile warFile = new JarFile(getBase())) {
                JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
                InputStream jarFileIs = warFile.getInputStream(jarFileInWar);

                try (JarInputStream jarIs = new JarInputStream(jarFileIs)) {
                    setManifest(jarIs.getManifest());
                }
            } catch (IOException ioe) {
                throw new IllegalArgumentException(ioe);
            }

            try {
                setBaseUrl(UriUtil.buildJarSafeUrl(new File(getBase())));
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }


    private static final class VersionedJarEntry {
        private final int version;
        private final JarEntry jarEntry;

        public VersionedJarEntry(int version, JarEntry jarEntry) {
            this.version = version;
            this.jarEntry = jarEntry;
        }


        public int getVersion() {
            return version;
        }


        public JarEntry getJarEntry() {
            return jarEntry;
        }
    }
}