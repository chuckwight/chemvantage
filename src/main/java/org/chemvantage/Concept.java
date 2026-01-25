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
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Concept implements Serializable {
	@Serial
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
				JsonObject api_request = new JsonObject();
				api_request.addProperty("model", Subject.getGPTModel());
				JsonObject prompt = new JsonObject();
				prompt.addProperty("id", "pmpt_68b1042b8a8481909fad9e65e35b00a7058a5345b5584798");
				JsonObject variables = new JsonObject();
				variables.addProperty("key_concept", this.title);
				prompt.add("variables", variables);
				api_request.add("prompt", prompt);

				URL u = new URI("https://api.openai.com/v1/responses").toURL();
				HttpURLConnection uc = (HttpURLConnection) u.openConnection();
				uc.setRequestMethod("POST");
				uc.setDoInput(true);
				uc.setDoOutput(true);
				uc.setRequestProperty("Authorization", "Bearer " + Subject.getOpenAIKey());
				uc.setRequestProperty("Content-Type", "application/json");
				uc.setRequestProperty("Accept", "application/json");
				OutputStream os = uc.getOutputStream();
				byte[] json_bytes = api_request.toString().getBytes("utf-8");
				os.write(json_bytes, 0, json_bytes.length);           
				os.close();

				int response_code = uc.getResponseCode();
				
				JsonObject api_response = null;
				BufferedReader reader = null;
				if (response_code/100==2) {
					reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
					api_response = JsonParser.parseReader(reader).getAsJsonObject();
					reader.close();
				} else {
					reader = new BufferedReader(new InputStreamReader(uc.getErrorStream()));
					reader.close();
				}

				// Find the output text buried in the response JSON:
				if (api_response == null) {
					return "Sorry, Sage is unable to provide a summary of this concepot at the moment.";
				}
				JsonArray output = api_response.get("output").getAsJsonArray();
				JsonObject message = null;
				JsonObject output_text = null;
				for (JsonElement element0 : output) {
					message = element0.getAsJsonObject();
					if (message.has("content")) {
						JsonArray content = message.get("content").getAsJsonArray();
						for (JsonElement element1 : content) {
							output_text = element1.getAsJsonObject();
							if (output_text.has("text")) {
								this.summary = output_text.get("text").getAsString();
								ofy().save().entity(this);
								break;
							}
						}
						break;
					}
				}
				return this.summary;
			} catch (Exception e) {
				return "Sorry, Sage is unable to provide a summary of this concepot at the moment.<p>"
						+ e.getMessage()==null?e.toString():e.getMessage();
			}
		} else return summary;
	}
}