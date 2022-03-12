<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.*,com.googlecode.objectify.*,org.chemvantage.*,com.google.common.net.InternetDomainName"%>

<%
	String message = request.getParameter("message");
	String sub = request.getParameter("sub");
	String email = request.getParameter("email");
	String aud = request.getParameter("aud");
	String url = request.getParameter("url");
	String ver = request.getParameter("ver");
	String lms = request.getParameter("lms");
	String lms_other = request.getParameter("lms_other");
	String AcceptChemVantageTOS = request.getParameter("AcceptChemVantageTOS");
	String openid_configuration = request.getParameter("openid_configuration");
	String registration_token = request.getParameter("registration_token");
	boolean dynamic = openid_configuration != null;
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
<link rel='icon' type='image/png' href='/images/favicon.png' />
<link rel='icon' type='image/vnd.microsoft.icon' href='/images/favicon.ico'>
<title>ChemVantage Registration Page</title>
<script type='text/javascript' src='https://www.google.com/recaptcha/api.js'> </script>
</head>

<body style='background-color: white; font-family: Calibri,Arial,sans-serif;' onload=showPricingDetails() >
<%= Subject.banner %><br/>

<% if (message != null) { %>
<span style='color: red; border: 2px solid red'>&nbsp;<%= message %> &nbsp;</span>
<% } %>

<h3>ChemVantage LTI Advantage <%= dynamic?"Dynamic":"" %> Registration</h3>

<form id=regform method=post action='/lti/registration'>

Please complete the form below to create a trusted LTI Advantage connection between your LMS and ChemVantage 
that is convenient, secure and <a href=https://site.imsglobal.org/certifications?query=chemvantage>certified by IMS</a>.
When you submit the form, ChemVantage will send a registration request to your LMS. If successful, you  
will need to activate the deployment in your LMS before a launch to ChemVantage can take place.<br/><br/>

Please tell us how to contact you if there is ever a problem with your account (see our <a href=https://www.chemvantage.org/about.html#privacy>Privacy Policy</a>):<br/>
Your Name: <input type=text name=sub size=40 value='<%= (sub==null?"":sub) %>'/><br/>
Your Email: <input type=text name=email size=40 value='<%= (email==null?"":email) %>'/><br/><br/>

Please tell us about your school, business or organization:<br/>
Org Name: <input type=text name=aud  value='<%= (aud==null?"":aud) %>'/> <br/>
Home Page: <input type=text name=url placeholder='https://myschool.edu' value='<%= (url==null?"":url) %>'/><br/><br/>

<% if (registration_token!=null) { %> 
 <input type=hidden name=registration_token value='<%= registration_token %>'/> 
<% } %>

<% if (!dynamic) { %>

Type of Learning Management System:<br/>
<label><input type=radio name=lms value=blackboard <%= ((lms!=null && lms.equals("blackboard"))?"checked":"") %> />Blackboard</label><br/>
<label><input type=radio name=lms value=brightspace <%= ((lms!=null && lms.equals("brightspace"))?"checked":"") %> />Brightspace</label><br/>
<label><input type=radio name=lms value=canvas <%= ((lms!=null && lms.equals("canvas"))?"checked":"") %> />Canvas</label><br/>
<label><input type=radio name=lms value=moodle <%= ((lms!=null && lms.equals("moodle"))?"checked":"") %> />Moodle</label><br/>
<label><input type=radio name=lms value=sakai <%= ((lms!=null && lms.equals("sakai"))?"checked":"") %> />Sakai</label><br/>
<label><input type=radio name=lms value=schoology <%= ((lms!=null && lms.equals("schoology"))?"checked":"") %> />Schoology</label><br/>
<label><input type=radio name=lms id=other value=other <%= ((lms!=null && lms.equals("other"))?"checked":"") %> />Other: 
<input type=text name=lms_other value='<%= (lms_other==null?"":lms_other) %>' onFocus="document.getElementById('other').checked=true;" /></label>
<br/><br/>
<% } else { %>
 <input type=hidden name=openid_configuration value='<%= openid_configuration %>' />
<% } %>

Pricing:
<ol>
<li>LTI registration and setup are free.</li>
<li>Instructor accounts are completely free.</li>
<li>Each student account costs $20 USD for a 10-month all-access subscription.</li>
</ol>
If this pricing structure doesn't fit your needs, please contact Chuck Wight at admin@chemvantage.org for alternative pricing.

<br/><br/>

<label><input type=checkbox name=AcceptChemVantageTOS value=true <%= ((AcceptChemVantageTOS!=null && AcceptChemVantageTOS.equals("true"))?"checked":"") %>/>Accept the <a href=/about.html#terms target=_blank>ChemVantage Terms of Service</a></label><br/><br/>

<div class='g-recaptcha' data-sitekey='6Ld_GAcTAAAAABmI3iCExog7rqM1VlHhG8y0d6SG'></div><br/><br/>

<input type=submit value='Submit Registration'/>

</form><br/><br/>

<hr/><img style='padding-left: 15px; vertical-align: middle;' src=images/CVLogo_tiny.png alt='ChemVantage logo' />&nbsp;
<a href=/about.html>About ChemVantage</a> | 
<a href=/about.html#terms>Terms and Conditions of Use</a> | 
<a href=/about.html#privacy>Privacy Policy</a> | 
<a href=/about.html#copyright>Copyright</a>

</body>
</html>