package org.apache.tomcat.util.scan;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Implementation of {@link org.apache.tomcat.Jar} that is optimised for file
 * based JAR URLs that refer to a JAR file nested inside a WAR
 * (e.g URLs of the form jar:file: ... .war!/ ... .jar).
 */
public class JarFileUrlNestedJar extends AbstractInputStreamJar {

    private final JarFile warFile;
    private final JarEntry jarEntry;

    public JarFileUrlNestedJar(URL url) throws IOException {
        super(url);
        JarURLConnection jarConn = (JarURLConnection) url.openConnection();
        jarConn.setUseCaches(false);
        warFile = jarConn.getJarFile();

        // origin code:
        // String urlAsString = url.toString();
        // int pathStart = urlAsString.indexOf("!/") + 2;
        // String jarPath = urlAsString.substring(pathStart);

        // 兼容基座部署模块，模块依赖的包能够正确获取到目录
        // 如：加载模块jar，目录为：jar:file:/**/hs-hk-admin-base/target/base-0.0.1-SNAPSHOT.jar!/BOOT-INF/classes/SOFA-ARK/biz/module-ark-biz.jar!/lib/module-support.jar
        // jarPath将得出结果：/BOOT-INF/classes/SOFA-ARK/biz/module-ark-biz.jar!/lib/module-support.jar
        // 并不能拿到 /lib/module-support.jar ，导致后续无法加载到 module-support.jar 的资源
        // 修改为获取最后 匹配到 "!/" 的后面的目录地址

        // compatible code:
        String urlAsString = url.toString();
        int pathStart = urlAsString.lastIndexOf("!/") + 2;
        String jarPath = urlAsString.substring(pathStart);

        jarEntry = warFile.getJarEntry(jarPath);
    }


    @Override
    public void close() {
        closeStream();
        if (warFile != null) {
            try {
                warFile.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }


    @Override
    protected NonClosingJarInputStream createJarInputStream() throws IOException {
        return new NonClosingJarInputStream(warFile.getInputStream(jarEntry));
    }
}