/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2011 ChemVantage LLC
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.googlecode.objectify.Objectify;

public class Upgrade extends HttpServlet {

	private static final long serialVersionUID = 137L;
	DAO dao = new DAO();
	Objectify ofy = dao.ofy();
	Subject subject = dao.getSubject();
	static String features = "<li>Assignment Deadlines - These are set by the course instructor."
		+ "<li>Reminders - Premium users have the option of receiving email or SMS reminders of assignment deadlines."
		+ "<li>Learner Analytics - The course instructor can set automatic notifications if assignments are missed."
		+ "<li>LTI Integration - Scores are returned directly to the class learning management system";
	
	public String getServletInfo() {
		return "This servlet allows the user to upgrade to a premium level account.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		User user = User.getInstance(request.getSession(true));
		if (user==null || (Login.lockedDown && !user.isAdministrator())) {
			response.sendRedirect("/");
			return;
		}
		else if ("No Thanks".equals(request.getParameter("Action"))) {
			response.sendRedirect("/Home");
		}

		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		if ("completed".equals(request.getParameter("purchase"))) out.println(Home.getHeader(user) + thankYou() + Home.footer);
		else out.println(Home.getHeader(user) + printUpgradeOptions(user) + Home.footer);
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		processPayPalIPN(request);
	}

	String printUpgradeOptions(User user) {
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>ChemVantage Basic and Premium Accounts</h2>"
		+ "Anyone can obtain a basic account at ChemVantage.org at no cost. "
		+ "All of the functionality required for completing quiz and homework assignments "
		+ "is included in the free basic account.<p>For a one-time fee, users may "
		+ "upgrade to a premium account, which allows users to join a group or class of students "
		+ "using the site. Additional group features include:<ol>" + features + "</ol>");
		
		if (user.premium) buf.append("Your account has already been upgraded to premium status.");
		else {
			buf.append("<span style='color:red'><b>100% Satisfaction Guarantee</b></span><br>"
				+ "If you aren't satisfied for any reason, ChemVantage will cheerfully refund your payment in full.<p>"
				+ "<TABLE>"
				+ "<TR><TD ALIGN=CENTER><b>Instant ChemVantage Premium Account Upgrade</b></TD></TR>"
				+ "<TR><TD ALIGN=CENTER><b>$4.99 USD</b></TD></TR><TR><TD ALIGN=CENTER> "
				+ "<form action=https://www.paypal.com/cgi-bin/webscr method=post>"
				+ "<input type=hidden name=cmd value=_s-xclick>"
				+ "<input type=hidden name=hosted_button_id value=" + (user.authDomain.equals("BLTI")?"U58TNLE8YE4AW":"HKW9475B55NJU") + ">"
				+ "<input type=hidden name=on0 value=userId><input type=hidden name=os0 value=" + user.id + ">"
				+ "<input type=image src=https://www.paypalobjects.com/en_US/i/btn/btn_buynowCC_LG.gif border=0 name=submit alt='PayPal - The safer, easier way to pay online!'>"
				+ "<br><font size=-2>Your payment will be processed by PayPal.com</font>"
				+ "<img alt='' border=0 src=https://www.paypalobjects.com/en_US/i/scr/pixel.gif width=1 height=1>"
				+ "</form>"
				+ "</TD></TR></TABLE>");
		}
		return buf.toString();
	}
	
	void processPayPalIPN(HttpServletRequest request) {
		// This method listens for an Instant Payment Notification (IPN) post from PayPal,
		// then posts the same parameters back to PayPal and listens for a single-word
		// response of "VERIFIED" or "INVALID".  Selected parameters are stored in a new
		// PayPalIPN object and committed to the datastore.
		try {
			
			@SuppressWarnings("rawtypes")
			Enumeration en = request.getParameterNames();
			String str = "cmd=_notify-validate";
			while(en.hasMoreElements()){
				String paramName = (String)en.nextElement();
				String paramValue = request.getParameter(paramName);
				str = str + "&" + paramName + "=" + URLEncoder.encode(paramValue,"UTF-8");
			}
			
			URL u = new URL("http://www.paypal.com/cgi-bin/webscr");
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
			OutputStreamWriter out = new OutputStreamWriter(uc.getOutputStream());
			out.write(str);
			out.close();

			BufferedReader in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			String res = in.readLine();
			in.close();
			
			PayPalIPN ipn = new PayPalIPN(request);
			ipn.verify(res); // makes sure that the message id from PayPal and is complete
			ipn.validate();  // makes sure that payment is correct and upgrades user account
			ofy.put(ipn);		
			
		} catch (Exception e) {
		}
	}
	
	String extendOffer() {
		return "<h2>Free Trial Offer: ChemVantage Premium Account Upgrade</h2>"
		+ "ChemVantage is pleased to make this one-time offer of a free account upgrade.<br>"
		+ "For the next 2 weeks you'll get the following convenience features:<OL>" + features + "</OL>"
		+ "There's no obligation to purchase a premium account upgrade after the free trial period "
		+ "and your satisfaction is 100% guaranteed.<p>"
		+ "<div style='text-align:center;color:red'>"
		+ "Your free trial offer will expire on " + new Date(new Date().getTime() + 1210000000L).toString() + "<p>"
		+ "<FORM ACTION=Upgrade METHOD=Get><input type=submit name=Action value='Accept Free Trial Offer'>"
		+ "&nbsp;<input type=submit name=Action value='No Thanks'></FORM></div>";
	}
	
	String freeTrialExpired(User user) {
		return "<h2>Your 2 Week Free Trial Premium Account Offer Has Expired</h2>"
		+ "Premium account users enjoy the following convenience features in ChemVantage:<OL>" + features + "</OL>"
		+ "To continue to use these benefits, you must purchase an account upgrade. There is<br>"
		+ "no obligation to buy, and your satisfaction is <span style='color:red'>100% guaranteed</span>.  If you are dissatisfied<br>"
		+ "with your account upgrade purchase for any reason, your money will be cheerfully refunded.<p>"
		+ "<TABLE>"
		+ "<TR><TD ALIGN=CENTER><b>Instant ChemVantage Premium Account Upgrade</b></TD></TR>"
		+ "<TR><TD ALIGN=CENTER><b>$20.00 USD</b></TD></TR><TR><TD ALIGN=CENTER> "
		+ "<form action=https://www.paypal.com/cgi-bin/webscr method=post>"
		+ "<input type=hidden name=cmd value=_s-xclick>"
		+ "<input type=hidden name=hosted_button_id value=" + (user.authDomain.equals("BLTI")?"U58TNLE8YE4AW":"HKW9475B55NJU") + ">"
		+ "<input type=hidden name=on0 value=userId><input type=hidden name=os0 value=" + user.id + ">"
		+ "<input type=image src=https://www.paypalobjects.com/en_US/i/btn/btn_buynowCC_LG.gif border=0 name=submit alt='PayPal - The safer, easier way to pay online!'>"
		+ "<br><font size=-2>Your payment will be processed by PayPal.com</font>"
		+ "<img alt='' border=0 src=https://www.paypalobjects.com/en_US/i/scr/pixel.gif width=1 height=1>"
		+ "</form>"
		+ "</TD></TR></TABLE>";
	}
	
	String thankYou() {
		return "<h2>Thank You</h2>"
		+ "Thanks for purchasing your premium account upgrade.<br>"
		+ "Within a few moments, you should see the premium options, including:<ol>" + features + "</ol>"
		+ "If you have any questions or difficulties using these features, please use the <a href=Feedback>Feedback Page</a> "
		+ "or send email directly to us at <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a>. "
		+ "If you are not completely satisfied, we will cheerfully refund your money.<p>"
		+ "<style type='text/css'>a.nav, a.nav:link, a.nav:visited {display:block; width:150px; height:35px; "
		+ "background:red; border:1px solid #000; margin-top:2px; text-align:center; text-decoration:none; "
		+ "font-family:verdana, arial, sans-serif; font-size:15px; color:white; line-height:35px; overflow:hidden;}"
		+ "a.nav:hover {color:#fff; background:#800;}</style><a class='nav' href='/Verification'>Continue</a>";
	}
}