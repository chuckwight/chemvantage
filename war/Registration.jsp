<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.*,com.googlecode.objectify.*,org.chemvantage.*,com.google.common.net.InternetDomainName"%>

<%
	String message = request.getParameter("message");
	String sub = request.getParameter("sub");
	String email = request.getParameter("email");
	String typ = request.getParameter("typ");
	String aud = request.getParameter("aud");
	String url = request.getParameter("url");
	String ver = request.getParameter("ver");
	String lms = request.getParameter("lms");
	String price = request.getParameter("price");
	if (price==null) price="-1";
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

<body style='background-color: white; font-family: Calibri,Arial,sans-serif;'>
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

<% if (registration_token!=null) { %> <input type=hidden name=registration_token value='<%= registration_token %>'/> <% } %>

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

<% } else { %>
 <input type=hidden name=openid_configuration value='<%= openid_configuration %>' />
<% } %>

<script>
function showPricingDetails() {
	var price = document.getElementById('nameit').value;
	var pricingDetails = document.getElementById('pricingDetails');
	if (price == "-1") pricingDetails.innerHTML = "";
	else if (price=="0") {
		pricingDetails.innerHTML = "You have selected a price of zero, and that is a valid choice. However, it changes the decision from one of price to a different decision about whether "
			+ "ChemVantage LLC is willing to pay the full cost of providing this service to your students. To help us make this decision, please use the spaces below to make your case:<br/>"
			+ "<textarea name=whyZeroPrice rows=4 cols=80 wrap=soft placeholder='Please explain here why you chose a price of zero.'></textarea><br/><br/>"
			+ "<textarea name=whyChemVPays rows=4 cols=80 wrap=soft placeholder='Please explain here why ChemVantage should pay the full cost of providing this service to your students.'></textarea><br/><br/>";
	} else {
		pricingDetails.innerHTML = "Thank you. No payment is required now. When you complete the LTI registration in your LMS, your ChemVantage account will be set up automatically "
			+ "to charge each student $" + price 
			+ "&nbsp;USD before granting access to the first assignment. Upon successful payment, the student will have unlimited access to ChemVantage assignments through this "
			+ "LTI account for a period of 10 months.<br/><br/>"
			+ "Some institutions prefer to purchase licenses centrally on behalf of their students. ChemVantage allows this, and we will send you an email with a link to purchase student "
			+ "licenses in bulk at a discounted rate. <br/><br/>"
			+ "Access to ChemVantage by instructors or administrators is always free and does not require a license. We will also provision your ChemVantage account "
			+ "with 5 complimentary student licenses to use for testing assignment creation and return of student scores to your LMS grade book.<br/><br/>";
	}
}
</script>

<h4>Pricing</h4>

ChemVantage allows you to name your own price, in part because instructors use the tool in a variety of ways (e.g., homework only, required/optional/extra credit, etc.).<br/>
Please select your price: 
<select id=nameit name=price onchange="showPricingDetails()">
<option value="-1" hidden=true <%= price.equals("-1")?"selected":"" %>>Select a value</option>
<option value="20" <%= price.equals("20")?"selected":"" %> >$20 USD/student</option>
<option value="10" <%= price.equals("10")?"selected":"" %> >$10 USD/student</option>
<option value="5" <%= price.equals("5")?"selected":"" %> >$5 USD/student</option>
<option value="0" <%= price.equals("0")?"selected":"" %> >$0 USD/student</option>
</select> for a 10-month subscription to ChemVantage.

<br/><br/>

<div id=pricingDetails></div>

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