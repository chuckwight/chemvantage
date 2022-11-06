<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.*,com.googlecode.objectify.*,org.chemvantage.*,com.google.common.net.InternetDomainName"%>

<%
	String price = "2";
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
	boolean development = request.getServerName().contains("dev-vantage-hrd.appspot.com");
%>

<!DOCTYPE html>
<html lang="en">
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

<body style='background-color: white; font-family: Calibri,Arial,sans-serif; padding: 30px; max-width: 800px' >

<%= Subject.banner %><br/>

<% if (message != null) { %>
<span style='color: #EE0000; border: 2px solid red'>&nbsp;<%= message %> &nbsp;</span>
<% } %>

<main><h1 style="display:none">ChemVantage Registration</h1>
<h3>ChemVantage LTI Advantage <%= dynamic?"Dynamic":"" %> Registration</h3>

<form id=regform method=post action='/lti/registration'>

Please complete the form below to create a trusted LTI Advantage connection between your LMS and ChemVantage 
that is convenient, secure and <a href=https://site.imsglobal.org/certifications?query=chemvantage>certified by 1EdTech</a>.
When you submit the form, ChemVantage will send a registration request to your LMS. If successful, you  
will need to activate the deployment in your LMS before a launch to ChemVantage can take place.<br/><br/>

Please tell us how to contact you if there is ever a problem with your account (see our <a href=https://www.chemvantage.org/about.html#privacy>Privacy Policy</a>):<br/>
<label>Your Name: <input type=text name=sub size=40 value='<%= (sub==null?"":sub) %>' /> </label><br/>
<label>Your Email: <input type=text name=email size=40 value='<%= (email==null?"":email) %>' /> </label><br/><br/>

Please tell us about your school, business or organization:<br/>
<label>Org Name: <input type=text name=aud  value='<%= (aud==null?"":aud) %>' /> </label><br/>
<label>Home Page: <input type=text name=url placeholder='https://myschool.edu' value='<%= (url==null?"":url) %>' /></label><br/><br/>

<% if (registration_token!=null) { %> 
 <input type=hidden name=registration_token value='<%= registration_token %>'/> 
<% } %>

<% if (!dynamic) { %>


<fieldset style='width:400px'><legend>Type of Learning Management System:<br/></legend>
<label><input type=radio name=lms value=blackboard <%= ((lms!=null && lms.equals("blackboard"))?"checked":"") %> />Blackboard</label><br/>
<label><input type=radio name=lms value=brightspace <%= ((lms!=null && lms.equals("brightspace"))?"checked":"") %> />Brightspace</label><br/>
<label><input type=radio name=lms value=canvas <%= ((lms!=null && lms.equals("canvas"))?"checked":"") %> />Canvas</label><br/>
<label><input type=radio name=lms value=moodle <%= ((lms!=null && lms.equals("moodle"))?"checked":"") %> />Moodle</label><br/>
<label><input type=radio name=lms value=sakai <%= ((lms!=null && lms.equals("sakai"))?"checked":"") %> />Sakai</label><br/>
<label><input type=radio name=lms value=schoology <%= ((lms!=null && lms.equals("schoology"))?"checked":"") %> />Schoology</label><br/>
<label><input type=radio name=lms id=other value=other <%= ((lms!=null && lms.equals("other"))?"checked":"") %> />Other:</label>
<label><input type=text name=lms_other value='<%= (lms_other==null?"":lms_other) %>' placeholder='(specify)' onFocus="document.getElementById('other').checked=true;" /></label>
</fieldset>
<br/><br/>
<% } else { %>
 <input type=hidden name=openid_configuration value='<%= openid_configuration %>' />
<% } 

if (development) { %>
This is a development server:
ChemVantage is pleased to provide free access to our software development server for testing LTI connections. 
Please note that the server is sometimes in an unstable state, and accounts may be reset or even deleted at any time.<br/><br/>
<% } else { %>
Pricing:
<ul>
<li>LTI registration and instructor accounts are free.</li>
<li>Each student account costs $<%= price %>.00 USD per month for an all-access subscription.</li>
<li>Institutions have the option of purchasing student licenses in bulk at deeply discounted rates.</li>
</ul>
<% } %>

<label><input type=checkbox name=AcceptChemVantageTOS value=true <%= ((AcceptChemVantageTOS!=null && AcceptChemVantageTOS.equals("true"))?"checked":"") %>/>Accept the <a href=/about.html#terms target=_blank aria-label='opens new tab'>ChemVantage Terms of Service</a></label><br/><br/>

<div class='g-recaptcha' data-sitekey='6Ld_GAcTAAAAABmI3iCExog7rqM1VlHhG8y0d6SG' aria-label='Google Recaptcha'></div><br/><br/>

<input type=submit value='Submit Registration'/>

</form><br/><br/>

</main>
<footer><hr/><img style='padding-left: 15px; vertical-align: middle;width:30px' src=images/CVLogo.png alt='ChemVantage logo' />&nbsp;
<a href=/index.html style='text-decoration: none;'><span style='color: blue;font-weight: bold;'>Chem</span><span style='color: #EE0000;font-weight: bold;'>Vantage</span></a> | 
<a href=/about.html#terms>Terms and Conditions of Use</a> | 
<a href=/about.html#privacy>Privacy Policy</a> | 
<a href=/about.html#copyright>Copyright</a></footer>

</body>
</html>