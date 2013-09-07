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

package nl.frensjan.osgi.autoconf.test;

import java.io.IOException;
import java.util.Properties;

import junit.framework.TestCase;
import nl.frensjan.osgi.autoconf.AutoConfigurator;
import nl.frensjan.osgi.autoconf.AutoConfigurator.Multiplicity;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

public class AutoConfiguratorTest extends TestCase {
	private static Class<ConsumerImpl> CONSUMER_CLASS = ConsumerImpl.class;
	private static Class<ProducerImpl> PRODUCER_CLASS = ProducerImpl.class;

	private final BundleContext context = FrameworkUtil.getBundle(this.getClass())
			.getBundleContext();

	private ConfigurationAdmin configAdmin;

	@Override
	protected void setUp() throws Exception {
		ServiceReference serviceReference = this.context.getServiceReference(ConfigurationAdmin.class.getName());
		this.configAdmin = (ConfigurationAdmin) this.context.getService(serviceReference);
	}

	public void testManyToMany() throws Exception {
		this.createAutoConfig();

		ServiceReference[] before = this.getServiceReferences(CONSUMER_CLASS);

		Configuration producerImplConfiguration = this.configAdmin.createFactoryConfiguration(PRODUCER_CLASS.getName(),
				null);
		producerImplConfiguration.update(new Properties());

		ServiceReference[] after = this.getServiceReferences(CONSUMER_CLASS);
		for (int i = 0; i < 10 && before.length == after.length; i++) {
			Thread.sleep(100);
			after = this.getServiceReferences(CONSUMER_CLASS);
		}

		assertEquals(before.length + 1, after.length);

		producerImplConfiguration.delete();

		after = this.getServiceReferences(CONSUMER_CLASS);
		for (int i = 0; i < 10 && before.length != after.length; i++) {
			Thread.sleep(100);
			after = this.getServiceReferences(CONSUMER_CLASS);
		}

		assertEquals(before.length, after.length);
	}

	private void createAutoConfig() throws IOException {
		Configuration autoconfig = this.configAdmin.createFactoryConfiguration(AutoConfigurator.class.getName());
		Properties properties = new Properties();
		properties.put("filter",
				String.format("(service.factoryPid=%s)", ProducerImpl.class.getName()));
		properties.put("multiplicity", Multiplicity.ONE_TO_ONE.toString());
		properties.put("targetPid", ConsumerImpl.class.getName());
		properties.put("factory", Boolean.FALSE);
		// properties.put("targetLocation", value);
		properties.put("configuration", new String[] { "a=bla", "b=created for {service.pid}" });

		autoconfig.update(properties);
	}

	private ServiceReference[] getServiceReferences(Class<ConsumerImpl> clazz)
			throws InvalidSyntaxException {
		ServiceReference[] refs = this.context.getServiceReferences(clazz.getName(), null);

		if (refs == null) {
			return new ServiceReference[0];
		} else {
			return refs;
		}
	}
}
