
<!-- This JSP file presents a form to students to purchase an individual subscription. -->

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.Date,java.text.DateFormat,com.googlecode.objectify.*,org.chemvantage.*,com.google.common.net.InternetDomainName"%>

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
<title>ChemVantage Subscription</title>
</head>

<body style='background-color: white; font-family: Calibri,Arial,sans-serif; max-width: 800px;'>
<%= Subject.banner %>
<main><h1 style='display: none'>Welcome to ChemVantage</h1>

<%
	String price = request.getParameter("price");
	if (price==null) price = "2"; // per month
	String sig = request.getParameter("sig");
	User user = User.getUser(sig);
	String hashedId = request.getParameter("HashedId");
	if (user == null || user.isAnonymous()) response.sendError(401, "You must be logged in through your LMS to see this page.");
	Date now = new Date();
	Date exp = null;
	DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.FULL);
	try {
		int nMonthsPurchased = Integer.parseInt(request.getParameter("NMonthsPurchased"));
		if (user.getHashedId().equals(hashedId)) { // successful purchase
			PremiumUser u = new PremiumUser(hashedId, nMonthsPurchased); // constructor automatically saves new entity
			exp = u.exp;
%> 
			<h2>Thank you for your payment!</h2>
			Your ChemVantage subscription is now active and expires on <%= df.format(exp) %><br/>
			Print this page as proof of purchase. Then return to your LMS and relaunch the assignment.<br/><br/>	
			Details: <%= request.getParameter("OrderDetails") %>
<%
		}
	} catch (Exception e) {  // the remainder of the JSP is devoted to presenting the purchase page

		String client_id = System.getProperty("com.google.appengine.application.id").equals("dev-vantage-hrd")
		? "AVJ8NuVQnTBwTbmkouWCjZhUT_eHeTm9fjAKwXJO0-RK-9VZFBtWm4J6V8o-47DvbOoaVCUiEb4JMXz8": // Paypal sandbox client_id
		"AYlUNqRJZXhJJ9z7pG7GRMOwC-Y_Ke58s8eacfl1R51833ISAqOUhR8To0Km297MPcShAqm9ffp5faun"; // Paypal live client_id
%>
		<h3>Individual ChemVantage License</h3>
		A subscription is required to access ChemVantage assignments and services through this learning management system. 
		The cost is just $<%= price %>.00 USD per month and gives you access to all ChemVantage quizzes, homework, videos 
		and other assignments created by your instructor.<br/><br/>
<% 
		PremiumUser u = ofy().load().type(PremiumUser.class).id(user.getHashedId()).now();
		if (u != null && u.exp.before(now)) {
%>		
		<h3>Your ChemVantage subscription expired: <%= df.format(u.exp) %></h3>
<%	
		}
%>

		Please select the desired number of months you wish to purchase: 
		<select id=nMonthsChoice onChange=updateAmount();>
			<option value=1>1 month</option>
			<option value=2>2 months</option>
			<option value=5>5 months</option>
			<option value=12>12 months</option>
		</select><br /><br /> 
		
		
		Select your preferred payment method below. When the transaction is completed, your subscription will be activated immediately.

		<h2>Purchase: <span id=amt>1 month - $2.00 USD</span></h2>

		<div id="smart-button-container">
      	  <div style="text-align: center;">
            <div id="paypal-button-container"></div>
          </div>
        </div>
 		<script src='https://www.paypal.com/sdk/js?client-id=<%= client_id %>&enable-funding=venmo&currency=USD'></script>
   		<script>
  		var nMonths = 1;
   		var value = "2";
   		function updateAmount() {
	   		nMonths = document.getElementById("nMonthsChoice").value;
	   		switch (nMonths) {
	   		case "1": value="2.00"; break;
	   		case "2": value="4.00"; break;
	   		case "5": value="9.00"; break;
	   		case "12": value="20.00"; break;
	   		default: value="2.00";
	  		}
	   		document.getElementById("amt").innerHTML=nMonths + ' months - $' + value + ' USD';
    	}
    	function initPayPalButton() {
      		paypal.Buttons({
        	style: {
          	shape: 'pill',
          	color: 'gold',
          	layout: 'vertical',
          	label: 'checkout',         
        },

        createOrder: function(data, actions) {
          return actions.order.create({
            purchase_units: [{"description":nMonths + "-month ChemVantage subscription for user: <%=user.getHashedId()%>",
            	"amount":{"currency_code":"USD","value":value}}]
          });
        },

        onApprove: function(data, actions) {
          return actions.order.capture().then(function(orderData) {
            
            // Full available details
            console.log('Capture result', orderData, JSON.stringify(orderData, null, 2));
			
            // Submit form
            document.getElementById('nmonths').value=nMonths;
            document.getElementById('orderdetails').value=JSON.stringify(orderData, null, 2);
            document.getElementById('activationForm').submit();
           // actions.redirect('thank_you.html');
            
          });
        },

        onError: function(err) {
          console.log(err);
        }
      	}).render('#paypal-button-container');
    	}
    	initPayPalButton();
  		</script>
  
  		<form id=activationForm method=post>
  		<input type=hidden name=sig value='<%= user.getTokenSignature() %>' />
  		<input type=hidden name=NMonthsPurchased id=nmonths />
 		<input type=hidden name=OrderDetails id=orderdetails />
  		<input type=hidden name=HashedId value='<%= user.getHashedId() %>' />
		</form>
<% 
	}
%>
</main>
<footer><hr/><img style='padding-left: 15px; vertical-align: middle;' src=images/CVLogo_tiny.png alt='ChemVantage logo' />&nbsp;
<a href=/about.html>About ChemVantage</a> | 
<a href=/about.html#terms>Terms and Conditions of Use</a> | 
<a href=/about.html#privacy>Privacy Policy</a> | 
<a href=/about.html#copyright>Copyright</a></footer>


</body>
</html>