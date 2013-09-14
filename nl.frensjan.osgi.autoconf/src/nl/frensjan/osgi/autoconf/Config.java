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

import aQute.bnd.annotation.metatype.Meta.AD;
import aQute.bnd.annotation.metatype.Meta.OCD;

@OCD(description = "Auto Configuration")
public interface Config {
	@AD(description = "The LDAP filter used for matching services in the"
			+ " OSGi registry, for which configurations are to be managed.")
	String filter();

	@AD(deflt = "ONE_FOR_EACH", description = "The amount of configurations"
			+ " to create and manage in response to the amount of service"
			+ " registrations matched")
	Multiplicity multiplicity();

	@AD(description = "The (factory) pid of the configuration to create.")
	String targetPid();

	@AD(deflt = "true", description = "Whether a factory configuration"
			+ " should be created or not.")
	boolean factory();

	@AD(required = false, description = "The (bundle) location to tie the"
			+ " managed configurations to (e.g. in case when multiple bundles"
			+ " with different versions are present in the OSGi container).")
	String targetLocation();

	@AD(description = "The specification of the configuration to manage"
			+ " in response to services matching the specified filter."
			+ " Each line is an entry in the managed configuration,"
			+ " formatted as key=value, the value can either be a plain string"
			+ " or references to configuration properties from the triggering"
			+ " service can be used by specifying {ref}, where ref is the name"
			+ " of the referenced property.")
	String[] configuration();
}