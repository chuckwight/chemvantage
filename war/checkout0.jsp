
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
	String sig = request.getParameter("sig");
	User user = User.getUser(sig);
	String hashedId = request.getParameter("HashedId");
	String voucherCode = request.getParameter("VoucherCode");
	if (user == null || user.isAnonymous()) response.sendError(401, "You must be logged in through your LMS to see this page.");
	Date now = new Date();
	Date exp = null;
	DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.FULL);
	Deployment d = null;
	int nMonthsPurchased = 5;
	try {
		nMonthsPurchased = Integer.parseInt(request.getParameter("nmonths"));
	} catch (Exception e) {}
	int amountPaid = 0;
	try {
		d = ofy().load().type(Deployment.class).id(request.getParameter("d")).now();
		Voucher v = null;
		if (voucherCode != null) v = ofy().load().type(Voucher.class).id(voucherCode.toUpperCase()).now();
		
		if (user.getHashedId().equals(hashedId) || v.activate()) { // successful purchase or voucherCode submission;
			amountPaid = Integer.parseInt(request.getParameter("AmountPaid"));
			PremiumUser u = new PremiumUser(user.getHashedId(), nMonthsPurchased, amountPaid, d.getOrganization()); // constructor automatically saves new entity
			exp = u.exp;
%> 
			<h2>Thank you for your payment!</h2>
			Your ChemVantage subscription is now active and expires on <%= df.format(exp) %><br/>
			Print or save this page as proof of purchase. Then return to your LMS and relaunch the assignment.<br/><br/>	
			Details: <%= request.getParameter("OrderDetails") %><br/>
			Months Purchased: <%= request.getParameter("nmonths") %>
<%
		} else throw new Exception("Invalid hashedId or voucherCode");
	} catch (Exception e) {  // the remainder of the JSP is devoted to presenting the purchase page

		String client_id = System.getProperty("com.google.appengine.application.id").equals("dev-vantage-hrd")
		? "AVJ8NuVQnTBwTbmkouWCjZhUT_eHeTm9fjAKwXJO0-RK-9VZFBtWm4J6V8o-47DvbOoaVCUiEb4JMXz8": // Paypal sandbox client_id
		"AYlUNqRJZXhJJ9z7pG7GRMOwC-Y_Ke58s8eacfl1R51833ISAqOUhR8To0Km297MPcShAqm9ffp5faun"; // Paypal live client_id
%>
		<h3>Individual ChemVantage Subscription</h3>
		A subscription is required to access ChemVantage assignments created by your instructor through this learning management system.<br/><br/>
<% 
		PremiumUser u = ofy().load().type(PremiumUser.class).id(user.getHashedId()).now();		
		if (u != null && u.exp.before(now)) {
%>		
			<h4>Your ChemVantage subscription expired: <%= df.format(u.exp) %></h4>
<%	
		}
		
		int nVouchersAvailable = ofy().load().type(Voucher.class).filter("activated",null).count();
		if (nVouchersAvailable > 0) {
%>
		<form method=post>
			<input type=hidden name=sig value='<%= user.getTokenSignature() %>' />
  			<input type=hidden name=d value='<%= d.getPlatformDeploymentId() %>' />
  			<input type=hidden name=nmonths value=12 />
  			<input type=hidden name=AmountPaid value='<%= 8*d.price %>' />
 			<input type=hidden name=OrderDetails value='Voucher' />
			If you have a subscription voucher, please enter the code here: <input type=text size=10 name=VoucherCode /> <br/>
			<label><input type=checkbox name=terms value=true> I have read and understood the <a href=/about.html#terms target=_blank>ChemVantage Terms and Conditions of Use</a></label> <br/>
			<input type=submit />
		</form>
		Otherwise, please select the desired number of months you wish to purchase:
<% 
		} else {
%>	
		Please select the desired number of months you wish to purchase: 
<%
		}
%>
		<select id=nMonthsChoice onChange=updateAmount();>
			<option value=1>1 month</option>
			<option value=2>2 months</option>
			<option value=5>5 months</option>
			<option value=12>12 months</option>
		</select><br />
		<label><input type=checkbox name=terms value=true> I have read and understood the <a href=/about.html#terms target=_blank>ChemVantage Terms and Conditions of Use</a></label> <br/><br/>
			
		Select your preferred payment method below. When the transaction is completed, your subscription will be activated immediately.

		<h2>Purchase: <span id=amt></span></h2>

		<div id="smart-button-container">
      	  <div style="text-align: center;">
            <div id="paypal-button-container"></div>
          </div>
        </div>
 		<script src='https://www.paypal.com/sdk/js?client-id=<%= client_id %>&enable-funding=venmo&currency=USD'></script>
   		<script>
  		var nMonths = <%= nMonthsPurchased %>;
   		var amtPaid = "";
   		var nMonthsInp = document.getElementById("nMonthsChoice");
   		if (nMonths==5) nMonthsInp.selectedIndex=2;
   		function updateAmount() {
	   		nMonths = nMonthsInp.options[nMonthsInp.selectedIndex].value;
	   		switch (nMonths) {
	   		case "1": amtPaid="<%= d.price %>"; break;
	   		case "2": amtPaid="<%= 2*d.price %>"; break;
	   		case "5": amtPaid="<%= 4*d.price %>"; break;
	   		case "12": amtPaid="<%= 8*d.price %>"; break;
	   		}
	   		document.getElementById("amt").innerHTML=nMonths + (nMonths=="1"?' month':' months') + ' - $' + amtPaid + '.00 USD';
    	}
   		updateAmount();
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
            	"amount":{"currency_code":"USD","value":amtPaid+".00"}}]
          });
        },

        onApprove: function(data, actions) {
          return actions.order.capture().then(function(orderData) {
            
            // Full available details
            console.log('Capture result', orderData, JSON.stringify(orderData, null, 2));
			
            // Submit form
            document.getElementById('nmonths').value=nMonths;
            document.getElementById('amtPaid').value=amtPaid;
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
  
  		<form id=activationForm method=post action='/checkout0.jsp'>
  		<input type=hidden name=sig value='<%= user.getTokenSignature() %>' />
  		<input type=hidden name=d value='<%= d.getPlatformDeploymentId() %>' />
  		<input type=hidden name=nmonths id=nmonths />
  		<input type=hidden name=AmountPaid id=amtPaid />
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