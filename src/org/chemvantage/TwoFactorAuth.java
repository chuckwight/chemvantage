/*  ChemVantage - A Java web application for online learning
 *   Copyright (C) 2016 ChemVantage LLC
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

import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class TwoFactorAuth {

	public static boolean sentSMSCode (User user, int code) {
		try {
			Long.parseLong(user.smsMessageDevice.substring(0,10)); // throws exception for invalid SMS device			
		} catch (Exception e) {  // bad cell number; unenroll user
			user.smsMessageDevice = "";
			user.use2FactorAuth = false;
			ofy().save().entity(user).now();
			return false;
		}
		try {
			Message msg = new MimeMessage(Session.getDefaultInstance(new Properties(), null));
			msg.setFrom(new InternetAddress("admin@chemvantage.org","ChemVantage"));
			msg.setSubject("ChemVantage Login");
			msg.setRecipient(Message.RecipientType.TO,new InternetAddress(user.smsMessageDevice));
			msg.setText("Your verification code is " + code);
			Transport.send(msg);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	public static String verificationForm(String returnURL) {
		StringBuffer buf = new StringBuffer("<h2>ChemVantage Two-Factor Authentication</h2>");		
		buf.append("<form method=post>");
		buf.append("A text message was sent to your phone. Please enter your 6-digit verification code here:&nbsp;");
		buf.append("<input type=text name=Code>");
		buf.append("<input type=submit value=Verify><input type=hidden name=ReturnURL value='" + returnURL + "'></form>");		
		return buf.toString();
	}

}
