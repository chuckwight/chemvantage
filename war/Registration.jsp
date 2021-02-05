<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.*,com.googlecode.objectify.*,org.chemvantage.*"%>

<%  String message = request.getParameter("message");
	String sub = request.getParameter("sub");
	String email = request.getParameter("email");
	String typ = request.getParameter("typ");
	String aud = request.getParameter("aud");
	String url = request.getParameter("url");
	String use = request.getParameter("use");
	if (use==null) use = request.getServerName().contains("dev-vantage")?"test":"prod";
	String ver = request.getParameter("ver");
	String lms = request.getParameter("lms");
	String lms_other = request.getParameter("lms_other");
	String AcceptChemVantageTOS = request.getParameter("AcceptChemVantageTOS");
	String openid_configuration = request.getParameter("openid_configuration");
	String registration_token = request.getParameter("registration_token");
%>

<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta http-equiv='Cache-Control' content='no-cache, no-store, must-revalidate' />
<meta http-equiv='Pragma' content='no-cache' />
<meta http-equiv='Expires' content='0' /><meta http-equiv='Content-type' content='text/html;charset=iso-8859-1'>
<meta name='Description' content='An online quiz and homework site'>
<meta name='Keywords' content='chemistry,learning,online,quiz,homework,video,textbook,open,education'>
<meta name='msapplication-config' content='none'/><link rel='icon' type='image/png' href='/favicon.png'>
<link rel='icon' type='image/vnd.microsoft.icon' href='/favicon.ico'>
<title>ChemVantage Registration Page</title>
<script type='text/javascript' src='https://www.google.com/recaptcha/api.js'> </script>
</head>

<body>
<%= Home.banner %><br>

<% if (message != null) { %>
<span style='color: red; border: 2px solid red'>&nbsp;<%= message %> &nbsp;</span>
<% } %>

<% if (openid_configuration == null) { %>
<h4>ChemVantage LTI Registration</h4>

Please complete the form below to obtain a set of LTI credentials. The information you 
provide will help us to create a trusted connection between your learning management system (LMS) and
ChemVantage that is convenient, secure and <a href=https://site.imsglobal.org/certifications?query=chemvantage>certified by IMS</a>.
When you submit the form, you will receive an email containing the information you need to
complete the configuration of your LMS as well as a link to finalize the registration.<br><br>

<%} else { %>

<h4>ChemVantage LTI Advantage Dynamic Registration</h4>

Please complete the form below to create a trusted LTI Advantage connection between your LMS and ChemVantage 
that is convenient, secure and <a href=https://site.imsglobal.org/certifications?query=chemvantage>certified by IMS</a>.
When you submit the form, ChemVantage will send a registration request to your LMS. If successful, your LMS administrator 
will need to activate the deployment in your LMS before a launch to ChemVantage can take place.<br><br>

<% } %>

<form method=post action=/lti/registration>

Your Name: <input type=text name=sub size=40 value='<%= (sub==null?"":sub) %>'><br/>
Your Email: <input type=text name=email size=40 value='<%= (email==null?"":email) %>'><br><br>

Type of organization:<br>
<label><input type=radio name=typ value=nonprofit <%= ((typ==null || typ.equals("nonprofit"))?"checked":"") %>> Public or nonprofit educational institution (up to 1000 users)</label><br>
<label><input type=radio name=typ value=personal <%= ((typ!=null && typ.equals("personal"))?"checked":"") %>> Small business or Personal account (up to 5 users)</label><br>
<label><input type=radio name=typ value=forprofit <%= ((typ!=null && typ.equals("forprofit"))?"checked":"") %>> Commercial partnership (up to 10000 users)</label><br><br>

<span id=orginfo>Your Organization Name: <input type=text name=aud  value='<%= (aud==null?"":aud) %>'> 
and Org Home Page: <input type=text name=url placeholder='https://myschool.edu' value='<%= (url==null?"":url) %>'><br>
	For faster account approval, your Email domain (above) should match the Home Page domain.<br><br></span>

Select your use case:<br>
<label><input type=radio name=use value=prod <%= (use.equals("prod")?"checked":"") %>>Teaching a chemistry class (production server)</label><br>
<label><input type=radio name=use value=test <%= (use.equals("test")?"checked":"") %>>Testing LTI connections (code development server)</label><br><br>

<% if (openid_configuration == null) { %>
Type of LTI registration:<br>
<label><input type=radio name=ver value=1p3 <%= ((ver==null || ver.equals("1p3"))?"checked":"") %>>LTI Advantage (preferred)</label><br>
<label><input type=radio name=ver value=1p1 <%= ((ver!=null && ver.equals("1p1"))?"checked":"") %>>LTI version 1.1 (expires 31-DEC-2021)</label><br><br>

Type of Learning Management System:<br>
<label><input type=radio name=lms value=blackboard <%= ((lms!=null && lms.equals("blackboard"))?"checked":"") %>>Blackboard</label><br>
<label><input type=radio name=lms value=brightspace <%= ((lms!=null && lms.equals("brightspace"))?"checked":"") %>>Brightspace</label><br>
<label><input type=radio name=lms value=canvas <%= ((lms!=null && lms.equals("canvas"))?"checked":"") %>>Canvas</label><br>
<label><input type=radio name=lms value=moodle <%= ((lms!=null && lms.equals("moodle"))?"checked":"") %>>Moodle</label><br>
<label><input type=radio name=lms value=sakai <%= ((lms!=null && lms.equals("sakai"))?"checked":"") %>>Sakai</label><br>
<label><input type=radio name=lms value=schoology <%= ((lms!=null && lms.equals("schoology"))?"checked":"") %>>Schoology</label><br>
<label><input type=radio name=lms id=other value=other <%= ((lms!=null && lms.equals("other"))?"checked":"") %>>Other: </label>
<input type=text name=lms_other value='<%= (lms_other==null?"":lms_other) %>' onFocus="document.getElementById('other').checked=true;"><br><br>	
<% } else {%>
<input type=hidden name=ver value=dynamic_registration>
<input type=hidden name=lms value=dynamic_registration>
<input type=hidden name=openid_configuration value='<%= openid_configuration %>'>
<% if (registration_token!=null) { %> <input type=hidden name=registration_token value='<%= registration_token %>'> <% } %>
<% } %>

<label><input type=checkbox name=AcceptChemVantageTOS value=true <%= ((AcceptChemVantageTOS!=null && AcceptChemVantageTOS.equals("true"))?"checked":"") %>>Accept the <a href=/About#terms target=_blank>ChemVantage Terms of Service</a></label><br><br>
<input type=hidden name=UserRequest value='Send Me the Registration Email'>

<div class='g-recaptcha' data-sitekey='6Ld_GAcTAAAAABmI3iCExog7rqM1VlHhG8y0d6SG'></div><br><br>
<input type=submit value='Submit Registration'>
</form>
<%= Home.footer %>