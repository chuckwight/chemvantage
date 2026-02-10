package org.chemvantage;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

public final class SpaRequest {
	private SpaRequest() {}

	static boolean isJsonRequest(HttpServletRequest request) {
		String contentType = request.getContentType();
		String accept = request.getHeader("Accept");
		return (contentType != null && contentType.contains("application/json"))
				|| (accept != null && accept.contains("application/json"));
	}

	static JsonObject readJsonBody(HttpServletRequest request) throws IOException {
		try (Reader reader = request.getReader()) {
			JsonElement element = JsonParser.parseReader(reader);
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
		} catch (Exception e) {
			return null;
		}
	}

	static HttpServletRequest wrap(HttpServletRequest request, JsonObject body) {
		if (body == null) return request;
		return new JsonRequestWrapper(request, toParamMap(body));
	}

	static String getBearerToken(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		if (header == null) return null;
		String prefix = "Bearer ";
		return header.startsWith(prefix) ? header.substring(prefix.length()).trim() : null;
	}

	static String getJsonString(JsonObject body, String key) {
		if (body == null || key == null || !body.has(key)) return null;
		JsonElement element = body.get(key);
		return element != null && !element.isJsonNull() ? element.getAsString() : null;
	}

	private static Map<String, String[]> toParamMap(JsonObject body) {
		Map<String, String[]> params = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : body.entrySet()) {
			String key = entry.getKey();
			JsonElement value = entry.getValue();
			if (value == null || value.isJsonNull()) continue;
			if (value.isJsonArray()) {
				JsonArray array = value.getAsJsonArray();
				String[] items = new String[array.size()];
				for (int i = 0; i < array.size(); i++) {
					items[i] = array.get(i).isJsonNull() ? null : array.get(i).getAsString();
				}
				params.put(key, items);
			} else {
				params.put(key, new String[] { value.getAsString() });
			}
		}
		return params;
	}

	private static class JsonRequestWrapper extends HttpServletRequestWrapper {
		private final Map<String, String[]> params;

		JsonRequestWrapper(HttpServletRequest request, Map<String, String[]> params) {
			super(request);
			this.params = params;
		}

		@Override
		public String getParameter(String name) {
			String[] values = params.get(name);
			return values != null && values.length > 0 ? values[0] : super.getParameter(name);
		}

		@Override
		public Map<String, String[]> getParameterMap() {
			Map<String, String[]> merged = new LinkedHashMap<>();
			merged.putAll(super.getParameterMap());
			merged.putAll(params);
			return merged;
		}

		@Override
		public Enumeration<String> getParameterNames() {
			return Collections.enumeration(getParameterMap().keySet());
		}

		@Override
		public String[] getParameterValues(String name) {
			String[] values = params.get(name);
			return values != null ? values : super.getParameterValues(name);
		}
	}
}
