<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.*,com.googlecode.objectify.*,org.chemvantage.*"%>

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
<%= Home.banner %><br><br>

ChemVantage is an Open Education Resource for teaching and learning college-level General
Chemistry.

<h4>ChemVantage LTI Registration</h4>
Please complete the form below to obtain a set of LTI credentials. The information you 
provide will help us to create a trusted connection between your learning management system (LMS) and
ChemVantage that is convenient, secure and <a href=https://site.imsglobal.org/certifications?query=chemvantage>certified by IMS</a>.
When you submit the form, you will receive an email containing the information you need to
complete the configuration of your LMS as well as a link to finalize the registration.<br><br>

<form method=post action=/lti/registration>

Your Name: <input type=text name=sub> and Email: <input type=text name=email><br><br>

Type of organization:<br>
<label><input type=radio name=typ value=nonprofit checked> Public school or nonprofit institution</label><br>
<label><input type=radio name=typ value=forprofit> Commercial company or for-profit school</label><br>
<label><input type=radio name=typ value=personal> Personal account (up to 5 users)</label><br><br>

<span id=orginfo>Your Organization: <input type=text name=aud> and Home Page: <input type=text name=url placeholder='https://myschool.edu'><br>
	For immediate access, your Email domain (above) should match the Home Page domain.<br><br></span>

Select your use case:<br>
<label><input type=radio name=use value=prod checked>Teaching a chemistry class (production environment)</label><br>
<label><input type=radio name=use value=test>Testing LTI connections (development environment)</label><br><br>

Type of LTI registration:<br>
<label><input type=radio name=ver value=1p1 checked>LTI version 1.1 (preferred)</label><br>
<label><input type=radio name=ver value=1p3>LTI Advantage (certified but still clunky)</label><br><br>

Type of Learning Management System:<br>
<label><input type=radio name=lms value=blackboard>Blackboard</label><br>
<label><input type=radio name=lms value=brightspace>Brightspace</label><br>
<label><input type=radio name=lms value=canvas>Canvas</label><br>
<label><input type=radio name=lms value=moodle>Moodle</label><br>
<label><input type=radio name=lms value=sakai>Sakai</label><br>
<label><input type=radio name=lms value=schoology>Schoology</label><br>
<label><input type=radio name=lms id=other value=other>Other: </label>
<input type=text name=lms_other onFocus="document.getElementById('other').checked=true;"><br><br>	

<label><input type=checkbox name=AcceptChemVantageTOS value=true>Accept the <a href=/About#terms target=_blank>ChemVantage Terms of Service</a></label><br><br>

<div class='g-recaptcha' data-sitekey='6Ld_GAcTAAAAABmI3iCExog7rqM1VlHhG8y0d6SG'></div><br><br>
<input type=submit name=UserRequest value='Send Me The Registration Email'>
</form>
<%= Home.footer %>