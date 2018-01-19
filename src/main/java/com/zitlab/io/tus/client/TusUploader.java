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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;


public class TusUploader {
	private URL uploadURL;
    private TusFileStream input;
    private long offset;
    private TusClient client;
    private byte[] buffer;
    private int requestPayloadSize = 1024 * 1024 * 1024;
    private int bytesRemainingForRequest;

    private HttpURLConnection connection;
    private OutputStream output;

    /**
     * Begin a new upload request by opening a PATCH request to specified upload URL. After this
     * method returns a connection will be ready and you can upload chunks of the file.
     *
     * @param client Used for preparing a request ({@link TusClient#prepareConnection(HttpURLConnection)}
     * @param uploadURL URL to send the request to
     * @param input Stream to read (and seek) from and upload to the remote server
     * @param offset Offset to read from
     * @throws IOException Thrown if an exception occurs while issuing the HTTP request.
     */
    public TusUploader(TusClient client, URL uploadURL, TusFileStream input, long offset) throws IOException {
        this.uploadURL = uploadURL;
        this.input = input;
        this.offset = offset;
        this.client = client;

        input.seekTo(offset);

        setChunkSize(2 * 1024 * 1024);
    }

    private void openConnection() throws TusProtocolException, IOException {
        // Only open a connection, if we have none open.
        if(connection != null) {
            return;
        }

        bytesRemainingForRequest = requestPayloadSize;
      //  input.mark(requestPayloadSize);

        connection = (HttpURLConnection) uploadURL.openConnection();
        client.prepareConnection(connection);
        connection.setRequestProperty("Upload-Offset", Long.toString(offset));
        connection.setRequestProperty("Content-Type", "application/offset+octet-stream");
        connection.setRequestProperty("Expect", "100-continue");
        try {
            connection.setRequestMethod("PATCH");
            // Check whether we are running on a buggy JRE
        } catch (java.net.ProtocolException pe) {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        }

        connection.setDoOutput(true);
        connection.setChunkedStreamingMode(0);
        try {
            output = connection.getOutputStream();
        } catch(java.net.ProtocolException pe) {
            // If we already have a response code available, our expectation using the "Expect: 100-
            // continue" header failed and we should handle this response.
            if(connection.getResponseCode() != -1) {
                finish();
            }

            throw pe;
        }
    }

    /**
     * Sets the used chunk size. This number is used by {@link #uploadChunk()} to indicate how
     * much data is uploaded in a single take. When choosing a value for this parameter you need to
     * consider that uploadChunk() will only return once the specified number of bytes has been
     * sent. For slow internet connections this may take a long time. In addition, a buffer with
     * the chunk size is allocated and kept in memory.
     *
     * @param size The new chunk size
     */
    public void setChunkSize(int size) {
        buffer = new byte[size];
    }

    /**
     * Returns the current chunk size set using {@link #setChunkSize(int)}.
     *
     * @return Current chunk size
     */
    public int getChunkSize() {
        return buffer.length;
    }

    /**
     * Set the maximum payload size for a single request counted in bytes. This is useful for splitting
     * bigger uploads into multiple requests. For example, if you have a resource of 2MB and
     * the payload size set to 1MB, the upload will be transferred by two requests of 1MB each.
     *
     * The default value for this setting is 1024 * 1024 * 1024 bytes (GiB).
     *
     * Be aware that setting a low maximum payload size (in the megabytes or even less range) will result in decreased
     * performance since more requests need to be used for an upload. Each request will come with its overhead in terms
     * of longer upload times. Furthermore, changing this setting is rarely necessary and is only advised in a situation
     * when a server cannot deal with streaming request bodies (e.g. some Python frameworks).
     *
     * This method must not be called when the uploader has currently an open connection to the
     * remote server. In general, try to set the payload size before invoking {@link #uploadChunk()}
     * the first time.
     *
     * @see {@link #getRequestPayloadSize()}
     *
     * @param size Number of bytes for a single payload
     * @throws IllegalStateException Thrown if the uploader currently has a connection open
     */
    public void setRequestPayloadSize(int size) throws IllegalStateException {
        if(connection != null) {
            throw new IllegalStateException("payload size for a single request must not be " +
                    "modified as long as a request is in progress");
        }

        requestPayloadSize = size;
    }

    /**
     * Get the current maximum payload size for a single request.
     *
     * @see {@link #setChunkSize(int)}
     *
     * @return Number of bytes for a single payload
     */
    public int getRequestPayloadSize() {
        return requestPayloadSize;
    }

    /**
     * Upload a part of the file by reading a chunk from the InputStream and writing
     * it to the HTTP request's body. If the number of available bytes is lower than the chunk's
     * size, all available bytes will be uploaded and nothing more.
     * No new connection will be established when calling this method, instead the connection opened
     * in the constructor will be used.
     * The size of the read chunk can be obtained using {@link #getChunkSize()} and changed
     * using {@link #setChunkSize(int)}.
     * In order to obtain the new offset, use {@link #getOffset()} after this method returns.
     *
     * @return Number of bytes read and written.
     * @throws IOException  Thrown if an exception occurs while reading from the source or writing
     *                      to the HTTP request.
     */
    public int uploadChunk() throws TusProtocolException,  IOException {
        openConnection();

//        int bytesToRead = Math.min(getChunkSize(), bytesRemainingForRequest);

        int bytesRead = input.read(buffer);
        if(bytesRead == -1) {
            // No bytes were read since the input stream is empty
            return -1;
        }

        // Do not write the entire buffer to the stream since the array will
        // be filled up with 0x00s if the number of read bytes is lower then
        // the chunk's size.
        output.write(buffer, 0, bytesRead);
        output.flush();

        offset += bytesRead;
        bytesRemainingForRequest -= bytesRead;

        if(bytesRemainingForRequest <= 0) {
            finishConnection();
        }

        return bytesRead;
    }

    /**
     * Get the current offset for the upload. This is the number of all bytes uploaded in total and
     * in all requests (not only this one). You can use it in conjunction with
     * {@link TusUpload#getSize()} to calculate the progress.
     *
     * @return The upload's current offset.
     */
    public long getOffset() {
        return offset;
    }

    public URL getUploadURL() {
        return uploadURL;
    }

    /**
     * Finish the request by closing the HTTP connection and the InputStream.
     * You can call this method even before the entire file has been uploaded. Use this behavior to
     * enable pausing uploads.
     *
     * @throws TusProtocolException Thrown if the server sends an unexpected status
     * code
     * @throws IOException  Thrown if an exception occurs while cleaning up.
     */
    public void finish() throws TusProtocolException,  IOException {
        finishConnection();
        input.close();
    }

    private void finishConnection() throws TusProtocolException, IOException {
        if(output != null) output.close();

        if(connection != null) {
            int responseCode = connection.getResponseCode();
            connection.disconnect();

            if (!(responseCode >= 200 && responseCode < 300)) {
                throw new TusProtocolException("unexpected status code (" + responseCode + ") while uploading chunk", connection);
            }

            // TODO detect changes and seek accordingly
            long serverOffset = getHeaderFieldLong(connection, "Upload-Offset");
            if(serverOffset == -1) {
                throw new TusProtocolException("response to PATCH request contains no or invalid Upload-Offset header", connection);
            }
            if(offset != serverOffset) {
                throw new TusProtocolException(
                        String.format("response contains different Upload-Offset value (%d) than expected (%d)",
                                serverOffset,
                                offset),
                        connection);
            }

            connection = null;
        }
    }

    private long getHeaderFieldLong(URLConnection connection, String field) {
        String value = connection.getHeaderField(field);
        if(value == null) {
            return -1;
        }

        try {
            return Long.parseLong(value);
        } catch(NumberFormatException e) {
            return -1;
        }
    }
}
