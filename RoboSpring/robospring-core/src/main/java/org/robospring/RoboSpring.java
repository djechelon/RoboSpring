/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.robospring;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.robospring.android.AndroidContextAwareProcessor;
import org.robospring.inject.RoboSpringInjector;
import org.robospring.util.Pair;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import android.content.Context;

/**
 * The RoboSpring main class for statically accessing Spring configurations.
 * 
 * @author Daniel Thommes
 */
public class RoboSpring {

	private static final Log log = LogFactory.getLog(RoboSpring.class);

	/**
	 * Default context config location - points to the classpath
	 */
	private static final String DEFAULT_CONTEXT_CONFIG_LOCATION = "applicationContext.xml";

	/**
	 * Name of the optional file in the classpath to change the
	 * contextConfigLocation
	 */
	private static final String ROBOSPRING_PROPERTIES_FILENAME = "/robospring.properties";

	/**
	 * Key for the properties file
	 */
	private static final String CONTEXT_CONFIG_LOCATION_KEY = "contextConfigLocation";

	/**
	 * Location of the Spring context configuration - can be configured via
	 * properties file
	 */
	private static String contextConfigLocation = DEFAULT_CONTEXT_CONFIG_LOCATION;

	/**
	 * Map of contexts - several are possible and can be accessed with the
	 * special getContext method
	 */
	private static Map<String, Pair<AbstractXmlApplicationContext, RoboSpringInjector>> contextMap = new HashMap<String, Pair<AbstractXmlApplicationContext, RoboSpringInjector>>();

	private static ClassPathXmlApplicationContext parentContext;

	private static Context androidAppContext;

	static {
		/*************************************************************
		 * Try to load the robospring.properties file
		 *************************************************************/
		InputStream inStream = null;
		try {
			inStream = RoboSpring.class
					.getResourceAsStream(ROBOSPRING_PROPERTIES_FILENAME);
			if (inStream == null) {
				log.info("Could not find " + ROBOSPRING_PROPERTIES_FILENAME
						+ ". Using default location: "
						+ DEFAULT_CONTEXT_CONFIG_LOCATION);
			}
			else {
				Properties props = new Properties();
				props.load(inStream);
				contextConfigLocation = props
						.getProperty(CONTEXT_CONFIG_LOCATION_KEY);
				if (contextConfigLocation == null
						|| contextConfigLocation.trim().equals("")) {
					log.info(ROBOSPRING_PROPERTIES_FILENAME
							+ " does not define the key "
							+ CONTEXT_CONFIG_LOCATION_KEY
							+ ". Using default location: classpath:/applicationContext.xml");
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			if (inStream != null) {
				try {
					inStream.close();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Returns the context that has been defined by
	 * <code>robospring.properties</code> or the default context
	 * <code>classpath:/applicationContext.xml</code>, if no other
	 * <code>contextConfigLocation</code> has been defined.
	 * 
	 * @return the default or configured {@link RoboSpringApplicationContext}
	 */
	public static AbstractXmlApplicationContext getContext() {
		return getContext(contextConfigLocation);
	}

	public static AbstractXmlApplicationContext getContext(
			Context androidContext) {
		createParentContextForAndroid(androidContext);
		return getContext(contextConfigLocation);
	}

	/**
	 * Returns the {@link AbstractXmlApplicationContext} defined by the given
	 * resource. This way you can use different contexts at the same time or
	 * access configurations programmatically, e.g. for unit testing.
	 * 
	 * @param contextConfigLocation contextConfigLocation of the spring
	 * configuration
	 * @return the {@link AbstractXmlApplicationContext} configured in the
	 * resource given as parameter
	 */
	public static AbstractXmlApplicationContext getContext(
			String contextConfigLocation) {
		return getConfigurationPair(contextConfigLocation).first;
	}

	public static AbstractXmlApplicationContext getContext(
			Context androidContext, String contextConfigLocation) {
		createParentContextForAndroid(androidContext);
		return getConfigurationPair(contextConfigLocation).first;
	}

	/**
	 * Autowires the given bean with beans from the default context.
	 * 
	 * @param bean The annotated bean that shall be autowired
	 */
	public static void autowire(Context androidContext) {
		createParentContextForAndroid(androidContext);
		autowire(androidContext, contextConfigLocation);
	}

	/**
	 * Autowires the given bean with beans from the default context.
	 * 
	 * @param bean The annotated bean that shall be autowired
	 */
	public static void autowire(Object bean) {
		autowire(bean, contextConfigLocation);
	}

	/**
	 * Autowires the given bean with beans from the context defined by the
	 * resourceName.
	 * 
	 * @param bean The annotated bean that shall be autowired
	 */
	public static void autowire(Object bean, String contextConfigLocation) {
		getConfigurationPair(contextConfigLocation).second
				.processInjection(bean);
	}

	private static void createParentContextForAndroid(Context androidContext) {
		if (parentContext != null) {
			return;
		}
		log.info("Creating parent context with bean 'androidContext' for you to use in your configuration");
		// http://stackoverflow.com/questions/1109953/how-can-i-inject-a-bean-into-an-applicationcontext-before-it-loads-from-a-file
		parentContext = new ClassPathXmlApplicationContext();
		parentContext.refresh(); // THIS IS REQUIRED
		androidAppContext = androidContext.getApplicationContext();
		ConfigurableListableBeanFactory beanFactory = parentContext
				.getBeanFactory();
		beanFactory.registerSingleton("androidContext", androidAppContext);
	}

	/**
	 * @param contextConfigLocation
	 * @return
	 */
	private static Pair<AbstractXmlApplicationContext, RoboSpringInjector> getConfigurationPair(
			String contextConfigLocation) {

		synchronized (contextMap) {
			Pair<AbstractXmlApplicationContext, RoboSpringInjector> pair = contextMap
					.get(contextConfigLocation);
			if (pair == null) {
				AbstractXmlApplicationContext context = createContext(contextConfigLocation);
				RoboSpringInjector injector = new RoboSpringInjector(
						context.getBeanFactory());
				pair = new Pair<AbstractXmlApplicationContext, RoboSpringInjector>(
						context, injector);
				contextMap.put(contextConfigLocation, pair);
			}
			return pair;
		}
	}

	private static AbstractXmlApplicationContext createContext(
			String contextConfigLocation) {

		String scheme;
		String resourceName;

		try {
			URI url = new URI(contextConfigLocation);
			scheme = url.getScheme();
			if (scheme == null) {
				scheme = "classpath";
			}
			resourceName = url.getPath();
		}
		catch (URISyntaxException e) {
			log.warn("ContextConfigLocation does not contain a scheme identifier - using classpath:/ as default.");
			// Trying to load from class path with this name
			scheme = "classpath";
			resourceName = contextConfigLocation;
		}

		if ("classpath".equals(scheme)) {
			if (parentContext != null) {
				String[] configLocations = new String[] { resourceName };
				return new ClassPathXmlApplicationContext(configLocations,
						parentContext) {
					protected void prepareBeanFactory(
							ConfigurableListableBeanFactory beanFactory) {
						beanFactory
								.addBeanPostProcessor(new AndroidContextAwareProcessor(
										androidAppContext));
						super.prepareBeanFactory(beanFactory);
					};
				};
			}
			else {
				return new ClassPathXmlApplicationContext(resourceName);
			}
		}
		else if ("file".equals(scheme)) {
			if (parentContext != null) {
				String[] configLocations = new String[] { resourceName };
				return new FileSystemXmlApplicationContext(configLocations,
						parentContext) {
					protected void prepareBeanFactory(
							ConfigurableListableBeanFactory beanFactory) {
						beanFactory
								.addBeanPostProcessor(new AndroidContextAwareProcessor(
										androidAppContext));
						super.prepareBeanFactory(beanFactory);
					};
				};
			}
			else {
				return new FileSystemXmlApplicationContext(resourceName);
			}
		}
		else {
			throw new IllegalArgumentException("Cannot handle scheme '"
					+ scheme + "' for loading Spring configuration.");
		}
	}
}
