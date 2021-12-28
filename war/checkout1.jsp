<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.Date,java.text.DateFormat,com.googlecode.objectify.*,org.chemvantage.*,com.google.common.net.InternetDomainName"%>

<%
	String sig = request.getParameter("sig");
	User user = User.getUser(sig);
	if (user == null || user.isAnonymous()) response.sendError(401, "You must be logged in through your LMS to see this page.");
	String hashedId = request.getParameter("HashedId");
	boolean premiumUser = user.isPremium();
	boolean paidExam = ofy().load().type(PlacementExamTransaction.class).filter("userId",user.getHashedId()).count()>0;
	
	//Date now = new Date();
	//Date exp = new Date(now.getTime() +  15811200000L); // six months from now
	DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
	
	if (!paidExam && user.getHashedId().equals(hashedId)) {  // successful payment; process a user upgrade 
		PlacementExamTransaction pt = new PlacementExamTransaction();
		pt.setUserId(hashedId);
		ofy().save().entity(pt).now();
		if (!user.isPremium()) {
			PremiumUser u = new PremiumUser(hashedId);  // constructor automatically saves new entity
			premiumUser = true;
		}
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
<title>ChemVantage Placement Exam</title>
</head>

<body style='background-color: white; font-family: Calibri,Arial,sans-serif; max-width: 600px;'>
<%= Subject.banner %>

<h3>Purchase a ChemVantage Placement Exam</h3>
Your school or university has assigned you to complete a General Chemistry Placement Exam 
to determine your level of preparation to take the course. To purchase this exam, please select 
your preferred payment option below. The cost is $6.00 USD and includes a complimentary 
6-month subscription that gives you enhanced access to the detailed step-by-step solutions 
to ChemVantage homework problems. There is no additional charge for you to repeat the exam 
if this is allowed by the settings in your school's learning management system.<br/><br/>

When the payment process is completed you should return to your learning management system 
to relaunch this assignment and begin the exam. Thank you for helping to support ChemVantage.

<h2>$6.00 USD</h2>

<% if (paidExam) { %>

<h3>Thank you for your payment!</h3>
Please return to your school's LMS to launch the placement exam now.<br/><br/>
<% } else { %>

<div id="smart-button-container">
      <div style="text-align: center;">
        <div id="paypal-button-container"></div>
      </div>
    </div>
  <script src='https://www.paypal.com/sdk/js?client-id=AYlUNqRJZXhJJ9z7pG7GRMOwC-Y_Ke58s8eacfl1R51833ISAqOUhR8To0Km297MPcShAqm9ffp5faun&enable-funding=venmo&currency=USD'></script>
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
            purchase_units: [{"description":"ChemVantage Placement Exam invoice <%= user.getHashedId() %>",
            	"amount":{"currency_code":"USD","value":6}}]
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

</body>
</html>