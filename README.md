# koupleless-jsp-test
koupleless静态合并部署，模块jsp加载

打包module-1

<code> mvn -s -pl /. -am clean package -DskipTests</code>

打包base

<code> mvn -s -pl /. -am clean package -DskipTests</code>

健康检查

http://localhost:8080/actuator/health