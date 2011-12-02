/**
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.chemvantage.samples.apps.marketplace.openid.appengine;

import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.ResponseTooLargeException;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.openid4java.util.AbstractHttpFetcher;
import org.openid4java.util.HttpRequestOptions;
import org.openid4java.util.HttpResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletResponse;

public class Openid4javaFetcher extends AbstractHttpFetcher {

	private final URLFetchService fetchService;

	@Inject
	public Openid4javaFetcher(URLFetchService fetchService) {
		this.fetchService = fetchService;
	}

	@Override
	public HttpResponse get(String url, HttpRequestOptions requestOptions)
			throws IOException {
		return fetch(url, requestOptions, HTTPMethod.GET, null);
	}

	@Override
	public HttpResponse head(String url, HttpRequestOptions requestOptions)
			throws IOException {
		return fetch(url, requestOptions, HTTPMethod.HEAD, null);
	}

	@Override
	public HttpResponse post(String url, Map<String, String> parameters,
			HttpRequestOptions requestOptions) throws IOException {
		return fetch(url, requestOptions, HTTPMethod.POST,
				encodeParameters(parameters));
	}

	private String encodeParameters(Map<String, String> params) {
		Map<String, String> escapedParams = Maps.newHashMap();
		for (Entry<String, String> entry : params.entrySet()) {
			try {
				escapedParams.put(URLEncoder.encode(entry.getKey(), "UTF-8"),
						URLEncoder.encode(entry.getValue(), "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				// this should not happen
				throw new RuntimeException("platform does not support UTF-8", e);
			}
		}
		return Joiner.on("&").withKeyValueSeparator("=").join(escapedParams);
	}

	private HttpResponse fetch(String url, HttpRequestOptions requestOptions,
			HTTPMethod method, String content) throws IOException {

		final FetchOptions options = getFetchOptions(requestOptions);

		String currentUrl = url;

		for (int i = 0; i <= requestOptions.getMaxRedirects(); i++) {

			HTTPRequest httpRequest = new HTTPRequest(new URL(currentUrl),
					method, options);

			addHeaders(httpRequest, requestOptions);

			if (method == HTTPMethod.POST && content != null) {
				httpRequest.setPayload(content.getBytes());
			}

			HTTPResponse httpResponse;
			try {
				httpResponse = fetchService.fetch(httpRequest);
			} catch (ResponseTooLargeException e) {
				return new TooLargeResponse(currentUrl);
			}

			if (!isRedirect(httpResponse.getResponseCode())) {
				boolean isResponseTooLarge = (getContentLength(httpResponse) > requestOptions
						.getMaxBodySize());
				return new AppEngineFetchResponse(httpResponse,
						isResponseTooLarge, currentUrl);
			} else {
				currentUrl = getResponseHeader(httpResponse, "Location")
						.getValue();
			}
		}
		throw new IOException("exceeded maximum number of redirects");
	}

	private static int getContentLength(HTTPResponse httpResponse) {
		byte[] content = httpResponse.getContent();
		if (content == null) {
			return 0;
		} else {
			return content.length;
		}
	}

	private static void addHeaders(HTTPRequest httpRequest,
			HttpRequestOptions requestOptions) {

		String contentType = requestOptions.getContentType();

		if (contentType != null) {
			httpRequest.addHeader(new HTTPHeader("Content-Type", contentType));
		}

		Map<String, String> headers = getRequestHeaders(requestOptions);

		if (headers != null) {
			for (Entry<String, String> header : headers.entrySet()) {
				httpRequest.addHeader(new HTTPHeader(header.getKey(), header
						.getValue()));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String> getRequestHeaders(
			HttpRequestOptions requestOptions) {
		return requestOptions.getRequestHeaders();
	}

	private static Header getResponseHeader(HTTPResponse httpResponse,
			String headerName) {
		Header[] allHeaders = getResponseHeaders(httpResponse, headerName);
		if (allHeaders.length == 0) {
			return null;
		} else {
			return allHeaders[0];
		}
	}

	private static Header[] getResponseHeaders(HTTPResponse httpResponse,
			String headerName) {
		List<HTTPHeader> allHeaders = httpResponse.getHeaders();
		List<Header> matchingHeaders = new ArrayList<Header>();
		for (HTTPHeader header : allHeaders) {
			if (header.getName().equalsIgnoreCase(headerName)) {
				matchingHeaders.add(new BasicHeader(header.getName(), header
						.getValue()));
			}
		}
		return matchingHeaders.toArray(new Header[matchingHeaders.size()]);
	}

	private static boolean isRedirect(int responseCode) {
		switch (responseCode) {
		case HttpServletResponse.SC_MOVED_PERMANENTLY:
		case HttpServletResponse.SC_MOVED_TEMPORARILY:
		case HttpServletResponse.SC_SEE_OTHER:
		case HttpServletResponse.SC_TEMPORARY_REDIRECT:
			return true;
		default:
			return false;
		}
	}

	private FetchOptions getFetchOptions(HttpRequestOptions requestOptions) {
		return FetchOptions.Builder.disallowTruncate().doNotFollowRedirects()
				.setDeadline(requestOptions.getConnTimeout() / 1000.0);
	}

	private static class AppEngineFetchResponse implements HttpResponse {

		private final HTTPResponse httpResponse;
		private final boolean bodySizeExceeded;
		private String finalUri;

		public AppEngineFetchResponse(HTTPResponse httpResponse,
				boolean bodySizeExceeded, String finalUri) {
			this.httpResponse = httpResponse;
			this.bodySizeExceeded = bodySizeExceeded;
			this.finalUri = finalUri;
		}

		public String getBody() {
			byte[] content = httpResponse.getContent();
			return (content == null || content.length == 0) ? null
					: new String(content);
		}

		public String getFinalUri() {
			return finalUri;
		}

		public Header getResponseHeader(String headerName) {
			return Openid4javaFetcher.getResponseHeader(httpResponse,
					headerName);
		}

		public Header[] getResponseHeaders(String headerName) {
			return Openid4javaFetcher.getResponseHeaders(httpResponse,
					headerName);
		}

		public boolean isBodySizeExceeded() {
			return bodySizeExceeded;
		}

		public int getStatusCode() {
			return httpResponse.getResponseCode();
		}
	}

	private static class TooLargeResponse implements HttpResponse {

		private String finalUri;

		public TooLargeResponse(String finalUri) {
			this.finalUri = finalUri;
		}

		public String getBody() {
			throw new ResponseTooLargeException(finalUri);
		}

		public String getFinalUri() {
			return finalUri;
		}

		public Header getResponseHeader(String headerName) {
			throw new ResponseTooLargeException(finalUri);
		}

		public Header[] getResponseHeaders(String headerName) {
			throw new ResponseTooLargeException(finalUri);
		}

		public boolean isBodySizeExceeded() {
			return true;
		}

		public int getStatusCode() {
			throw new ResponseTooLargeException(finalUri);
		}
	}
}
