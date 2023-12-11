<!-- This JSP file presents a form to an instructor to purchase student subscription tokens in bulk. -->

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
<title>Purchase ChemVantage Licenses</title>
</head>

<body style='background-color: white; font-family: Calibri,Arial,sans-serif; max-width: 800px;'>
<%= Subject.banner %>

<%
String sig = request.getParameter("sig");
User user = User.getUser(sig);
if (user == null || user.isAnonymous())
	response.sendError(401, "You must be logged in through your LMS to see this page.");
String hashedId = request.getParameter("HashedId");
DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
int nLicensesPurchased = 0;
String purchaseAmount = "0";
String orderDetails = "";
long assignmentId = user.getAssignmentId();
Assignment a = ofy().load().type(Assignment.class).id(assignmentId).now();
Deployment d = ofy().load().type(Deployment.class).id(a.domain).now();
String client_id = Subject.projectId.equals("dev-vantage-hrd")?
		"AVJ8NuVQnTBwTbmkouWCjZhUT_eHeTm9fjAKwXJO0-RK-9VZFBtWm4J6V8o-47DvbOoaVCUiEb4JMXz8":  // Paypal sandbox client_id
		"AYlUNqRJZXhJJ9z7pG7GRMOwC-Y_Ke58s8eacfl1R51833ISAqOUhR8To0Km297MPcShAqm9ffp5faun";  // Paypal live client_id


if (user.getHashedId().equals(hashedId)) { // successful payment; process a user upgrade 
	try {
		nLicensesPurchased = Integer.parseInt(request.getParameter("NExams"));
		purchaseAmount = request.getParameter("PurchaseAmount");
		orderDetails = request.getParameter("OrderDetails");
		d.putNLicensesRemaining(d.getNLicensesRemaining() + nLicensesPurchased);
%>
		<h3>Success! Thank you for your purchase.</h3>
		<h2>Receipt</h2>
		ChemVantage LLC<br/>
		606 Tony Tank Ln, Salisbury, MD USA<br/>
		+1-801-243-8242<br/>
		admin@chemvantage.org<br/><br/>
<%
	} catch (Exception e) {
%>
		<h3>Purchase Failed</h3>
		There was an unexpected problem with the transaction, sorry. Please contact 
		Chuck Wight at admin@chemvantage.org to resolve the situation. It will be most 
		helpful to cut/paste any information below in your message:<br/><br/>
<% 
	}
%>
		Date/Time: <%= df.format(new Date()) %><br /> 
		UserId: <%= user.getHashedId() %><br /> 
		Deployment: <%= d.getPlatformDeploymentId() %><br /> 
		Organization: <%= d.getOrganization() %><br />
		Number of licenses purchased: <%= nLicensesPurchased %><br /> 
		Purchase amount: $<%= purchaseAmount %> USD<br /> 
		Your account now has <%= d.getNLicensesRemaining() %> student licenses available.<br /><br />
		
		Please save a copy of this receipt for your records.<br /><br /> 
		
		Details: <%= orderDetails %>
<%
} else {    // first time here; present the offer to purchase exams
%>
<main><h1 style='display: none'>Purchase ChemVantage Student Licenses</h1>
	<div id=offer>
		
		<h3>Purchase Student Licenses for your ChemVantage Account</h3>
		Your ChemVantage account currently has
		<%= d.getNLicensesRemaining() %> student licenses remaining.<br /><br />
		You may purchase more licenses on behalf of students at your institution in the following quantities:
		<ul>
			<li>50 licenses @ $5.00 USD/each = $250 USD</li>
			<li>100 licenses @ $4.00 USD/each = $400 USD</li>
			<li>200 licenses @ $3.00 USD/each = $600 USD</li>
			<li>500 licenses @ $2.00 USD/each = $1000 USD</li>
		</ul>

		Please select the desired quantity to purchase: 
		<select id=nexamschoice onChange=updateAmount();>
			<option value=50>50</option>
			<option value=100>100</option>
			<option value=200>200</option>
			<option value=500>500</option>
		</select><br /><br /> 
		
		Select your preferred payment option below. If your institution
		requires an invoice to be paid via purchase order, please contact
		Chuck Wight at admin@chemvantage.org.<br><br> 
		
		When the payment process is completed you will receive a
		printable receipt on this page.<br /><br />

		<h2>
			Purchase: <span id=amt>50 licenses - $250 USD</span>
		</h2>
	</div>

	<div id="smart-button-container">
      <div style="text-align: center;">
        <div id="paypal-button-container"></div>
      </div>
    </div>
  
   <script src='https://www.paypal.com/sdk/js?client-id=<%= client_id %>&enable-funding=venmo&currency=USD'></script>
   <script>
   var nExams = 50;
   var value = "250";
   function updateAmount() {
	   nExams = document.getElementById("nexamschoice").value;
	   switch (nExams) {
	   case "50": value="250"; break;
	   case "100": value="400"; break;
	   case "200": value="600"; break;
	   case "500": value="1000"; break;
	   default: value="250";
	   }
	   document.getElementById("amt").innerHTML=nExams + ' licenses - $' + value + ' USD';
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
            purchase_units: [{"description":nExams + " ChemVantage Placement Exams",
            	"amount":{"currency_code":"USD","value":value}}]
          });
        },

        onApprove: function(data, actions) {
          return actions.order.capture().then(function(orderData) {
            
            // Full available details
            console.log('Capture result', orderData, JSON.stringify(orderData, null, 2));
			
            // Submit form
            document.getElementById('nexams').value=nExams;
            document.getElementById('purchaseamt').value=value;
            document.getElementById('orderdetails').value=JSON.stringify(orderData, null, 2);
            document.getElementById('activationForm').submit();
            
            // Make Paypal buttons invisible to  prevent duplicate submissions
            document.getElementById(id="smart-button-container").style='display:none';
            
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
  <input type=hidden name=NExams id=nexams />
  <input type=hidden name=PurchaseAmount id=purchaseamt />
  <input type=hidden name=OrderDetails id=orderdetails />
  <input type=hidden name=HashedId value='<%= user.getHashedId() %>' />
  </form>
<%
}
%>
</main>
<footer><hr/><img style='padding-left: 15px; vertical-align: middle;width:30px' src=images/CVLogo.png alt='ChemVantage logo' />&nbsp;
<a href=/index.html style='text-decoration: none;'><span style='color: blue;font-weight: bold;'>Chem</span><span style='color: #EE0000;font-weight: bold;'>Vantage</span></a> | 
<a href=/about.html#terms>Terms and Conditions of Use</a> | 
<a href=/about.html#privacy>Privacy Policy</a> | 
<a href=/about.html#copyright>Copyright</a></footer>

</body>
</html>