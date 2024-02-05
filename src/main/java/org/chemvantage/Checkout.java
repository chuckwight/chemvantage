/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2024 ChemVantage LLC
*   
*    This program is free software: you can redistribute it and/or modify
*   it under the terms of the GNU General Public License as published by
*   the Free Software Foundation, either version 3 of the License, or
*   (at your option) any later version.
*
*   This program is distributed in the hope that it will be useful,
*   but WITHOUT ANY WARRANTY; without even the implied warranty of
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*   GNU General Public License for more details.
*
*   You should have received a copy of the GNU General Public License
*   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Date;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


@WebServlet("/checkout")
public class Checkout extends HttpServlet {

	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "This servlet is used by students to purchase ChemVantage subscriptions.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		try {
			User user = User.getUser(request.getParameter("sig"));
			Deployment deployment = ofy().load().type(Deployment.class).id(request.getParameter("d")).now();
			if (user==null || deployment==null) throw new Exception("You must be logged in through your class LMS to see this page.");
			out.println(checkoutForm(user,deployment));	
		} catch (Exception e) {
			out.println(Subject.header() + Logout.now(request,e) + Subject.footer);
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		User user = null;
		
		try {
			user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception("You must be logged in through your class LMS to see this page.");
			String paymentMethod = validatePayment(user,request);
			int nMonthsPurchased = 5;
			try {
				nMonthsPurchased = Integer.parseInt(request.getParameter("nmonths"));
			} catch (Exception e) {}
			int amountPaid = 0;
			try {
				Integer.parseInt(request.getParameter("AmountPaid"));
			} catch (Exception e) {}
			Deployment deployment = ofy().load().type(Deployment.class).id(request.getParameter("d")).now();
			new PremiumUser(user.getHashedId(), nMonthsPurchased, amountPaid, deployment.getOrganization()); // constructor automatically saves new entity
			String details = request.getParameter("OrderDetails");
			out.println(thankYouPage(user, paymentMethod, details));
		} catch (Exception e) {
			out.println(Subject.header("Logout") + Logout.now(user) + Subject.footer);
		}
	}

	static String checkoutForm(User user, Deployment d) {
		StringBuffer buf = new StringBuffer();
		
		Date now = new Date();
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.FULL);
		
		buf.append(Subject.header("ChemVantage Subscription") + Subject.banner);
		
		String client_id = Subject.projectId.equals("dev-vantage-hrd")
				? "AVJ8NuVQnTBwTbmkouWCjZhUT_eHeTm9fjAKwXJO0-RK-9VZFBtWm4J6V8o-47DvbOoaVCUiEb4JMXz8": // Paypal sandbox client_id
					"AYlUNqRJZXhJJ9z7pG7GRMOwC-Y_Ke58s8eacfl1R51833ISAqOUhR8To0Km297MPcShAqm9ffp5faun"; // Paypal live client_id

		PremiumUser u = ofy().load().type(PremiumUser.class).id(user.getHashedId()).now();		
		String title = (u != null && u.exp.before(now))?"Your ChemVantage subscription expired on " + df.format(u.exp):"Individual ChemVantage Subscription";

		buf.append("<h1>" + title + "</h1>\n"
				+ "A subscription is required to access ChemVantage assignments created by your instructor through this learning management system. "
				+ "First, please indicate your agreement with the two statements below by checking the boxes.<br/><br/>"
				+ "<label><input type=checkbox id=terms onChange=showPurchase();> I understand and agree to the <a href=/terms_and_conditions.html target=_blank>ChemVantage Terms and Conditions of Use</a>.</label> <br/>"
				+ "<label><input type=checkbox id=norefunds onChange=showPurchase();> I understand that all ChemVantage subscription fees are non-refundable.</label> <br/><br/>"
				+ "<div id=purchase style='display:none'>\n");

		int nVouchersAvailable = ofy().load().type(Voucher.class).filter("activated",null).count();
		if (nVouchersAvailable > 0) {
			buf.append("<form method=post>"
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
					+ "<input type=hidden name=d value='" + d.getPlatformDeploymentId() + "' />"
					+ "<input type=hidden name=nmonths value=12 />"
					+ "<input type=hidden name=AmountPaid value='0' />"
					+ "<input type=hidden name=OrderDetails value='Voucher' />"
					+ "If you have a subscription voucher, please enter the code here: <input type=text size=10 name=VoucherCode />"
					+ "<input type=submit />"
					+ "</form>\n"
					+ "<br/>Otherwise, please select the desired number of months you wish to purchase:");
		} else {
			buf.append("Please select the desired number of months you wish to purchase:");
		}
		buf.append("<select id=nMonthsChoice onChange=updateAmount();>"
				+ "<option value=1>1 month</option>"
				+ "<option value=2>2 months</option>"
				+ "<option value=5>5 months</option>"
				+ "<option value=12>12 months</option>"
				+ "</select><br/><br/>"
				+ "Select your preferred payment method below. When the transaction is completed, your subscription will be activated immediately."
				+ "<h2>Purchase: <span id=amt></span></h2>"
				+ "  <div id=\"smart-button-container\">"
				+ "    <div style=\"text-align: center;\">"
				+ "      <div id=\"paypal-button-container\"></div>"
				+ "    </div>"
				+ "  </div>"
				+ "</div>\n");
		
		buf.append("<script src='https://www.paypal.com/sdk/js?client-id=" + client_id +"&enable-funding=venmo&currency=USD'></script>\n");
		buf.append("<script src=/js/checkout_student.js></script>");
		return buf.toString();
	}
	
	String validatePayment(User user,HttpServletRequest request) throws Exception {
		String hashedId = request.getParameter("HashedId");
		String voucherCode = request.getParameter("VoucherCode");
		String paymentMethod = "";
		
		if (voucherCode != null) {  // student is redeeming a subscription voucher purchased elsewhere
			voucherCode = voucherCode.toUpperCase();
			Voucher v = ofy().load().type(Voucher.class).id(voucherCode).now();
			if (v==null) throw new Exception("This voucher code was invalid.");
			if (v.activate()) paymentMethod = "voucher";
			else throw new Exception("It looks like this voucher code was redeemed previously.");
		} else if (hashedId != null && user.getHashedId().equals(hashedId)) {
			paymentMethod = "paypal";
		} else throw new Exception("Sorry, the purchase failed. Try again later or contact admin@chemvantage.org for assistance.");
		
		return paymentMethod;
	}
	
	String thankYouPage(User user, String paymentMethod, String details) throws Exception {
		StringBuffer buf = new StringBuffer();
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.FULL);
		
		try {
			PremiumUser u = ofy().load().type(PremiumUser.class).id(user.getHashedId()).safe();
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
			buf.append(Subject.header("Thank you") + Subject.banner + "<h1>Thank you for your purchase</h1>"
					+ "Your ChemVantage subscription is now active and expires on " + df.format(u.exp) + "<br/>"
					+ "Print or save this page as proof of purchase.<br/><br/>\n"
					+ "<a href='/" + a.assignmentType + "?sig=" + user.getTokenSignature() + "'><button class=btn>Proceed to your assignment</button></a><br/><br/>"
					+ "Purchase details: " + details);
		} catch (Exception e) {
			buf.append("<h1>Oops, something went wrong</h1>Please contact admin@chemvantage.org for support.");
			String message = "Student Payment Error: " + e.getMessage()==null?e.toString():e.getMessage() + "<p>"
					+ "User hashedId: " + user.getHashedId() + "<br/>"
					+ "Payment method: " + paymentMethod + "<br/>"
					+ "Details: " + details;
			Utilities.sendEmail("ChemVantage LLC", "admin@chemvantage.org", "Student Payment Error", message);
		}
		return buf.toString();
	}
}