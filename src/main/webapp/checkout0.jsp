
<!-- This JSP file presents a form to students to purchase an individual subscription. -->

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.Date,java.text.DateFormat,com.googlecode.objectify.*,org.chemvantage.*,com.google.common.net.InternetDomainName"%>

<%= Subject.header("ChemVantage Subscription") %>

<%
	String sig = request.getParameter("sig");
	User user = User.getUser(sig);
	String platformDeploymentId = request.getParameter("d");
	String hashedId = request.getParameter("HashedId");
	String voucherCode = request.getParameter("VoucherCode");
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
		if (user == null || user.isAnonymous() || platformDeploymentId==null) throw new Exception("You must be logged in through your LMS to see this page.");
		d = ofy().load().type(Deployment.class).id(platformDeploymentId).safe();
		Voucher v = null;
		boolean voucherRedeemed = false;
		if (voucherCode != null) {
			voucherCode = voucherCode.toUpperCase();
			v = ofy().load().type(Voucher.class).id(voucherCode).now();
			if (v==null) throw new Exception("The voucher code was invalid.");
			if (v.activate()) voucherRedeemed = true;
			else throw new Exception("The voucher code was redeemed previously.");
		}
		
		if (user.getHashedId().equals(hashedId) || voucherRedeemed) { // successful purchase or voucherCode submission;
			amountPaid = Integer.parseInt(request.getParameter("AmountPaid"));
			PremiumUser u = new PremiumUser(user.getHashedId(), nMonthsPurchased, amountPaid, d.getOrganization()); // constructor automatically saves new entity
			exp = u.exp;
			String details = request.getParameter("OrderDetails");
			if (voucherRedeemed) details = details + " " + voucherCode;
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
%> 
			<h1>Thank you for your purchase!</h1>
			Your ChemVantage subscription is now active and expires on <%= df.format(exp) %><br/>
			Print or save this page as proof of purchase.<br/><br/>	
			Details: <%= details %><br/>
			Months Purchased: <%= request.getParameter("nmonths") %><br/><br/>
			<a class="btn btn-two" href="/<%= a.assignmentType %>?sig=<%= user.getTokenSignature() %>">Continue to your assignment</a><br/><br/>
<%
		} else {
			
			String client_id = Subject.projectId.equals("dev-vantage-hrd")
			? "AVJ8NuVQnTBwTbmkouWCjZhUT_eHeTm9fjAKwXJO0-RK-9VZFBtWm4J6V8o-47DvbOoaVCUiEb4JMXz8": // Paypal sandbox client_id
			"AYlUNqRJZXhJJ9z7pG7GRMOwC-Y_Ke58s8eacfl1R51833ISAqOUhR8To0Km297MPcShAqm9ffp5faun"; // Paypal live client_id
		
			PremiumUser u = ofy().load().type(PremiumUser.class).id(user.getHashedId()).now();		
			String title = (u != null && u.exp.before(now))?"Your ChemVantage subscription expired on " + df.format(u.exp):"Individual ChemVantage Subscription";
%>	
			<h1><%= title %></h1>
			A subscription is required to access ChemVantage assignments created by your instructor through this learning management system. 
			First, indicate your agreement with the two statements below by checking the boxes.<br/><br/>
		
			<label><input type=checkbox id=terms onChange=showPurchase();> I understand and agree to the <a href=/terms_and_conditions.html target=_blank>ChemVantage Terms and Conditions of Use</a>.</label> <br/>
			<label><input type=checkbox id=norefunds onChange=showPurchase();> I understand that all ChemVantage subscription fees are non-refundable.</label> <br/><br/>

			<div id=purchase style='display:none'>
<%		
			int nVouchersAvailable = ofy().load().type(Voucher.class).filter("activated",null).count();
			if (nVouchersAvailable > 0) {
%>
				<form method=post>
					<input type=hidden name=sig value='<%= user.getTokenSignature() %>' />
  					<input type=hidden name=d value='<%= d.getPlatformDeploymentId() %>' />
  					<input type=hidden name=nmonths value=12 />
  					<input type=hidden name=AmountPaid value='<%= 8*d.price %>' />
 					<input type=hidden name=OrderDetails value='Voucher' />
					If you have a subscription voucher, please enter the code here: <input type=text size=10 name=VoucherCode />
					<input type=submit />
				</form>
				<br/>Otherwise, please select the desired number of months you wish to purchase:
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
			</select><br/><br/>
			
			Select your preferred payment method below. When the transaction is completed, your subscription will be activated immediately.

			<h2>Purchase: <span id=amt></span></h2>

			<div id="smart-button-container">
      		  <div style="text-align: center;">
     	        <div id="paypal-button-container"></div>
      	      </div>
      	    </div>
        
       	</div>
        
 		<script src='https://www.paypal.com/sdk/js?client-id=<%= client_id %>&enable-funding=venmo&currency=USD'></script>
   		<script>
   		var agreeTerms = document.getElementById('terms');
   		var agreeNoRefunds = document.getElementById('norefunds');
   		var purchase = document.getElementById('purchase');
   		function showPurchase() {
   			if (agreeTerms.checked && agreeNoRefunds.checked) purchase.style = 'display:inline';
   			else purchase.style = 'display:none';
   		}
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
	} catch (Exception e) {
		if (user != null) ofy().delete().entity(user).now();  // log the user out
%>
		<h1>ChemVantage Subscription Activation Failed</h1>
		Sorry, something went wrong. Please return to your LMS and launch the assignment again.<br/><br/>
		<%= e.getMessage()==null?e.toString():e.getMessage() %> <br/><br/>
		If you need assistance, please send email to <a href='mailto:admin@chemvantage.org'>admin@chemvantage.org</a><br/><br/>
<%
	}
%>

<%= Subject.footer %>