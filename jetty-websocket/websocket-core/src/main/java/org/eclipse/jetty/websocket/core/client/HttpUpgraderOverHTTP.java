//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.websocket.core.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.HttpResponse;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.client.HttpUpgrader;
import org.eclipse.jetty.http.HttpFieldsBuilder;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.core.WebSocketConstants;
import org.eclipse.jetty.websocket.core.internal.WebSocketCore;

public class HttpUpgraderOverHTTP implements HttpUpgrader
{
    private final ClientUpgradeRequest clientUpgradeRequest;

    public HttpUpgraderOverHTTP(ClientUpgradeRequest clientUpgradeRequest)
    {
        this.clientUpgradeRequest = clientUpgradeRequest;
    }

    @Override
    public void prepare(HttpRequest request)
    {
        request.method(HttpMethod.GET).version(HttpVersion.HTTP_1_1);
        request.header(HttpHeader.SEC_WEBSOCKET_VERSION, WebSocketConstants.SPEC_VERSION_STRING);
        request.header(HttpHeader.UPGRADE, "websocket");
        request.header(HttpHeader.CONNECTION, "Upgrade");
        request.header(HttpHeader.SEC_WEBSOCKET_KEY, generateRandomKey());

        // Per the hybi list: Add no-cache headers to avoid compatibility issue.
        // There are some proxies that rewrite "Connection: upgrade" to
        // "Connection: close" in the response if a request doesn't contain
        // these headers.
        request.header(HttpHeader.PRAGMA, "no-cache");
        request.header(HttpHeader.CACHE_CONTROL, "no-cache");

        // Notify the UpgradeListeners now the headers are set.
        clientUpgradeRequest.requestComplete();
    }

    private String generateRandomKey()
    {
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        return new String(Base64.getEncoder().encode(bytes), StandardCharsets.US_ASCII);
    }

    @Override
    public void upgrade(HttpResponse response, EndPoint endPoint, Callback callback)
    {
        HttpRequest request = (HttpRequest)response.getRequest();
        HttpFieldsBuilder requestHeaders = request.getHeaders();
        if (requestHeaders.contains(HttpHeader.UPGRADE, "websocket"))
        {
            HttpFieldsBuilder responseHeaders = response.getHeaders();
            if (responseHeaders.contains(HttpHeader.CONNECTION, "upgrade"))
            {
                // Check the Accept hash
                String reqKey = requestHeaders.get(HttpHeader.SEC_WEBSOCKET_KEY);
                String expectedHash = WebSocketCore.hashKey(reqKey);
                String respHash = responseHeaders.get(HttpHeader.SEC_WEBSOCKET_ACCEPT);
                if (expectedHash.equalsIgnoreCase(respHash))
                {
                    clientUpgradeRequest.upgrade(response, endPoint);
                    callback.succeeded();
                }
                else
                    callback.failed(new HttpResponseException("Invalid Sec-WebSocket-Accept hash (was: " + respHash + " expected: " + expectedHash + ")", response));
            }
            else
            {
                callback.failed(new HttpResponseException("WebSocket upgrade missing 'Connection: Upgrade' header", response));
            }
        }
        else
        {
            callback.failed(new HttpResponseException("Not a WebSocket upgrade", response));
        }
    }
}
