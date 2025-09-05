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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.DateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


@WebServlet("/checkout")
public class Checkout extends HttpServlet {

	private static final long serialVersionUID = 137L;
	private static int price = 2;
	private JsonObject auth_json = new JsonObject();
	
	public String getServletInfo() {
		return "This servlet is used by students to purchase ChemVantage subscriptions.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user.isPremium()) {  // do not allow the user to use this page
				Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
				if (a != null) response.sendRedirect("/" + a.assignmentType + "?sig=" + user.getTokenSignature());
				else out.println("Your subscription is active.");
				return;
			}
			Deployment deployment = ofy().load().type(Deployment.class).id(request.getParameter("d")).now();
			if (user==null || deployment==null) throw new Exception("You must be logged in through your class LMS to see this page.");
			out.println(checkoutPage(user,deployment));	
		} catch (Exception e) {
			out.println(Subject.header("Logout") + Logout.now(request,e) + Subject.footer);
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("application/json");
		PrintWriter out = response.getWriter();
		User user = null;
		JsonObject res = new JsonObject();
		
		try {
			user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception("You must be logged in through your class LMS to see this page.");
			
			if (user.isPremium()) {  // do not allow the user to use this page
				Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
				if (a != null) response.sendRedirect("/" + a.assignmentType + "?sig=" + user.getTokenSignature());
				else out.println("Your subscription is active.");
				return;
			}
			
			String userRequest = request.getParameter("UserRequest");
			switch (userRequest) {
			case "RedeemVoucher":
				Date exp = redeemVoucher(user,request);
				res.addProperty("exp", exp.toString());
				out.println(res.toString());
				break;
			case "CreateOrder":
				String order_id = createOrder(user,request);
				res.addProperty("id", order_id);
				out.println(res.toString());
				break;
			case "CompleteOrder":
				out.println(completeOrder(user,request).toString());
				break;
			default:
				response.sendError(400);  // Bad request
			}
		} catch (Exception e) {
			res.addProperty("error", e.getMessage());
			//out.println(res.toString());
			response.sendError(401,res.toString());
			Logout.now(request,e);
		}
	}

	static String checkoutPage(User user, Deployment d) {
		StringBuffer buf = new StringBuffer();
		
		Date now = new Date();
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.FULL);
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
		
		buf.append(Subject.header("ChemVantage Subscription", "checkout") + Subject.banner);
		
		// Store the PayPal client_id and user's sig value and platform_deployment_id in DOM elements so they can be accessed by javascript
		buf.append("<input type=hidden id=client_id value='" + Subject.getPayPalClientId() + "' />"); 		
		buf.append("<input type=hidden id=sig value=" + user.getTokenSignature() + " />");
		buf.append("<input type=hidden id=platform_deployment_id value=" + d.getPlatformDeploymentId() + " />");
		buf.append("<input type=hidden id=price value=" + price + " />");

		PremiumUser u = ofy().load().type(PremiumUser.class).id(user.getHashedId()).now();		
		String title = (u != null && u.exp.before(now))?"Your ChemVantage subscription expired on " + df.format(u.exp):"Individual ChemVantage Subscription";

		buf.append("<h1>" + title + "</h1>\n"
				+ "A subscription is required to access ChemVantage assignments created by your instructor through this learning management system. "
				+ "First, please indicate your agreement with the two statements below by checking the boxes.<br/><br/>"
				+ "<label><input type=checkbox id=terms onChange=showSelectPaymentMethod();> I understand and agree to the <a href=/terms_and_conditions.html target=_blank>ChemVantage Terms and Conditions of Use</a>.</label> <br/>"
				+ "<label><input type=checkbox id=norefunds onChange=showSelectPaymentMethod();> I understand that all ChemVantage subscription fees are non-refundable.</label> <br/><br/>");
		
		buf.append("<div id=select_payment_method style='display:none'>\n");

		buf.append( "If you have a subscription voucher, please enter the code here: "
				+ "<input id=voucher_code type=text size=10 />&nbsp;"
				+ "<button class=btn onclick=redeemVoucher('" + user.getTokenSignature() + "','" + d.getPlatformDeploymentId() + "')>&nbsp;Redeem</button><br/>");
		
		buf.append("<hr>Otherwise, please select the desired number of months you wish to purchase:<br/>"
				+ "<div style='align: center'>"
				+ "<select id=nmonths>"
				+ "<option value=1>1 month - $" + 1*price + " USD</option>"
				+ "<option value=2>2 months - $" + 2*price + " USD</option>"
				+ "<option value=5 selected>5 months - $" + 4*price + " USD</option>"
				+ "<option value=12>12 months - $" + 8*price + " USD</option>"
				+ "</select>&nbsp;"
				+ "<button class=btn onclick=startCheckout();>Checkout</button>"
				+ "</div>");		
		buf.append("</div>");  // end of 'select_payment_method' div
		
		// Create a div for displaying the PayPal payment buttons (initially hidden)
		buf.append("<div id=payment_div style='display: none'>"
				+ "Please select your method of payment:<br/><br/>"
				+ "<div id='paypal-button-container'></div>");
		buf.append("</div>");  // end of payment div
		
		buf.append("<div id=proceed style='display: none'><br/><br/>"
				+ "<a class='btn btn-two' href='/" + a.assignmentType + "?sig=" + user.getTokenSignature() + "'>Proceed to your assignment</a><br/><br/>"
				+ "</div>");
		
		buf.append(Subject.footer);
		
		return buf.toString();
	}
	
	Date redeemVoucher(User user, HttpServletRequest request) throws Exception {
		PremiumUser pu = null;
		
		String voucherCode = request.getParameter("voucher_code");
		if (voucherCode==null || voucherCode.isEmpty()) throw new Exception("Sorry, the voucher code was missing or invalid.");
		voucherCode = voucherCode.toUpperCase();
		Voucher v = ofy().load().type(Voucher.class).id(voucherCode).now();
		if (v==null) throw new Exception("Sorry, the voucher code was missing or invalid.");
		if (!v.activate()) { // check for duplicate submission by same user
			pu = ofy().load().type(PremiumUser.class).id(user.hashedId).safe();
			if (pu.order_id.equals(v.code)) return pu.exp;
			else throw new Exception("This voucher code was redeemed previously by another user.");
		}

		// code is valid, so create a new PremiumUser
		Deployment deployment = ofy().load().type(Deployment.class).id(request.getParameter("d")).safe();
		if (pu == null) pu = new PremiumUser(user.getHashedId(), v.months, v.paid, deployment.getOrganization(),v.code); // constructor automatically saves new entity
		return pu.exp;
	}
	
	String generateAccessToken() throws Exception {
		/**
		 * Generate an OAuth 2.0 access token for authenticating with PayPal REST APIs.
		 * @see https://developer.paypal.com/api/rest/authentication/
		 */
		Date now = new Date();
		
		try {  // First, see if there is a cached auth token:
			
			if (auth_json.isEmpty() || new Date(auth_json.get("exp").getAsLong()).after(now)) throw new Exception();
			return auth_json.get("access_token").getAsString();
			
		} catch (Exception e) {  // retrieve a new auth token from PayPal
			
			String auth = Base64.getEncoder().encodeToString((Subject.getPayPalClientId()+":"+Subject.getPayPalClientSecret()).getBytes());

			String baseUrl = "https://api-m." + (Subject.getProjectId().equals("dev-vantage-hrd")?"sandbox.":"") + "paypal.com";
			String body = "grant_type=client_credentials";

			URL u = new URI(baseUrl + "/v1/oauth2/token").toURL();
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setDoInput(true);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Authorization", "Basic " + auth);
			uc.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
			uc.setRequestProperty("Accept", "application/json;charset=UTF-8");
			uc.setRequestProperty("charset", "utf-8");
			uc.setUseCaches(false);
			uc.setReadTimeout(15000);  // waits up to 15 s for server to respond
			// send the message
			DataOutputStream wr = new DataOutputStream(uc.getOutputStream());
			wr.writeBytes(body);
			wr.close();

			BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));				
			auth_json = JsonParser.parseReader(reader).getAsJsonObject();
			reader.close();

			// Cache the auth_json for future use
			int expires_in = auth_json.get("expires_in").getAsInt();  // seconds from now
			Long exp = new Date(new Date().getTime() + expires_in*1000L - 5000L).getTime();  // exp millis - 5 s grace
			auth_json.addProperty("exp", exp);
		
			return auth_json.get("access_token").getAsString();
		}
	}
	
	String createOrder(User user, HttpServletRequest request) throws Exception {	
		int nMonths = Integer.parseInt(request.getParameter("nmonths"));
		int value = price * (nMonths - nMonths/3);
		
		String platform_deployment_id = request.getParameter("d");
		String request_id = UUID.randomUUID().toString();
		
		JsonObject order_data = new JsonObject();
		order_data.addProperty("intent", "CAPTURE");
		  JsonArray purchaseUnits = new JsonArray();
		    JsonObject subscription = new JsonObject();
		    subscription.addProperty("description", nMonths + " - month ChemVantage subscription");
		      JsonObject amount = new JsonObject();
		      amount.addProperty("currency_code", "USD");
		      amount.addProperty("value", (price * (nMonths - nMonths/3)) + ".00");  // calculated discount schedule
		    subscription.add("amount", amount);
		 purchaseUnits.add(subscription);
		order_data.add("purchase_units", purchaseUnits);
		
		String baseUrl = "https://api-m." + (Subject.getProjectId().equals("dev-vantage-hrd")?"sandbox.":"") + "paypal.com";
		
		URL u = new URI(baseUrl + "/v2/checkout/orders").toURL();
		HttpURLConnection uc = (HttpURLConnection) u.openConnection();
		uc.setRequestMethod("POST");
		uc.setRequestProperty("Authorization", "Bearer " + generateAccessToken());
		uc.setRequestProperty("PayPal-Request-Id", request_id);
		uc.setRequestProperty("Content-Type","application/json");
		uc.setDoOutput(true);
		
		OutputStreamWriter writer = new OutputStreamWriter(uc.getOutputStream());
		writer.write(order_data.toString());
		writer.flush();
		writer.close();
		uc.getOutputStream().close();

		BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));				
		String order_id = JsonParser.parseReader(reader).getAsJsonObject().get("id").getAsString();
		reader.close();
		
		ofy().save().entity(new PayPalOrder(order_id,new Date(),order_data.toString(),nMonths,value,user,platform_deployment_id,request_id));
		
		return order_id;
	}
	
	JsonObject completeOrder(User user, HttpServletRequest request) throws Exception {
		String order_id = request.getParameter("order_id");
		PayPalOrder order = ofy().load().type(PayPalOrder.class).id(order_id).safe();
		Deployment deployment = ofy().load().type(Deployment.class).id(order.platform_deployment_id).now();
		String baseUrl = "https://api-m." + (Subject.getProjectId().equals("dev-vantage-hrd")?"sandbox.":"") + "paypal.com";
		
		URL u = new URI(baseUrl + "/v2/checkout/orders/" + order_id + "/capture").toURL();
		HttpURLConnection uc = (HttpURLConnection) u.openConnection();
		uc.setRequestMethod("POST");
		uc.setRequestProperty("Authorization", "Bearer " + generateAccessToken());
		uc.setRequestProperty("PayPal-Request-Id", order.request_id);
		uc.setRequestProperty("Content-Type","application/json");
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));				
		JsonObject resp = JsonParser.parseReader(reader).getAsJsonObject();
		reader.close();
		
		order.status = resp.get("status").getAsString();  // update order status
		if (order.status.equals("COMPLETED")) {  // create new PremiumUser
			PremiumUser pu = new PremiumUser(user.getHashedId(), order.nMonths, order.value, deployment.getOrganization(),order.id);
			resp.addProperty("expires", pu.exp.toString());
		}
		ofy().save().entity(order);
		
		return resp;
	}
	
}

@Entity
class PayPalOrder {
	@Id 	String id;    // this is a PayPal-generated value for the path of API calls
	@Index 	Date created;
			String request_id;  // this is a ChemVantage-generated value for the request headers
			String order_data;
			int nMonths;
			int value;
			String hashedId;
			String platform_deployment_id;
			String status = "CREATED";
			
	PayPalOrder() {}
	PayPalOrder(String id, Date created, String order_data, int nMonths, int value, User user, String platform_deployment_id, String request_id) {
		this.id = id;
		this.created = created;
		this.order_data = order_data;
		this.nMonths = nMonths;
		this.value = value;
		this.hashedId = user.hashedId;
		this.platform_deployment_id = platform_deployment_id;
		this.request_id = request_id;
	}
}