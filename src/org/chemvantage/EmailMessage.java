/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2022 ChemVantage LLC
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

import java.util.Date;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class EmailMessage {
	@Id 	Long id;
	@Index	Date created;
	String	subjectLine;
			String text;
			Date lastRecipientCreated = new Date(0L); 	// Each message is sent to recipients in the order that the
													  	// recipients were created. This Date is used to  determine
														// the last recipient, so messages can be sent in batches 
														// with no duplicates.
			
	EmailMessage() {}
	
	EmailMessage(String subjectLine, String text) {
		this.created = new Date();
		this.subjectLine = subjectLine;
		this.text = text;
	}
	
	String getText() {
		return text;
	}
	
	String getSubjectLine() {
		return subjectLine;
	}
}
