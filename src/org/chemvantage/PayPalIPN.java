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

import javax.persistence.Id;
import javax.servlet.http.HttpServletRequest;

import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.annotation.Unindexed;

@Unindexed
public class PayPalIPN {    // this object represents a PayPal Instant Payment Notification transaction
	@Id String txn_id;
	String userId;
	String receiver_email;
	String txn_type;
	String payer_email;
	String payer_id;
	String payer_status;
	String first_name;
	String last_name;
	String address_name;
	String address_street;
	String address_city;
	String address_state;
	String address_zip;
	String address_country;
	String address_status;
	String item_name;
	String item_number;
	String mc_currency;
	String mc_gross;
	String mc_fee;
	String payment_date;
	String payment_status;
	String payment_type;
	String response;
	boolean verified;
	boolean validated;
	
	PayPalIPN() {}
	
	PayPalIPN (HttpServletRequest request) {
		this.txn_id = request.getParameter("txn_id");
		this.userId = request.getParameter("option_selection1");
		this.receiver_email = request.getParameter("receiver_email");
		this.txn_type = request.getParameter("txn_type");
		this.payer_email = request.getParameter("payer_email");
		this.payer_id = request.getParameter("payer_id");
		this.payer_status = request.getParameter("payer_status");
		this.first_name = request.getParameter("first_name");
		this.address_name = request.getParameter("address_name");
		this.address_street = request.getParameter("address_street");
		this.address_city = request.getParameter("address_city");
		this.address_state = request.getParameter("address_state");
		this.address_zip = request.getParameter("address_zip");
		this.address_country = request.getParameter("address_country");
		this.address_status = request.getParameter("address_status");
		this.item_name = request.getParameter("item_name");
		this.item_number = request.getParameter("item_number");
		this.mc_currency = request.getParameter("mc_currency");
		this.mc_gross = request.getParameter("mc_gross");
		this.mc_fee = request.getParameter("mc_fee");
		this.payment_date = request.getParameter("payment_date");
		this.payment_status = request.getParameter("payment_status");
		this.payment_type = request.getParameter("payment_type");
		this.response = null;
	}
	
	void verify(String message) {
		// verified means that the message came from PayPal and is complete
		this.response = message;
		this.verified = message.equals("VERIFIED")?true:false;
	}
	
	void validate() {
		// validated means that the payment information contained in the message 
		// is correct. This method upgrades the user's account
		boolean valid = this.verified;  // IPN message was OK
		try {
			if (!"admin@chemvantage.org".equals(receiver_email)) valid=false;
			if (!"20.00".equals(mc_gross)) valid=false;
			if (!"Completed".equals(payment_status)) valid=false;
			Objectify ofy = ObjectifyService.begin();
			User u = ofy.get(User.class,userId);
			if (u.hasPremiumAccount()) valid = false; // duplicate payment
			else u.setPremium(valid); // upgrade the user to premium status if everything is OK
			ofy.put(u);
		} catch (Exception e) {
			valid = false; // error or no user found with this id
		}
		this.validated = valid;
	}
}