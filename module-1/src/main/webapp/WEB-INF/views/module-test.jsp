<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<html>
<head>
    <title>title</title>
</head>
<body>
    module-1 - jsp
    <c:set var="supportPage"
           value="/views/support-page.jsp" />
    <jsp:include page="${supportPage}"></jsp:include>
</body>
</html>
