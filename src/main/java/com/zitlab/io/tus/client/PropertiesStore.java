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

import java.net.URL;
import java.util.Properties;

public class PropertiesStore implements TusURLStore {

	private Properties props = new Properties();
	private static final PropertiesStore store = new PropertiesStore();
	
	private PropertiesStore() {
	}
	
	public static PropertiesStore getStore () {
		return store;
	}
	
	/* (non-Javadoc)
	 * @see com.zitlab.tus.client.TusURLStore#set(java.lang.String, java.lang.String)
	 */
	@Override
	public void put(String fingerprint, String url) {

		System.out.println("Fingerprint:" + fingerprint);
		System.out.println("url:" + url);
		props.setProperty(fingerprint, url);
	}

	/* (non-Javadoc)
	 * @see com.zitlab.tus.client.TusURLStore#get(java.lang.String)
	 */
	@Override
	public String get(String fingerprint) {
		if(null == fingerprint)
			return null;
		System.out.println(fingerprint);
		return props.getProperty(fingerprint);
	}

	/* (non-Javadoc)
	 * @see com.zitlab.tus.client.TusURLStore#remove(java.lang.String)
	 */
	@Override
	public void remove(String fingerprint) {
		props.remove(fingerprint);
	}

}
