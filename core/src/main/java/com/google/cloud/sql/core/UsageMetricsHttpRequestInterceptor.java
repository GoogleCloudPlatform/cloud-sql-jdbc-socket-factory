/*
 * Copyright 2018 Google Inc. All Rights Reserved.
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

package com.google.cloud.sql.core;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;

import java.io.IOException;

public class UsageMetricsHttpRequestInterceptor implements HttpRequestInitializer {

	/**
	 * Credentials to authenticate and authorize the call.
	 */
	private Credential credential;

	/**
	 * Token added to the beginning of the User-Agent string. For example,
	 * {@code spring-cloud-gcp-sql-mysql/1.0.0}.
	 */
	private String userToken;

	public UsageMetricsHttpRequestInterceptor(Credential credential,
											  String userToken) {
		this.credential = credential;
		this.userToken = userToken;
	}

	@Override
	public void initialize(HttpRequest request) throws IOException {
		HttpHeaders headers = request.getHeaders();
		String userAgent = headers.getUserAgent();
		userAgent = userAgent != null ? userToken + userAgent : userToken;
		headers.setUserAgent(userAgent);
		request.setHeaders(headers);

		request.setInterceptor(credential);
		request.setUnsuccessfulResponseHandler(credential);
	}
}
