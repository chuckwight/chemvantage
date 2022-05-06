<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.Date,java.text.DateFormat,com.googlecode.objectify.*,org.chemvantage.*,com.google.common.net.InternetDomainName"%>

<%
	String sig = request.getParameter("sig");
	String price = "2";
	int nMonthsPurchased = 1;
	String purchaseAmount = "2.00";
	String orderDetails = "";
	User user = User.getUser(sig);
	if (user == null || user.isAnonymous()) response.sendError(401, "You must be logged in through your LMS to see this page.");
	String hashedId = request.getParameter("HashedId");
	boolean premiumUser = user.isPremium();
	String client_id = System.getProperty("com.google.appengine.application.id").equals("dev-vantage-hrd")?
	"AVJ8NuVQnTBwTbmkouWCjZhUT_eHeTm9fjAKwXJO0-RK-9VZFBtWm4J6V8o-47DvbOoaVCUiEb4JMXz8":  // Paypal sandbox client_id
	"AYlUNqRJZXhJJ9z7pG7GRMOwC-Y_Ke58s8eacfl1R51833ISAqOUhR8To0Km297MPcShAqm9ffp5faun";  // Paypal live client_id
	Date now = new Date();
	Date exp = now;
	DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.FULL);

	if (!premiumUser && user.getHashedId().equals(hashedId)) { // successful payment; process a user upgrade 
		PremiumUser u = new PremiumUser(hashedId); // constructor automatically saves new entity
		premiumUser = true;
		exp = u.exp;
	}
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
<title>ChemVantage Subscription</title>
</head>

<body style='background-color: white; font-family: Calibri,Arial,sans-serif; max-width: 800px;'>
<%= Subject.banner %>
<main><h1 style='display: none'>Welcome to ChemVantage</h1>
<%
if (user.getHashedId().equals(hashedId)) { // successful payment; process a user upgrade 
	try {
		nMonthsPurchased = Integer.parseInt(request.getParameter("NMonths"));
		purchaseAmount = request.getParameter("PurchaseAmount");
		orderDetails = request.getParameter("OrderDetails");
		exp = new Date(now.getTime() + nMonthsPurchased * 2628000000L);  // nMonths from now
		new PremiumUser(user.getHashedId(),nMonthsPurchased);
%>
<h3>Thank you for your payment!</h3>
	Your ChemVantage subscription is now active and expires on <%= df.format(exp) %><br/>
	Please return to your LMS and relaunch the assignment.
<%
	} catch (Exception e) {
%>
		<h3>Individual License Purchase Failed</h3>
		There was an unexpected problem with the transaction, sorry. Please contact 
		Chuck Wight at admin@chemvantage.org to resolve the situation. It will be most 
		helpful to cut/paste the information below in your message:<br/><br/>
		Date/Time: <%= df.format(new Date()) %><br /> 
		UserId: <%= user.getHashedId() %><br /> 
		Number of months purchased: <%= nMonthsPurchased %><br /> 
		Purchase amount: $<%= purchaseAmount %> USD<br /> 
		Details: <%= orderDetails %>
<%
	}
} else {  // first time here: show form to purchase license:
%>

<h3>Individual ChemVantage License</h3>
A subscription is required to access ChemVantage assignments and services through this learning management system. 
The cost is just $<%= price %>.00 USD per month and gives you access to all ChemVantage quizzes, homework, videos 
and other assignments created by your instructor.<br/><br/>

<% 
	PremiumUser u = ofy().load().type(PremiumUser.class).id(user.getHashedId()).now();
	if (u==null);
	else if (u.exp.before(now)) {
%>		
		<h3>Your ChemVantage subscription expired at <%= df.format(u.exp) %></h3>
<%	
	}
%>

Please select the desired number of months you wish to purchase: 
		<select id=monthsChoice onChange=updateAmount();>
			<option value=1>1 month</option>
			<option value=2>2 months</option>
			<option value=5>5 months</option>
			<option value=12>12 months</option>
		</select><br /><br /> 
		
		
Please select your preferred payment method below. When the transaction is completed, your subscription will be activated immediately.

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
	   nMonths = document.getElementById("nmonthsChoice").value;
	   switch (nMonths) {
	   case "1": value="2.00"; break;
	   case "2": value="4.00"; break;
	   case "5": value="10.00"; break;
	   case "12": value="24.00"; break;
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
            purchase_units: [{"description":nMonths + "-month ChemVantage subscription for user: <%= user.getHashedId() %>",
            	"amount":{"currency_code":"USD","value":value}}]
          });
        },

        onApprove: function(data, actions) {
          return actions.order.capture().then(function(orderData) {
            
            // Full available details
            console.log('Capture result', orderData, JSON.stringify(orderData, null, 2));
			
            // Submit form
            document.getElementById('nmonths').value=nMonths;
            document.getElementById('purchaseamt').value=value;
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
  <input type=hidden name=NMonths id=nmonths />
  <input type=hidden name=PurchaseAmount id=purchaseamt />
  <input type=hidden name=OrderDetails id=orderdetails />
  <input type=hidden name=HashedId value='<%= user.getHashedId() %>' />
</form>
<% } %>
</main>
<footer><hr/><img style='padding-left: 15px; vertical-align: middle;' src=images/CVLogo_tiny.png alt='ChemVantage logo' />&nbsp;
<a href=/about.html>About ChemVantage</a> | 
<a href=/about.html#terms>Terms and Conditions of Use</a> | 
<a href=/about.html#privacy>Privacy Policy</a> | 
<a href=/about.html#copyright>Copyright</a></footer>


</body>
</html>