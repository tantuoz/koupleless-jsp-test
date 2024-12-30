# koupleless-jsp-test
koupleless静态合并部署，模块jsp加载

PS: springboot2之后 内置容器启动(java -jar)，是无法支持访问jsp的，原因未排查，可参考博客：
https://blog.csdn.net/qq_36441169/article/details/102729248

## 打包步骤

### install 父jar
<code> mvn -s -pl /. -am clean instasll -DskipTests</code>

### install module-support
<code> mvn -s -pl ./module-support/. -am clean install -DskipTests</code>

### 打包module-1
<code> mvn -s -pl ./module-1/. -am clean package -DskipTests</code>

### 打包base
<code> mvn -s -pl /. -am clean package -DskipTests</code>

### 静态合并部署启动基座
<code>java -Dsofa.ark.embed.static.biz.enable=true -jar ./base/target/base-1.0-SNAPSHOT.jar</code>


## 服务访问

### 健康检查
<code>http://localhost:8080/actuator/health </code>

### 模块jsp访问
<code>http://localhost:8080/module1/index </code>

## 兼容改造说明

### 兼容加载模块加载自己jsp

详见：base/src/main/java/org/springframework/boot/web/servlet/server/StaticResourceJars.java

复现：删除本地工程StaticResourceJars.java，模块jsp目录将无法被加载，访问会404

### 兼容模块加载自己依赖的jar的jsp
详见：
- base/src/main/java/org/apache/tomcat/util/scan/JarFileUrlNestedJar.java
- base/src/main/java/org/apache/catalina/webresources/JarWarResourceSet.java
- base/src/main/java/org/apache/catalina/webresources/JarWarResource.java

复现1：删除本地工程JarFileUrlNestedJar.java，启动会报错

<code>java.lang.RuntimeException: Meet exception when deploying biz after embed master biz started!
at com.alipay.sofa.ark.support.startup.EmbedSofaArkBootstrap.deployStaticBizAfterEmbedMasterBizStarted(EmbedSofaArkBootstrap.java:106) ~[sofa-ark-support-starter-2.2.14.jar!/:na]
at com.alipay.sofa.ark.springboot.listener.ArkDeployStaticBizListener.onApplicationEvent(ArkDeployStaticBizListener.java:38) ~[sofa-ark-springboot-starter-2.2.14.jar!/:na]
at com.alipay.sofa.ark.springboot.listener.ArkDeployStaticBizListener.onApplicationEvent(ArkDeployStaticBizListener.java:26) ~[sofa-ark-springboot-starter-2.2.14.jar!/:na]
at org.springframework.context.event.SimpleApplicationEventMulticaster.doInvokeListener(SimpleApplicationEventMulticaster.java:176) ~[spring-context-5.3.20.jar!/:5.3.20]
at org.springframework.context.event.SimpleApplicationEventMulticaster.invokeListener(SimpleApplicationEventMulticaster.java:169) ~[spring-context-5.3.20.jar!/:5.3.20]
at org.springframework.context.event.SimpleApplicationEventMulticaster.multicastEvent(SimpleApplicationEventMulticaster.java:143) ~[spring-context-5.3.20.jar!/:5.3.20]
at org.springframework.context.support.AbstractApplicationContext.publishEvent(AbstractApplicationContext.java:421) ~[spring-context-5.3.20.jar!/:5.3.20]
at org.springframework.context.support.AbstractApplicationContext.publishEvent(AbstractApplicationContext.java:378) ~[spring-context-5.3.20.jar!/:5.3.20]
at org.springframework.context.support.AbstractApplicationContext.finishRefresh(AbstractApplicationContext.java:938) ~[spring-context-5.3.20.jar!/:5.3.20]
at org.springframework.context.support.AbstractApplicationContext.refresh(AbstractApplicationContext.java:586) ~[spring-context-5.3.20.jar!/:5.3.20]
at org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext.refresh(ServletWebServerApplicationContext.java:145) ~[spring-boot-2.5.14.jar!/:2.5.14]
at org.springframework.boot.SpringApplication.refresh(SpringApplication.java:780) ~[spring-boot-2.5.14.jar!/:2.5.14]
at org.springframework.boot.SpringApplication.refreshContext(SpringApplication.java:453) ~[spring-boot-2.5.14.jar!/:2.5.14]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:343) ~[spring-boot-2.5.14.jar!/:2.5.14]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:1370) ~[spring-boot-2.5.14.jar!/:2.5.14]
at org.springframework.boot.SpringApplication.run(SpringApplication.java:1359) ~[spring-boot-2.5.14.jar!/:2.5.14]
at com.tt.base.BaseApplication.main(BaseApplication.java:10) ~[classes!/:1.0-SNAPSHOT]
</code>

复现2：删除本地工程JarWarResourceSet.java、JarWarResource.java
访问模块jsp时，会出现报错jsp not found

<code>2024-12-30 14:37:36.648 ERROR 48043 --- [nio-8080-exec-1] o.a.c.c.C.[.[.[.[dispatcherServlet]      : Servlet.service() for servlet [dispatcherServlet] in context with path [/module1] threw exception [JSP file [&#47;views&#47;support-page.jsp] not found] with root cause

javax.servlet.ServletException: JSP file [&#47;views&#47;support-page.jsp] not found
at org.apache.jasper.servlet.JspServlet.handleMissingResource(JspServlet.java:400) ~[tomcat-embed-jasper-9.0.63.jar!/:na]
at org.apache.jasper.servlet.JspServlet.serviceJspFile(JspServlet.java:368) ~[tomcat-embed-jasper-9.0.63.jar!/:na]
at org.apache.jasper.servlet.JspServlet.service(JspServlet.java:327) ~[tomcat-embed-jasper-9.0.63.jar!/:na]
at javax.servlet.http.HttpServlet.service(HttpServlet.java:764) ~[tomcat-embed-core-9.0.63.jar!/:4.0.1]
at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:227) ~[tomcat-embed-core-9.0.63.jar!/:na]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-9.0.63.jar!/:na]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:111) ~[spring-web-5.3.20.jar!/:5.3.20]
at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:189) ~[tomcat-embed-core-9.0.63.jar!/:na]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-9.0.63.jar!/:na]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:111) ~[spring-web-5.3.20.jar!/:5.3.20]
at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:189) ~[tomcat-embed-core-9.0.63.jar!/:na]
</code>