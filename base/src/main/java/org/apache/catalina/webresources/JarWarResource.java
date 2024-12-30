/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.webresources;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.UriUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

/**
 * Represents a single resource (file or directory) that is located within a
 * JAR that in turn is located in a WAR file.
 */
public class JarWarResource extends AbstractArchiveResource {

    private static final Log log = LogFactory.getLog(JarWarResource.class);

    private final String archivePath;

    public JarWarResource(AbstractArchiveResourceSet archiveResourceSet, String webAppPath,
            String baseUrl, JarEntry jarEntry, String archivePath) {

        super(archiveResourceSet, webAppPath,
                "jar:war:" + baseUrl + UriUtil.getWarSeparator() + archivePath + "!/",
                jarEntry, "war:" + baseUrl + UriUtil.getWarSeparator() + archivePath);
        this.archivePath = archivePath;
    }

    @Override
    protected JarInputStreamWrapper getJarInputStreamWrapper() {
        JarFile warFile = null;
        JarInputStream jarIs = null;
        JarEntry entry = null;
        try {
            // 兼容基座部署模块，模块依赖的包能够加载到所依赖jar的资源目录
            // 模块执行到此处，拿到的 warFile 是基座的，但 archivePath 是 BOOT-INF/classes/SOFA-ARK/biz/module-ark-biz.jar!/lib/module-support.jar
            // 将无法拿到正确的目录
            // 修改：通过先获取到模块jar，即 warFile 是模块，再拆分出 lib/module-support.jar 为 archivePath，从而拿到依赖jar的资源目录
            InputStream isInWar;
            String archivePathTemp = archivePath;
            if (archivePathTemp.indexOf("!/") > 0) {
                String base = getBase();
                int directoryIndex = archivePathTemp.lastIndexOf("!/");
                int realArchivePathStart = directoryIndex + 2;
                archivePathTemp = archivePath.substring(realArchivePathStart);
                String warPath = "jar:file:" + base + "!/" + archivePath.substring(0, directoryIndex) + "!/";
                URL url = new URL(warPath);
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                warFile = connection.getJarFile();
                JarEntry jarFileInWar = warFile.getJarEntry(archivePathTemp);
                isInWar = warFile.getInputStream(jarFileInWar);
            } else {
                // else 块为 源码
                warFile = getArchiveResourceSet().openJarFile();
                JarEntry jarFileInWar = warFile.getJarEntry(archivePath);
                isInWar = warFile.getInputStream(jarFileInWar);
            }

            jarIs = new JarInputStream(isInWar);
            entry = jarIs.getNextJarEntry();
            while (entry != null &&
                    !entry.getName().equals(getResource().getName())) {
                entry = jarIs.getNextJarEntry();
            }

            if (entry == null) {
                return null;
            }

            return new JarInputStreamWrapper(entry, jarIs);
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("jarResource.getInputStreamFail",
                        getResource().getName(), getBaseUrl()), e);
            }
            // Ensure jarIs is closed if there is an exception
            entry = null;
            return null;
        } finally {
            if (entry == null) {
                if (jarIs != null) {
                    try {
                        jarIs.close();
                    } catch (IOException ioe) {
                        // Ignore
                    }
                }
                if (warFile != null) {
                    getArchiveResourceSet().closeJarFile();
                }
            }
        }
    }

    @Override
    protected Log getLog() {
        return log;
    }
}
