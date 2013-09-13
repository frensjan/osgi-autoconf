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

import java.io.IOException;
import java.io.PrintStream;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.log.LogService;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Modified;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta.AD;
import aQute.bnd.annotation.metatype.Meta.OCD;

@Component(immediate = true, designateFactory = AutoConfigurator.Config.class)
public class AutoConfigurator implements ServiceListener {
	private ConfigurationAdmin configAdmin;
	private LogService logger = new PrintStreamLogger(System.out);

	private Config config;

	private final Map<ServiceReference, Configuration> managedConfigs = new HashMap<>();

	@Activate
	@Modified
	public synchronized void modified(BundleContext context, Map<String, Object> props)
			throws InvalidSyntaxException {
		// parse configuration
		Config configNew = Configurable.createConfigurable(Config.class, props);

		if (this.config == null || this.config.filter().equals(configNew.filter()) == false) {
			this.deactivate();

			context.removeServiceListener(this);
			context.addServiceListener(this, configNew.filter());
		}

		if (this.config != null) {
			Multiplicity multiplicity = this.config.multiplicity();

			if (multiplicity.equals(configNew.multiplicity()) == false) {
				if (multiplicity == Multiplicity.ONE_LAZY || multiplicity == Multiplicity.ONE_EAGER) {
					while (this.managedConfigs.size() > 1) {
						ServiceReference ref = this.managedConfigs.keySet().iterator().next();
						Configuration managedConfig = this.managedConfigs.remove(ref);

						this.deleteManagedConfiguration(managedConfig);
					}
				}
			}
		}

		this.config = configNew;

		// for all known services matching the configured filter
		// create the configurations
		Set<ServiceReference> refs = this.getServices(context, this.config.filter());
		for (ServiceReference ref : refs) {
			try {
				if (this.managedConfigs.containsKey(ref)) {
					this.updateManagedConfiguration(ref);
				} else {
					this.createManagedConfiguration(ref);
				}
			} catch (IOException e) {
				this.logger.log(LogService.LOG_WARNING, "Couldn't create configuration", e);
			}
		}

		// if there is any configuration managed in response to a service no
		// longer available, delete the configuration
		for (ServiceReference ref : this.managedConfigs.keySet()) {
			// if the multiplicity is one, then don't delete the last config
			if (this.config.multiplicity() == Multiplicity.ONE_EAGER
					&& this.managedConfigs.size() == 1 && refs.size() > 0) {
				break;
			}

			// delete the configuration if the reference causing it is gone
			if (refs.contains(ref) == false) {
				this.deleteManagedConfiguration(this.managedConfigs.remove(ref));
			}
		}

		// if the multiplicity is one and is eager, then make sure there
		// is a configuration, no matter what
		if (this.config.multiplicity() == Multiplicity.ONE_EAGER && this.managedConfigs.size() < 1) {
			try {
				this.createManagedConfiguration(null);
			} catch (IOException e) {
				this.logger.log(LogService.LOG_WARNING, "Couldn't create configuration", e);
			}
		}
	}

	@Deactivate
	public synchronized void deactivate() {
		for (Configuration managedConfiguration : this.managedConfigs.values()) {
			this.deleteManagedConfiguration(managedConfiguration);
		}
	}

	@Reference(optional = true)
	public void setLogger(LogService logger) {
		this.logger = logger;
	}

	@Reference
	public void setConfigAdmin(ConfigurationAdmin configAdmin) {
		this.configAdmin = configAdmin;
	}

	@Override
	public synchronized void serviceChanged(ServiceEvent event) {
		try {
			switch (event.getType()) {
			case ServiceEvent.REGISTERED:
				this.createManagedConfiguration(event.getServiceReference());
				break;
			case ServiceEvent.MODIFIED:
				this.updateManagedConfiguration(event.getServiceReference());
				break;
			case ServiceEvent.UNREGISTERING:
				this.deleteManagedConfiguration(event.getServiceReference());
				break;
			}
		} catch (IOException e) {
			this.logger.log(LogService.LOG_WARNING, "Unable to process service changed event", e);
		}
	}

	private void createManagedConfiguration(ServiceReference serviceReference) throws IOException {
		if ((this.config.multiplicity() == Multiplicity.ONE_LAZY || this.config.multiplicity() == Multiplicity.ONE_EAGER)
				&& this.managedConfigs.size() >= 1) {
			return;
		}

		Configuration managedConfiguration = null;

		String pid = this.config.targetPid();
		String location = this.config.targetLocation();
		if (location != null && location.length() == 0) {
			location = null;
		}

		try {
			if (this.config.factory()) {
				managedConfiguration = this.configAdmin.createFactoryConfiguration(pid, location);
			} else {
				managedConfiguration = this.configAdmin.getConfiguration(pid, location);
			}
		} catch (NullPointerException e) {
			this.logger.log(LogService.LOG_ERROR, "Damn nullpointer in ConfigurationAdminImpl ...");
			return;
		}

		try {
			Properties props = this.createProperties(this.config.configuration(), serviceReference);
			managedConfiguration.update(props);
			this.managedConfigs.put(serviceReference, managedConfiguration);

			this.logger.log(LogService.LOG_DEBUG, String.format("Created configuration"));
		} catch (ParseException e) {
			this.logger.log(LogService.LOG_WARNING,
					"Couldn't parse the configuration specification",
					e);
		}
	}

	private void updateManagedConfiguration(ServiceReference serviceReference) {
		Configuration managedConfiguration = this.managedConfigs.get(serviceReference);

		try {
			@SuppressWarnings("rawtypes")
			Dictionary newProps = this.createProperties(this.config.configuration(),
					serviceReference);
			managedConfiguration.update(newProps);
		} catch (ParseException e) {
			this.logger.log(LogService.LOG_WARNING, "Couldn't parse the configuration", e);
		} catch (IOException e) {
			this.logger.log(LogService.LOG_WARNING, "Couldn't update the configuration", e);
		}
	}

	private void deleteManagedConfiguration(ServiceReference serviceReference) {
		Configuration managedConfiguration = this.managedConfigs.remove(serviceReference);

		if (managedConfiguration != null) {
			this.deleteManagedConfiguration(managedConfiguration);
		}
	}

	private void deleteManagedConfiguration(Configuration managedConfiguration) {
		try {
			managedConfiguration.delete();
		} catch (IllegalStateException | IOException e) {
			this.logger.log(LogService.LOG_INFO, "unable to delete managed configuration");
		}
	}

	/**
	 * Create properties from the given property lines. Any references (in the
	 * format of {name}) are resolved as properties of the given service
	 * reference.
	 * 
	 * @param propertyLines
	 *            The keys and values as array of strings in the format of
	 *            key=value.
	 * @param serviceReference
	 *            The service reference used to resolve references in the
	 *            properties
	 * @throws ParseException
	 *             Thrown if the property lines aren't correctly formatted.
	 */
	private Properties createProperties(String[] propertyLines, ServiceReference serviceReference)
			throws ParseException {
		Properties props = new Properties();
		for (String prop : propertyLines) {
			String[] keyValue = prop.split("=", 2);
			if (keyValue.length != 2) {
				throw new ParseException(
						String.format("property %s is not in the format key=value", prop), 0);
			}

			String key = keyValue[0];
			String value = keyValue[1];

			int openIdx = value.indexOf('{');
			int closeIdx = value.indexOf('}');

			// if the value does not contain a reference, or there is no service
			// registration which can provide the values, use it 'as is'
			if (openIdx == -1 || closeIdx == -1 || serviceReference == null) {
				props.setProperty(key, value);
			}

			// if value is only a reference, get the value from the
			// serviceReference (which is the context for the reference) as an
			// object (instead of copying it as a string)
			else if (openIdx == 0 && closeIdx == value.length() - 1) {
				String ref = value.substring(1, value.length() - 1);
				props.put(key, serviceReference.getProperty(ref));
			}

			// otherwise the value contains multiple references, build a
			// string from that by replacing all references with the value from
			// the serviceReference (which is the context for the property
			// reference)
			else {
				Matcher matcher = Pattern.compile("\\{(.*)\\}").matcher(value);
				StringBuilder valueBuilder = new StringBuilder();

				// find all references and resolve them with values from the
				// serviceReference (which is the context for the references)
				int pos = 0;
				for (; pos < value.length() - 1 && matcher.find(pos); pos = matcher.end() + 1) {
					// append the text before the reference
					String text = value.substring(pos, matcher.start());
					valueBuilder.append(text);

					// get the reference value and append it
					String ref = matcher.group(1);
					Object v = serviceReference.getProperty(ref);
					valueBuilder.append(v);
				}

				// append the tail
				if (pos < value.length()) {
					valueBuilder.append(value.substring(pos - 1));
				}

				// build the value and set the property
				props.setProperty(key, valueBuilder.toString());
			}
		}

		return props;
	}

	private Set<ServiceReference> getServices(BundleContext context, String filter)
			throws InvalidSyntaxException {
		ServiceReference[] refs = context.getAllServiceReferences(null, filter);

		if (refs != null) {
			Set<ServiceReference> refsSet = new HashSet<>();
			refsSet.addAll(Arrays.asList(refs));
			return refsSet;
		} else {
			return new HashSet<>(0);
		}
	}

	private final class PrintStreamLogger implements LogService {
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

	public static enum Multiplicity {
		ONE_FOR_EACH, ONE_LAZY, ONE_EAGER
	}
}
