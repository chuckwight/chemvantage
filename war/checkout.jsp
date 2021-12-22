<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="static com.googlecode.objectify.ObjectifyService.ofy" %>
<%@ page import="java.util.Date,java.text.DateFormat,com.googlecode.objectify.*,org.chemvantage.*,com.google.common.net.InternetDomainName"%>

<%
	String sig = request.getParameter("sig");
	Date now = new Date();
	Date exp = new Date(now.getTime() +  15811200000L); // six months from now
	DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.FULL);
%>

<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Insert title here</title>
</head>
<body>
<%= Subject.banner %>
<h3>Subscription Services</h3>
As a ChemVantage subscriber, you will have access to the detailed step-by-step solutions to each homework 
problem after you submit the correct answer. This service costs just $5.00 and lasts for 6 months.<br/><br/>
Please select your preferred payment method below. Your subscription will activate immediately after payment. 
Thank you for supporting ChemVantage.
<div id="smart-button-container">
      <div style="text-align: center;">
        <div id="paypal-button-container"></div>
      </div>
    </div>
  <script src="https://www.paypal.com/sdk/js?client-id=sb&enable-funding=venmo&currency=USD" data-sdk-integration-source="button-factory"></script>
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
            purchase_units: [{"description":"6-month ChemVantage subscription for user: " + <%= sig %> + " Expires: " + <%= df.format(exp) %>,"amount":{"currency_code":"USD","value":5}}]
          });
        },

        onApprove: function(data, actions) {
          return actions.order.capture().then(function(orderData) {
            
            // Full available details
            console.log('Capture result', orderData, JSON.stringify(orderData, null, 2));

            // Show a success message within this page, e.g.
            const element = document.getElementById('paypal-button-container');
            element.innerHTML = '';
            element.innerHTML = '<h3>Thank you for your payment!</h3>';

            // Or go to another URL:  actions.redirect('thank_you.html');
            
          });
        },

        onError: function(err) {
          console.log(err);
        }
      }).render('#paypal-button-container');
    }
    initPayPalButton();
  </script>

</body>
</html>