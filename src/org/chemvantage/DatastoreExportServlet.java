/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.chemvantage;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;

import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.common.io.CharStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@WebServlet(name = "DatastoreExportServlet", value = "/cloud-datastore-export")
@ServletSecurity(@HttpConstraint(rolesAllowed = {"admin"}))
public class DatastoreExportServlet extends HttpServlet {
	private static final long serialVersionUID = 137L;

	private static final String PROJECT_ID = System.getenv("GOOGLE_CLOUD_PROJECT");

	private static final Logger log = Logger.getLogger(DatastoreExportServlet.class.getName());

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

		// Validate outputURL parameter
		String outputUrlPrefix = request.getParameter("output_url_prefix");

		if (outputUrlPrefix == null || !outputUrlPrefix.matches("^gs://.*")) {
			// Send error response if outputURL not set or not a Cloud Storage bucket
			response.setStatus(HttpServletResponse.SC_CONFLICT);
			response.setContentType("text/plain");
			response.getWriter().println("Error: Must provide a valid output_url_prefix.");

		} else {

			// Put together export request headers
			URL url = new URL("https://datastore.googleapis.com/v1/projects/" + PROJECT_ID + ":export");
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setDoOutput(true);
			connection.setRequestMethod("POST");
			connection.addRequestProperty("Content-Type", "application/json");

			// Get an access token to authorize export request
			ArrayList<String> scopes = new ArrayList<String>();
			scopes.add("https://www.googleapis.com/auth/datastore");
			final AppIdentityService appIdentity = AppIdentityServiceFactory.getAppIdentityService();
			final AppIdentityService.GetAccessTokenResult accessToken =
					AppIdentityServiceFactory.getAppIdentityService().getAccessToken(scopes);
			connection.addRequestProperty("Authorization", "Bearer " + accessToken.getAccessToken());

			// Build export request payload based on URL parameters
			// Required: output_url_prefix
			// Optional: entity filter
			JsonObject exportRequest = new JsonObject();

			// If output prefix ends with a slash, use as-is
			// Otherwise, add a timestamp to form unique output url
			if (!outputUrlPrefix.endsWith("/")) {
				String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
				outputUrlPrefix = outputUrlPrefix + "/" + timeStamp + "/";
			}

			// Add outputUrl to payload
			exportRequest.addProperty("output_url_prefix", outputUrlPrefix);

			// Build optional entity filter to export subset of
			// kinds or namespaces
			JsonObject entityFilter = new JsonObject();

			// Read kind parameters and add to export request if not null
			String[] kinds = request.getParameterValues("kind");
			if (kinds != null) {
				JsonArray kindsJson = new JsonArray();
				for (String k : kinds) kindsJson.add(k);
				entityFilter.add("kinds", kindsJson);
			}

			// Read namespace parameters and add to export request if not null
			String[] namespaces = request.getParameterValues("namespace_id");
			if (namespaces != null) {
				JsonArray namespacesJson = new JsonArray();
				for (String n : namespaces) namespacesJson.add(n);
				entityFilter.add("namespaceIds", namespacesJson);
			}

			// Add entity filter to payload
			// Finish export request payload
			exportRequest.add("entityFilter", entityFilter);

			// Send export request
			OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
			writer.write(exportRequest.toString());
			writer.close();

			// Examine server's response
			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				// Request failed, log errors and return
				InputStream s = connection.getErrorStream();
				InputStreamReader r = new InputStreamReader(s, StandardCharsets.UTF_8);
				String errorMessage =
						String.format(
								"got error (%d) response %s from %s",
								connection.getResponseCode(), CharStreams.toString(r), connection.toString());
				log.warning(errorMessage);
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				response.setContentType("text/plain");
				response.getWriter().println(
						"Failed to initiate export.");
				return;   
			}

			// Success, print export operation information
			InputStreamReader r = new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8);
			JsonObject exportResponse = JsonParser.parseReader(r).getAsJsonObject();
			
			response.setContentType("text/plain");
			response.getWriter().println(
					"Export started:\n" + exportResponse.toString());    
		}
	}
}