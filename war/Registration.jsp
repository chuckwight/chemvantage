<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.*,com.googlecode.objectify.*,org.chemvantage.*"%>

<%
	String thisURL = "https://" + request.getServerName() + "/lti/registration";
	String message = request.getParameter("message");
	String sub = request.getParameter("sub");
	String email = request.getParameter("email");
	String typ = request.getParameter("typ");
	String aud = request.getParameter("aud");
	String url = request.getParameter("url");
	String use = request.getParameter("use");
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
<link rel='icon' type='image/vnd.microsoft.icon' href='/favicon.ico'>
<title>ChemVantage Registration Page</title>
<script type='text/javascript' src='https://www.google.com/recaptcha/api.js'> </script>
</head>

<body>
<%= Home.banner %><br/>

<% if (message != null) { %>
<span style='color: red; border: 2px solid red'>&nbsp;<%= message %> &nbsp;</span>
<% } %>

<h2>This page is currently under construction. Please use the <a href=https://www.chemvantage.org/lti/registration>main ChemVantage registration page</a> instead.</h2>

<% if (dynamic) { %>

<h4>ChemVantage LTI Advantage Dynamic Registration</h4>

Please complete the form below to create a trusted LTI Advantage connection between your LMS and ChemVantage 
that is convenient, secure and <a href=https://site.imsglobal.org/certifications?query=chemvantage>certified by IMS</a>.
When you submit the form, ChemVantage will send a registration request to your LMS. If successful, your LMS administrator 
will need to activate the deployment in your LMS before a launch to ChemVantage can take place.<br/><br/>

<% } else { %>

<h4>ChemVantage LTI Registration</h4>

ChemVantage supports LTI Advantage Dynamic Registration at <a href=<%= thisURL %>><%= thisURL %></a><br/>
If your LMS supports this, the registration process will be fast and easy!<br/><br/>

Otherwise, you may proceed with manual registration by completing the form below to obtain a set of LTI credentials. The information you 
provide will help us to create a trusted connection between your learning management system (LMS) and
ChemVantage that is convenient, secure and <a href=https://site.imsglobal.org/certifications?query=chemvantage>certified by IMS</a>.
When you submit the form, you will receive an email containing the information you need to
complete the configuration of your LMS as well as a link to finalize the registration.<br/><br/>

<% } %>

<form method=post action=/lti/registration>
Please tell us how to contact you if there is ever a problem with your account (see our <a href=https://www.chemvantage.org/About#privacy>Privacy Policy</a>):<br/>
Your Name: <input type=text name=sub size=40 value='<%= (sub==null?"":sub) %>'><br/>
Your Email: <input type=text name=email size=40 value='<%= (email==null?"":email) %>'><br/><br/>

Please select your use case:<br/>
<label><input type=radio name=use value=prod <%= ("prod".equals(use)?"checked":"") %> onclick="document.getElementById('orgtype').style.display='';document.getElementById('testing').style.display='none'">Teaching a chemistry class (production server)</label><br/>
<label><input type=radio name=use value=test <%= ("test".equals(use)?"checked":"") %> onclick="document.getElementById('testing').style.display='';document.getElementById('orgtype').style.display='none'">Testing LTI connections (code development server)</label><br/><br/>

<span id=orgtype style="display:none">
Type of organization:<br/>
<label><input type=radio name=typ value=nonprofit <%= ("nonprofit".equals(typ)?"checked":"") %> > Public or nonprofit educational institution (free for up to 1000 users)</label><br/>
<label><input type=radio name=typ value=personal <%= ("personal".equals(typ)?"checked":"") %> > Small business or Personal account ($20/month for up to 5 users)</label><br/>
<label><input type=radio name=typ value=forprofit <%= ("forprofit".equals(typ)?"checked":"") %> > Commercial account ($5000/year for up to 10000 users)</label><br/>
If your need exceeds the user limits above, please contact us at admin@chemvantage.org for pricing options.<br/><br/>
</span>

<span id=testing style="display:none">
ChemVantage LLC is pleased to support the LTI community by permitting free access to our code development server (https://dev-vantage-hrd.appspot.com) for non-instructional 
uses such as testing LTI connections and LTI software development for LMS platforms. The development server may become unstable at times. Accounts are purged every month or two, but reregistration is free.<br/><br/>
</span>

<script>
var usebuttons = document.getElementsByName('use');
if(usebuttons[0].checked) document.getElementById('orgtype').style.display='inline';
if(usebuttons[1].checked) document.getElementById('testing').style.display='inline';
</script>

Please tell us about your school, business or organization:<br/>
Org Name: <input type=text name=aud  value='<%= (aud==null?"":aud) %>'> <br/>
Home Page: <input type=text name=url placeholder='https://myschool.edu' value='<%= (url==null?"":url) %>'><br/>
For instant account approval, your Email domain (above) should match the Home Page domain.<br/><br/>

<% if (!dynamic) { %>
	Type of LTI registration:<br/>
	<label><input type=radio name=ver value=1p3 <%= ((ver==null || ver.equals("1p3"))?"checked":"") %>>LTI Advantage (preferred)</label><br/>
	<label><input type=radio name=ver value=1p1 <%= ((ver!=null && ver.equals("1p1"))?"checked":"") %>>LTI version 1.1 (expires 31-DEC-2021)</label><br/><br/>

	Type of Learning Management System:<br/>
	<label><input type=radio name=lms value=blackboard <%= ((lms!=null && lms.equals("blackboard"))?"checked":"") %>>Blackboard</label><br/>
	<label><input type=radio name=lms value=brightspace <%= ((lms!=null && lms.equals("brightspace"))?"checked":"") %>>Brightspace</label><br/>
	<label><input type=radio name=lms value=canvas <%= ((lms!=null && lms.equals("canvas"))?"checked":"") %>>Canvas</label><br/>
	<label><input type=radio name=lms value=moodle <%= ((lms!=null && lms.equals("moodle"))?"checked":"") %>>Moodle</label><br/>
	<label><input type=radio name=lms value=sakai <%= ((lms!=null && lms.equals("sakai"))?"checked":"") %>>Sakai</label><br/>
	<label><input type=radio name=lms value=schoology <%= ((lms!=null && lms.equals("schoology"))?"checked":"") %>>Schoology</label><br/>
	<label><input type=radio name=lms id=other value=other <%= ((lms!=null && lms.equals("other"))?"checked":"") %>>Other: </label>
	<input type=text name=lms_other value='<%= (lms_other==null?"":lms_other) %>' onFocus="document.getElementById('other').checked=true;"><br/><br/>	
<% } else {%>
	<input type=hidden name=ver value=dynamic_registration>
	<input type=hidden name=lms value=dynamic_registration>
	<input type=hidden name=openid_configuration value='<%= openid_configuration %>'>
	<% if (registration_token!=null) { %> <input type=hidden name=registration_token value='<%= registration_token %>'> <% } %>
<% }  %>

<label><input type=checkbox name=AcceptChemVantageTOS value=true <%= ((AcceptChemVantageTOS!=null && AcceptChemVantageTOS.equals("true"))?"checked":"") %>>Accept the <a href=/About#terms target=_blank>ChemVantage Terms of Service</a></label><br/><br/>

<div class='g-recaptcha' data-sitekey='6Ld_GAcTAAAAABmI3iCExog7rqM1VlHhG8y0d6SG'></div><br/><br/>
<input type=submit value='Submit Registration'>
</form><br/><br/>

If you find ChemVantage to be useful, please consider making a corporate or personal donation to help us support Open Education Resources for teaching and learning. Thank you!<br/>

<form action="https://www.paypal.com/donate" method="post" target="_top">
<input type="hidden" name="hosted_button_id" value="4DYCV6EG2HPB2" />
<input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_donate_LG.gif" name="submit" title="PayPal - The safer, easier way to pay online!" alt="Donate with PayPal button" />
<img alt="" border="0" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1" />
</form>
<br/>
<%= Home.footer %>