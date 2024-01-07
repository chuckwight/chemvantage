<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.*,com.googlecode.objectify.*,org.chemvantage.*,com.google.common.net.InternetDomainName"%>

<%
	int price = 2;  // posted monthky subscription price in USD
	String message = request.getParameter("message");
	String sub = request.getParameter("sub");
	String email = request.getParameter("email");
	String aud = request.getParameter("aud");
	String url = request.getParameter("url");
	String lms = request.getParameter("lms");
	String lms_other = request.getParameter("lms_other");
	String AcceptChemVantageTOS = request.getParameter("AcceptChemVantageTOS");
	String openid_configuration = request.getParameter("openid_configuration");
	String registration_token = request.getParameter("registration_token");
	boolean dynamic = openid_configuration != null;
%>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" href="images/logo_sq.png">
    <!-- Fav-Icon -->
    <title>ChemVantage | Registration</title>
    <!-- Title -->
    <link href="https://fonts.googleapis.com/css2?family=Poppins:wght@100;200;300;400;500;600;700;800;900&family=Shantell+Sans:wght@300;400;500;600;700;800&display=swap" rel="stylesheet"/>
    <!-- Font Family -->
    <link rel="stylesheet" href="css/style.css">
    <!-- Main Style Sheet -->
    <link rel="stylesheet" href="css/responsive.css">
    <!-- Responsive Style Sheet -->

    <!-- Calendly badge widget begin -->
    <link href="https://assets.calendly.com/assets/external/widget.css" rel="stylesheet">
    <script src="https://assets.calendly.com/assets/external/widget.js" type="text/javascript" async></script>
    <script type="text/javascript">window.onload = function() { Calendly.initBadgeWidget({ url: 'https://calendly.com/richkingsford/30m', text: 'Schedule a setup', color: '#0069FF', textColor: '#FFFFFF', branding: undefined }); }</script>
    <!-- Calendly badge widget end -->
</head>

<body>
    <a href=#main class="skip-to-main-content-link">Skip to main content</a>
    <!-- Webpage Header Start -->
    <header class="webpage-header">
        <div class="container">
            <div class="d-flex justify-between align-center">
                <div class="w-0">
                    <a href="./index.html">
                        <img src="images/logo.png" class="webpage-logo" alt="website logo"/>
                    </a>
                </div>
                <div class="w-80">
                    <nav class="header-nav d-flex flex-end align-center gap-3">
                        <div class="nav-list">
                            <ul>
                                <li>
                                    <a href="./index.html" class="nav-link">Home</a>
                                </li>
                                <li>
                                    <a href="./about.html" class="nav-link">About Us</a>
                                </li>
                                <li>
                                    <a href="./index.html#assignments" class="nav-link">Assignments</a>
                                </li>
                                <li>
                                    <a href="./index.html#itembank" class="nav-link">Items</a>
                                </li>
                                <li>
                                    <a href="./index.html#pricing" class="nav-link">Pricing</a>
                                </li>
                            </ul>
                            <button class="close">&times;</button>
                        </div>
                        <button onclick="Calendly.showPopupWidget('https://calendly.com/chemvantage/install-chemvantage');" class="btn btn-two">
                            Schedule a Setup
                        </button>
                        <button class="solid-bar">&#9776;</button>
                    </nav>
                </div>
            </div>
        </div>
    </header>
    <!-- Webpage Header End -->

    <!-- Webpage BTT Start -->
    <div class="btt">
        <img src="images/up-arrow.png" alt="up-arrow"/>
    </div>
    <!-- Webpage BTT End -->

    <main id="main">
    <!-- Registration Content Start -->
    <section class="section-padding registration-content">
        <div class="container">
            <div class="section-heading">
                <h1>ChemVantage LTI <%= dynamic?"Dynamic ":"" %>Registration</h1>
            </div>
            <div class="section-heading-top-gap">
                 <p>
                    Please complete the form below to create a trusted LTI Advantage connection between your LMS and ChemVantage that is convenient, secure and <a href="https://site.imsglobal.org/certifications?query=chemvantage" target="_blank" aria-label="Opens new tab">certified by 1EdTech</a>. 
                    When you submit the form, ChemVantage will send <%= dynamic?"a registration request to your LMS. If successful, you must activate the deployment in your LMS.":"a confirmation email with a tokenized link to complete the registration." %>
                </p>
                 <div class="form-elements w-70 mx-auto">
                 
                 <% if (message != null) { %>
					<div style='color: #EE0000; border: 2px solid #EE0000; padding: 5px'><%= message %></div><br/>
				 <% } %>
                 
                    <p>
                        Please tell us how to contact you if there is ever a problem with your account (see our <a href="./privacy_policy.html">Privacy Policy</a>):
                    </p>
                    <form id=regform method=post action="/lti/registration">
                        <div class="d-flex gap-1">
                            <div class="w-50 mb">
                                <label for="name" class="form-label">Name <span>*</span></label>
                                <input type="text" class="form-control" id="name" name="sub" value="<%= (sub==null?"":sub) %>" placeholder="Enter your name.." required>
                            </div>
                            <div class="w-50 mb">
                                <label for="email" class="form-label">Email <span>*</span></label>
                                <input type="email" class="form-control" id="email" name="email" value="<%= (email==null?"":email) %>" placeholder="Enter your email.." required>
                            </div>
                        </div>
                        <div class="d-flex gap-1">
                            <div class="w-50 mb">
                                <label for="org_name" class="form-label">Org Name <span>*</span></label>
                                <input type="text" class="form-control" id="org_name" name="aud" value="<%= (aud==null?"":aud) %>" placeholder="Organization name here.." required>
                            </div>
                            <div class="w-50 mb">
                                <label for="url" class="form-label">Home page <span>*</span></label>
                                <input type="url" class="form-control" id="url" name="url" value="<%= (url==null?"":url) %>" placeholder="https://domain.edu" required>
                            </div>
                        </div>
                        
                        <% if (registration_token!=null) { %> 
							<input type=hidden name=registration_token value='<%= registration_token %>'/> 
						<% }  
                        
                        if (!dynamic) { %>
                        
                        <div class="mt">
                            <p>
                                Type of Learning Management System:
                            </p>
                            <div class="mb">
                                <label for="blackboard" class="radio-label">
                                    <input type="radio" class="form-radio" name="lms" id="blackboard" value="blackboard" <%= "blackboard".equals(lms)?"checked":"" %> required />
                                    Blackboard
                                </label>
                                <label for="brightspace" class="radio-label">
                                    <input type="radio" class="form-radio" name="lms" id="brightspace" value="brightspace" <%= "brightspace".equals(lms)?"checked":"" %> />
                                    Brightspace
                                </label>
                                <label for="canvas" class="radio-label">
                                    <input type="radio" class="form-radio" name="lms" id="canvas" value="canvas" <%= "canvas".equals(lms)?"checked":"" %> />
                                    Canvas
                                </label>
                                <label for="moodle" class="radio-label">
                                    <input type="radio" class="form-radio" name="lms" id="moodle" value="moodle" <%= "moodle".equals(lms)?"checked":"" %> />
                                    Moodle
                                </label>
                                <label for="sakai" class="radio-label">
                                    <input type="radio" class="form-radio" name="lms" id="sakai" value="sakai" <%= "sakai".equals(lms)?"checked":"" %> />
                                    Sakai
                                </label>
                                <label for="schoology" class="radio-label">
                                    <input type="radio" class="form-radio" name="lms" id="schoology" value="schoology" <%= "schoology".equals(lms)?"checked":"" %> />
                                    Schoology
                                </label>
                                <label for="other" class="radio-label">
                                    <input type="radio" class="form-radio" name="lms" id="other" value="other" <%= "other".equals(lms)?"checked":"" %> />
                                    Other:
                                    <input type="text" class="form-radio-input" name="lms_other" value="<%= "other".equals(lms) && lms_other!=null?lms_other:"" %>" placeholder="(Specify)" />
                                </label>
                            </div>
                            
                            <% } else { %>
								<input type=hidden name=openid_configuration value='<%= openid_configuration %>' />
							<% } %>
                            
                            <div>
                            Pricing:
							<ul>
								<li>LTI registration and instructor accounts are free.</li>
								<li>Each student account costs $<%= price %>.00 USD per month or $<%= 4*price %>.00 USD per semester (5 months).</li>
								<li>Institutions have the option of purchasing student licenses in bulk for as little as $<%= price %>.00 USD per year.</li>
							</ul><br/>
                            </div>
                        	<div class="mb btm-chk">
                                <label for="accept" class="check-label">
                                    <input type="checkbox" name="AcceptChemVantageTOS" id="accept" value="true" <%= "true".equals(AcceptChemVantageTOS)?"checked":"" %> required />
                                    Accept the <a href="./terms_and_conditions.html">ChemVantage Terms of Service</a>
                                </label>                               
                        	</div>
                            <div class="mb">
                            	<% if (!request.getServerName().contains("localhost")) { %>
                            		<div class="g-recaptcha" data-sitekey="<%= Subject.getReCaptchaSiteKey() %>" aria-label="Google Recaptcha"></div>
                           		<% } %>
                            </div>
                            <button type="submit" class="btn btn-two">Submit Registration</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    </section>
    <!-- Registration Content End -->
  </main>
  
    <!-- Webpage Footer Start -->
    <footer>
        <div class="container">
            <div class="d-flex footer-content justify-between align-center">
                <div class="w-40">
                    <ul class="social"> <!-- 
                        <li>
                            <a href="#" role="button" target="_blank" aria-label="Opens new tab">
                                <img src="images/facebook.png" alt="facebook"/>
                            </a>
                        </li>
                        <li>
                            <a href="#" role="button" target="_blank" aria-label="Opens new tab">
                                <img src="images/instagram.png" alt="instagram"/>
                            </a>
                        </li>
                        <li>
                            <a href="#" role="button" target="_blank" aria-label="Opens new tab">
                                <img src="images/twitterx.png" alt="twitterx"/>
                            </a>
                        </li>
                        <li>
                            <a href="#" role="button" target="_blank" aria-label="Opens new tab">
                                <img src="images/linkedin.png" alt="linkedin"/>
                            </a>
                        </li>  -->
                    </ul>
                </div>
                <div class="w-40 text-end">
                    <ul class="contact">
                        <li>
                            <a href="tel:+18012438242" title="phone">
                                +1 (801)243-8242
                            </a>
                        </li>
                        <li>
                            <a href="mailto:admin@chemvantage.org" title="email">
                                admin@chemvantage.org
                            </a>
                        </li>
                    </ul>
                </div>
            </div>
        </div>
        <div class="m-container">
            <div class="d-flex align-center footer-btm-content">
                <div class="w-40">
                    <span class="copy">
                        &copy; 2010 - <script>document.write(new Date().getFullYear())</script> ChemVantage LLC
                    </span>
                </div>
                <div class="w-60 text-end">
                    <ul>
                        <li>
                            <a href="./terms_and_conditions.html" target="_blank" aria-label="Opens new tab">
                                Terms and Conditions
                            </a>
                        </li>
                        <li>
                            <a href="./privacy_policy.html" target="_blank" aria-label="Opens new tab">
                                Privacy Policy
                            </a>
                        </li>
                        <li>
                            <a href="./copyright.html" target="_blank" aria-label="Opens new tab">
                                Copyright
                            </a>
                        </li>
                    </ul>
                </div>
            </div>
        </div>
    </footer>
    <!-- Webpage Footer End -->

    <script type="text/javascript" src="https://www.google.com/recaptcha/api.js"> </script>
    <!-- Google recaptcha -->
    
    <script src="js/script.js"></script>
    <!-- Main Script Sheet -->
</body>
</html>
