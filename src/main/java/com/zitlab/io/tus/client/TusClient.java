/** 
 * (C) Copyright 2018 ZitLab (ksvraja@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.zitlab.io.tus.client;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;


public class TusClient {
	
	public final static String TUS_VERSION = "1.0.0";
	
	private URL baseURL;
	private boolean resumingEnabled;
    private TusURLStore urlStore;
    private Map<String, String> headers;
    private int connectTimeout = 5000;
    
    
    
	public URL getBaseURL() {
		return baseURL;
	}
	public void setBaseURL(URL baseURL) {
		this.baseURL = baseURL;
	}
	public boolean isResumingEnabled() {
		return resumingEnabled;
	}
	public void setResumingEnabled(boolean resumingEnabled) {
		this.resumingEnabled = resumingEnabled;
		if(!this.resumingEnabled) this.urlStore = null;
	}
	public TusURLStore getUrlStore() {
		return urlStore;
	}
	public void setUrlStore(TusURLStore urlStore) {
		this.urlStore = urlStore;
	}
	public int getConnectTimeout() {
		return connectTimeout;
	}
	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}
	public Map<String, String> getHeaders() {
		return headers;
	}
	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}
    
	 public TusUploader createUpload(TusFileStream upload) throws TusProtocolException, IOException {
	        HttpURLConnection connection = (HttpURLConnection) baseURL.openConnection();
	        connection.setRequestMethod("POST");
	        prepareConnection(connection);

	        String encodedMetadata = upload.getEncodedMetadata();
	        if(encodedMetadata.length() > 0) {
	            connection.setRequestProperty("Upload-Metadata", encodedMetadata);
	        }

	        connection.addRequestProperty("Upload-Length", Long.toString(upload.getSize()));
	        connection.connect();

	        int responseCode = connection.getResponseCode();
	        if(!(responseCode >= 200 && responseCode < 300)) {
	            throw new TusProtocolException("unexpected status code (" + responseCode + ") while creating upload", connection);
	        }

	        String urlStr = connection.getHeaderField("Location");
	        if(urlStr == null || urlStr.length() == 0) {
	            throw new TusProtocolException("missing upload URL in response for creating upload", connection);
	        }

	        // The upload URL must be relative to the URL of the request by which is was returned,
	        // not the upload creation URL. In most cases, there is no difference between those two
	        // but there may be cases in which the POST request is redirected.
	        URL uploadURL = new URL(connection.getURL(), urlStr);

	        if(resumingEnabled) {
	            urlStore.put(upload.getFingerprint(), urlStr);
	        }

	        return new TusUploader(this, uploadURL, upload, 0);
	    }

	    /**
	     * Try to resume an already started upload. Before call this function, resuming must be
	     * enabled using {@link #enableResuming(TusURLStore)}. This method will look up the URL for this
	     * upload in the {@link TusURLStore} using the upload's fingerprint (see
	     * {@link TusUpload#getFingerprint()}). After a successful lookup a HEAD request will be issued
	     * to find the current offset without uploading the file, yet.
	     *
	     * @param upload The file for which an upload will be resumed
	     * @return Use {@link TusUploader} to upload the remaining file's chunks.
	     * @throws FingerprintNotFoundException Thrown if no matching fingerprint has been found in
	     * {@link TusURLStore}. Use {@link #createUpload(TusUpload)} to create a new upload.
	     * @throws ResumingNotEnabledException Throw if resuming has not been enabled using {@link
	     * #enableResuming(TusURLStore)}.
	     * @throws TusProtocolException Thrown if the remote server sent an unexpected response, e.g.
	     * wrong status codes or missing/invalid headers.
	     * @throws IOException Thrown if an exception occurs while issuing the HTTP request.
	     */
	    public TusUploader resumeUpload(TusFileStream upload) throws  TusProtocolException, IOException, ResumingNotEnabledException, FingerprintNotFoundException {
	        if(!resumingEnabled) {
	            throw new ResumingNotEnabledException();
	        }

	        String uploadURL = urlStore.get(upload.getFingerprint());
	        if(uploadURL == null) {
	            throw new FingerprintNotFoundException("FingerPrint " + upload.getFingerprint() + " found");
	        }

	        URL url = new URL(baseURL, uploadURL);
	        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	        connection.setRequestMethod("HEAD");
	        prepareConnection(connection);

	        connection.connect();

	        int responseCode = connection.getResponseCode();
	        if(!(responseCode >= 200 && responseCode < 300)) {
	            throw new TusProtocolException("unexpected status code (" + responseCode + ") while resuming upload", connection);
	        }

	        String offsetStr = connection.getHeaderField("Upload-Offset");
	        if(offsetStr == null || offsetStr.length() == 0) {
	            throw new TusProtocolException("missing upload offset in response for resuming upload", connection);
	        }
	        long offset = Long.parseLong(offsetStr);

	        return new TusUploader(this, url, upload, offset);
	    }

	    /**
	     * Try to resume an upload using {@link #resumeUpload(TusUpload)}. If the method call throws
	     * an {@link ResumingNotEnabledException} or {@link FingerprintNotFoundException}, a new upload
	     * will be created using {@link #createUpload(TusUpload)}.
	     *
	     * @param upload The file for which an upload will be resumed
	     * @throws TusProtocolException Thrown if the remote server sent an unexpected response, e.g.
	     * wrong status codes or missing/invalid headers.
	     * @throws IOException Thrown if an exception occurs while issuing the HTTP request.
	     */
	    public TusUploader resumeOrCreateUpload(TusFileStream upload) throws TusProtocolException, IOException {
	        try {
	            return resumeUpload(upload);
	        } catch(FingerprintNotFoundException e) {
	            return createUpload(upload);
	        } catch(ResumingNotEnabledException e) {
	            return createUpload(upload);
	        } catch(TusProtocolException e) {
	            // If the attempt to resume returned a 404 Not Found, we immediately try to create a new
	            // one since TusExectuor would not retry this operation.
	            HttpURLConnection connection = e.getCausingConnection();
	            if(connection != null && connection.getResponseCode() == 404) {
	                return createUpload(upload);
	            }

	            throw e;
	        }
	    }

	    /**
	     * Set headers used for every HTTP request. Currently, this will add the Tus-Resumable header
	     * and any custom header which can be configured using {@link #setHeaders(Map)},
	     *
	     * @param connection The connection whose headers will be modified.
	     */
	    public void prepareConnection(HttpURLConnection connection) {
	        // Only follow redirects, if the POST methods is preserved. If http.strictPostRedirect is
	        // disabled, a POST request will be transformed into a GET request which is not wanted by us.
	        // See: http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/7u40-b43/sun/net/www/protocol/http/HttpURLConnection.java#2372
	        connection.setInstanceFollowRedirects(Boolean.getBoolean("http.strictPostRedirect"));

	        connection.setConnectTimeout(connectTimeout);
	        connection.addRequestProperty("Tus-Resumable", TUS_VERSION);

	        if(headers != null) {
	            for (Map.Entry<String, String> entry : headers.entrySet()) {
	                connection.addRequestProperty(entry.getKey(), entry.getValue());
	            }
	        }
	    }
}
