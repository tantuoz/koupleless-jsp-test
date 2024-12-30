# koupleless-jsp-test
koupleless静态合并部署，模块jsp加载

打包module-1

<code> mvn -s -pl /. -am clean package -DskipTests</code>

打包base

<code> mvn -s -pl /. -am clean package -DskipTests</code>

健康检查

http://localhost:8080/actuator/health


### 兼容加载模块加载自己jsp
详见：base/src/main/java/org/springframework/boot/web/servlet/server/StaticResourceJars.java

### 兼容模块加载自己依赖的jar的jsp
详见：
- base/src/main/java/org/apache/tomcat/util/scan/JarFileUrlNestedJar.java
- base/src/main/java/org/apache/catalina/webresources/JarWarResourceSet.java
- base/src/main/java/org/apache/catalina/webresources/JarWarResource.java