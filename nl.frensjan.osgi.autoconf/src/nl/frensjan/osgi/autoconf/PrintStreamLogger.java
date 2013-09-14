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

import java.io.PrintStream;

import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

final class PrintStreamLogger implements LogService {
	private final PrintStream out;

	public PrintStreamLogger(PrintStream out) {
		this.out = out;
	}

	@Override
	public void log(ServiceReference sr, int level, String message, Throwable exception) {
		switch (level) {
		case LOG_DEBUG:
			this.out.print("DEBUG");
			break;
		case LOG_INFO:
			this.out.print("INFO");
			break;
		case LOG_WARNING:
			this.out.print("WARNING");
			break;
		case LOG_ERROR:
			this.out.print("ERROR");
			break;
		}

		this.out.print(" - ");
		this.out.print(message);

		if (sr != null) {
			this.out.printf(" for service %s", sr);
		}

		if (exception != null) {
			this.out.print(" with exception: ");
			this.out.print(exception.getClass().getName());
			this.out.print("\n");
			exception.printStackTrace(this.out);
		}
	}

	@Override
	public void log(ServiceReference sr, int level, String message) {
		this.log(sr, level, message, null);
	}

	@Override
	public void log(int level, String message, Throwable exception) {
		this.log(null, level, message, exception);
	}

	@Override
	public void log(int level, String message) {
		this.log(null, level, message, null);
	}
}