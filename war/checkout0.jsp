<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.Date,java.text.DateFormat,com.googlecode.objectify.*,org.chemvantage.*,com.google.common.net.InternetDomainName"%>

<%
	String sig = request.getParameter("sig");
	User user = User.getUser(sig);
	if (user == null || user.isAnonymous()) response.sendError(401, "You must be logged in through your LMS to see this page.");
	String hashedId = request.getParameter("HashedId");
	boolean premiumUser = user.isPremium();
	String client_id = System.getProperty("com.google.appengine.application.id").equals("dev-vantage-hrd")?
			"AVJ8NuVQnTBwTbmkouWCjZhUT_eHeTm9fjAKwXJO0-RK-9VZFBtWm4J6V8o-47DvbOoaVCUiEb4JMXz8":  // Paypal sandbox client_id
			"AYlUNqRJZXhJJ9z7pG7GRMOwC-Y_Ke58s8eacfl1R51833ISAqOUhR8To0Km297MPcShAqm9ffp5faun";  // Paypal live client_id

	Date now = new Date();
	Date exp = new Date(now.getTime() +  15811200000L); // six months from now
	DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
	
	if (!premiumUser && user.getHashedId().equals(hashedId)) {  // successful payment; process a user upgrade 
		PremiumUser u = new PremiumUser(hashedId);  // constructor automatically saves new entity
		premiumUser = true;
		exp = u.exp;
	}
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
<title>ChemVantage Subscription</title>
</head>

<body style='background-color: white; font-family: Calibri,Arial,sans-serif; max-width: 600px;'>
<%= Subject.banner %>

<h3>Subscription Services</h3>
Most ChemVantage services are offered to students and educational institutions free of charge. You can help 
support this Open Education Resource by becoming a ChemVantage subscriber. The cost is just $5.00 USD for a six-month 
subscription that won't expire until <%= df.format(exp) %>. As a subscriber, you can<ul>
<li>view the detailed step-by-step solutions to homework problems</li>
<li>report issues or mistakes in homework solutions to ChemVantage </li>
</ul> 
To accept this offer, please select your preferred payment method below. When the transaction is completed, 
your subscription will be activated immediately.
Thank you for helping to support ChemVantage.

<h2>$5.00 USD</h2>

<% if (premiumUser) { %>

<h3>Thank you for your payment!</h3>
Your subscription is active and expires on <%= df.format(exp) %>

<% } else { %>

<div id="smart-button-container">
      <div style="text-align: center;">
        <div id="paypal-button-container"></div>
      </div>
    </div>
  <script src='https://www.paypal.com/sdk/js?client-id=<%= client_id %>&enable-funding=venmo&currency=USD'></script>
   <script>
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
            purchase_units: [{"description":"6-month ChemVantage subscription invoice <%= user.getHashedId() %>",
            	"amount":{"currency_code":"USD","value":5}}]
          });
        },

        onApprove: function(data, actions) {
          return actions.order.capture().then(function(orderData) {
            
            // Full available details
            console.log('Capture result', orderData, JSON.stringify(orderData, null, 2));
			
            // Submit form
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
  <input type=hidden name=HashedId value='<%= user.getHashedId() %>' />
  </form>
<% } %>

<hr/><img style='padding-left: 15px; vertical-align: middle;' src=images/CVLogo_tiny.png alt='ChemVantage logo' />&nbsp;
<a href=/about.html>About ChemVantage</a> | 
<a href=/about.html#terms>Terms and Conditions of Use</a> | 
<a href=/about.html#privacy>Privacy Policy</a> | 
<a href=/about.html#copyright>Copyright</a>


</body>
</html>