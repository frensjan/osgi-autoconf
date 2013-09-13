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

	// for finding the services
	private final int timeout = 1000;

	private final BundleContext context = FrameworkUtil.getBundle(this.getClass())
			.getBundleContext();

	private ConfigurationAdmin configAdmin;
	private Configuration autoconfig;

	@Override
	protected void setUp() throws Exception {
		ServiceReference serviceReference = this.context.getServiceReference(ConfigurationAdmin.class.getName());
		this.configAdmin = (ConfigurationAdmin) this.context.getService(serviceReference);
	}

	@Override
	protected void tearDown() throws Exception {
		if (this.autoconfig != null) {
			this.autoconfig.delete();
			this.assertConsumerCount(0);
		}

		Thread.sleep(100);
	}

	public void testSimple() throws Exception {
		this.createAutoConfig(Multiplicity.ONE_FOR_EACH);
		this.assertConsumersForProducers(1, 1);
	}

	public void testManyToMany() throws Exception {
		this.createAutoConfig(Multiplicity.ONE_FOR_EACH);
		this.assertConsumersForProducers(10, 10);
	}

	public void testOneLazy() throws Exception {
		this.createAutoConfig(Multiplicity.ONE_LAZY);
		this.assertConsumersForProducers(10, 1);
	}

	public void testOneEager() throws Exception {
		this.assertConsumerCount(0);

		this.createAutoConfig(Multiplicity.ONE_EAGER);
		this.assertConsumerCount(1);

		this.assertConsumersForProducers(100, 1);
		this.assertConsumerCount(1);

		this.autoconfig.delete();
		this.autoconfig = null;
		this.assertConsumerCount(0);
	}

	private void assertConsumersForProducers(int producerCount, int consumerCount)
			throws IOException, InvalidSyntaxException, InterruptedException {
		Configuration[] producerConfigs = new Configuration[0];

		try {
			// create a producer and assert it is matched with a consumer
			producerConfigs = this.createProducerConfigs(producerCount);
			this.assertConsumerCount(consumerCount);
		} finally {
			// remove the producer and assert the consumer is removed
			this.deleteAll(producerConfigs);
		}
	}

	private void assertConsumerCount(int count) throws InvalidSyntaxException, InterruptedException {
		ServiceReference[] refs = this.getServiceReferences(CONSUMER_CLASS, this.timeout, count);
		assertEquals(count, refs.length);
	}

	private void deleteAll(Configuration[] configs) throws IOException {
		for (Configuration config : configs) {
			config.delete();
		}
	}

	private Configuration[] createProducerConfigs(int count) throws IOException {
		Configuration[] configs = new Configuration[count];
		for (int i = 0; i < count; i++) {
			configs[i] = this.createProducerConfig();
		}

		return configs;
	}

	private Configuration createProducerConfig() throws IOException {
		Configuration producerConfig = this.configAdmin.createFactoryConfiguration(PRODUCER_CLASS.getName(),
				null);
		producerConfig.update(new Properties());
		return producerConfig;
	}

	private void createAutoConfig(Multiplicity multiplicity) throws IOException {
		this.autoconfig = this.configAdmin.createFactoryConfiguration(AutoConfigurator.class.getName());
		Properties properties = new Properties();
		properties.put("filter",
				String.format("(service.factoryPid=%s)", ProducerImpl.class.getName()));
		properties.put("multiplicity", multiplicity.toString());
		properties.put("targetPid", ConsumerImpl.class.getName());
		properties.put("factory", Boolean.TRUE);
		// properties.put("targetLocation", value);
		properties.put("configuration", new String[] { "a=bla",
				"b=created for {service.pid}",
				"producer.target=(service.pid={service.pid})" });

		this.autoconfig.update(properties);
	}

	private ServiceReference[] getServiceReferences(Class<ConsumerImpl> clazz, int timeout,
			int expected) throws InvalidSyntaxException, InterruptedException {
		int iterations = 100;
		timeout = Math.max(1, timeout / iterations);

		ServiceReference[] refs = this.getServiceReferences(clazz);
		int iteration = 0;
		for (; iteration < iterations && refs.length != expected; iteration++) {
			refs = this.getServiceReferences(CONSUMER_CLASS);
			Thread.sleep(timeout);
		}

		System.out.printf("%s services for %s in %s\n", refs.length == expected ? "found"
				: "didn't find", clazz, iteration * timeout);

		return refs;
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
