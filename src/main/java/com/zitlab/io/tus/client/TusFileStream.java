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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

/**
 * TusFileStream input to encapsulate RandamAccessFile
 * @author ksvraja
 *
 */
public class TusFileStream {
	private RandomAccessFile raFile;
	private long size;
	private String fingerprint;
	private Map<String, String> metadata;

	//private long bytesRead;
	
	public TusFileStream(File file) throws FileNotFoundException, IOException {
		raFile = new RandomAccessFile(file, "r");
		size = raFile.length();
		fingerprint = String.format("%s-%d", file.getAbsolutePath(), size);
		metadata = new HashMap<String, String>();
	    metadata.put("filename", file.getName());
	}
	
	public void seekTo(long pos) throws IOException {
		raFile.seek(pos);
	}
	
	public long getSize() {
        return size;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public String getEncodedMetadata() {
        if(metadata == null || metadata.size() == 0) {
            return "";
        }

        String encoded = "";

        boolean firstElement = true;
        for(Map.Entry<String, String> entry : metadata.entrySet()) {
            if(!firstElement) {
                encoded += ",";
            }
            encoded += entry.getKey() + " " + base64Encode(entry.getValue().getBytes());

            firstElement = false;
        }

        return encoded;
    }
    
    
    static String base64Encode(byte[] in)       {
        StringBuilder out = new StringBuilder((in.length * 4) / 3);
        String codes = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";

        int b;
        for (int i = 0; i < in.length; i += 3)  {
            b = (in[i] & 0xFC) >> 2;
            out.append(codes.charAt(b));
            b = (in[i] & 0x03) << 4;
            if (i + 1 < in.length)      {
                b |= (in[i + 1] & 0xF0) >> 4;
                out.append(codes.charAt(b));
                b = (in[i + 1] & 0x0F) << 2;
                if (i + 2 < in.length)  {
                    b |= (in[i + 2] & 0xC0) >> 6;
                    out.append(codes.charAt(b));
                    b = in[i + 2] & 0x3F;
                    out.append(codes.charAt(b));
                } else  {
                    out.append(codes.charAt(b));
                    out.append('=');
                }
            } else      {
                out.append(codes.charAt(b));
                out.append("==");
            }
        }

        return out.toString();
    }

	public int read(byte[] buffer) throws IOException {
		return raFile.read(buffer);
	}

	public void close() throws IOException {
		raFile.close();
	}
}
