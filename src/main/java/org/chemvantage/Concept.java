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

import static com.googlecode.objectify.ObjectifyService.ofy;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
/*
 * This entity represents a key concept, which is roughly equivalent to a section of a textbook chapter.
 * Typically, we can expect 4-8 Concepts per Topic.
 * Topics have an optional field consisting of a List of conceptId values
 * Questions have an optional field of one conceptId value, so they can be filtered in a query.
 */
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Concept implements Serializable {
	private static final long serialVersionUID = 137L;
	@Id 	Long id;
	@Index 	String orderBy;
	 		String title;
	 		String summary;
	
	Concept() {}

	Concept(String title,String orderBy) {
		this.title = title;
		this.orderBy = orderBy;
	}
	
	String getSummary() {
		if (summary == null) {
			try {
				JsonObject api_request = new JsonObject();  // these are used to score essay questions using ChatGPT
				api_request.addProperty("model","gpt-4");
				//api_request.addProperty("model","gpt-3.5-turbo");
				api_request.addProperty("max_tokens",200);
				api_request.addProperty("temperature",0.2);

				JsonArray messages = new JsonArray();
				JsonObject m1 = new JsonObject();
				m1.addProperty("role", "system");
				m1.addProperty("content", "You are a tutor for a student in a General Chemistry class.");
				messages.add(m1);
				JsonObject m2 = new JsonObject();  // api request message
				m2.addProperty("role", "user");
				m2.addProperty("content", "Write a brief summary to teach me the key concept: " + this.title);
				messages.add(m2);

				api_request.add("messages", messages);
				URL u = new URL("https://api.openai.com/v1/chat/completions");
				HttpURLConnection uc = (HttpURLConnection) u.openConnection();
				uc.setRequestMethod("POST");
				uc.setDoInput(true);
				uc.setDoOutput(true);
				uc.setRequestProperty("Authorization", "Bearer " + Subject.getOpenAIKey());
				uc.setRequestProperty("Content-Type", "application/json");
				uc.setRequestProperty("Accept", "text/html");
				OutputStream os = uc.getOutputStream();
				byte[] json_bytes = api_request.toString().getBytes("utf-8");
				os.write(json_bytes, 0, json_bytes.length);           
				os.close();

				BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				reader.close();
				
				String content = json.get("choices").getAsJsonArray().get(0).getAsJsonObject().get("message").getAsJsonObject().get("content").getAsString();
				content.replaceAll("\\n\\n", "<p>");
				
				// save this summary in the datastore
				ofy().save().entity(this);
				
				return content;
			} catch (Exception e) {
				return "Sorry, Sage is unable to provide a summary of this concepot at the moment.";
			}
		} else return summary;
	}
}