  var agreeTerms = document.getElementById('terms');
  var agreeNoRefunds = document.getElementById('norefunds');
  var purchase = document.getElementById('purchase');
  function showPurchase() {
    if (agreeTerms.checked && agreeNoRefunds.checked) purchase.style = 'display:inline';
   	else purchase.style = 'display:none';
  }
  var price = 2;
  var nMonths = 5;
  var amtPaid = "";
  var nMonthsInp = document.getElementById("nMonthsChoice");
   		
  function updateAmount() {
    nMonths = nMonthsInp.options[nMonthsInp.selectedIndex].value;
	switch (nMonths) {
	case "1": amtPaid=price; break;
	case "2": amtPaid=2*price; break;
	case "5": amtPaid=4*price; break;
	case "12": amtPaid=8*price; break;
	}
	document.getElementById("amt").innerHTML=nMonths + (nMonths=="1"?' month':' months') + ' - $' + amtPaid + '.00 USD';
  }
  updateAmount();
  function initPayPalButton(hashedId) {
    paypal.Buttons({
    style: {
      shape: 'pill',
      color: 'gold',
      layout: 'vertical',
      label: 'checkout',         
    },
    createOrder: function(data, actions) {
      return actions.order.create({
        purchase_units: [{"description":nMonths + "-month ChemVantage subscription for user: " + hashedId,"amount":{"currency_code":"USD","value":amtPaid+".00"}}]});
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

