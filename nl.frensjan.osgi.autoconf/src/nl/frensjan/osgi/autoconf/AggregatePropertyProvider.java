/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package nl.frensjan.osgi.autoconf;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.framework.ServiceReference;

class AggregatePropertyProvider implements PropertyProvider {
	private static final String FOREACH_ARRAY = "array:";
	private static final String FOREACH_CONCAT = "concat:";
	private static final String FOREACH_COUNT = "count";

	private final Set<ServiceReference> refs;

	public AggregatePropertyProvider(Set<ServiceReference> refs) {
		this.refs = refs;
	}

	@Override
	public Object getProperty(String key) {
		if (key.startsWith(FOREACH_ARRAY)) {
			key = key.substring(FOREACH_ARRAY.length());
			String[] value = new String[this.refs.size()];

			int i = 0;
			for (ServiceReference ref : this.refs) {
				value[i++] = String.valueOf(ref.getProperty(key));
			}

			return value;
		} else if (key.startsWith(FOREACH_CONCAT)) {
			key = key.substring(FOREACH_CONCAT.length());

			Pattern pattern = Pattern.compile("([^:\\[%]+):([^:\\[%]*)\\[([^:\\[%]*)%([^:\\[%]*)\\]([^:\\[%]*)");
			Matcher matcher = pattern.matcher(key);
			if (matcher.matches()) {
				key = matcher.group(1);

				StringBuilder value = new StringBuilder(matcher.group(2));

				for (ServiceReference ref : this.refs) {
					value.append(matcher.group(3))
							.append(String.valueOf(ref.getProperty(key)))
							.append(matcher.group(4));
				}

				return value.append(matcher.group(5)).toString();
			} else {
				return key;
			}
		} else if (key.equals(FOREACH_COUNT)) {
			return this.refs.size();
		}

		return null;
	}

}
