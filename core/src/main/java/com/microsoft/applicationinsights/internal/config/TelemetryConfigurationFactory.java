/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.internal.config;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.extensibility.ContextInitializer;
import com.microsoft.applicationinsights.extensibility.TelemetryInitializer;
import com.microsoft.applicationinsights.channel.concrete.inprocess.InProcessTelemetryChannel;
import com.microsoft.applicationinsights.channel.TelemetryChannel;
import com.microsoft.applicationinsights.extensibility.TelemetryModule;
import com.microsoft.applicationinsights.extensibility.initializer.DeviceInfoContextInitializer;
import com.microsoft.applicationinsights.extensibility.initializer.SdkVersionContextInitializer;
import com.microsoft.applicationinsights.internal.annotation.AnnotationPackageScanner;
import com.microsoft.applicationinsights.internal.annotation.PerformanceModule;
import com.microsoft.applicationinsights.internal.jmx.JmxAttributeData;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.internal.perfcounter.JmxMetricPerformanceCounter;
import com.microsoft.applicationinsights.internal.perfcounter.PerformanceCounterContainer;
import com.microsoft.applicationinsights.internal.perfcounter.PerformanceCounterConfigurationAware;

import com.google.common.base.Strings;

/**
 * Initializer class for configuration instances.
 */
public enum TelemetryConfigurationFactory {
    INSTANCE;

    // Default file name
    private final static String CONFIG_FILE_NAME = "ApplicationInsights.xml";
    private final static String DEFAULT_PERFORMANCE_MODULES_PACKAGE = "com.microsoft.applicationinsights";
    private final static String BUILT_IN_NAME = "BuiltIn";

    private String performanceCountersSection = DEFAULT_PERFORMANCE_MODULES_PACKAGE;

    final static String EXTERNAL_PROPERTY_IKEY_NAME = "APPLICATION_INSIGHTS_IKEY";

    private AppInsightsConfigurationBuilder builder = new JaxbAppInsightsConfigurationBuilder();

    TelemetryConfigurationFactory() {
    }

    /**
     * Currently we do the following:
     *
     * Set Instrumentation Key
     * Set Developer Mode (default false)
     * Set Channel (default {@link InProcessTelemetryChannel})
     * Set Tracking Disabled Mode (default false)
     * Set Context Initializers where they should be written with full package name
     * Set Telemetry Initializers where they should be written with full package name
     * @param configuration The configuration that will be populated
     */
    public final void initialize(TelemetryConfiguration configuration) {
        try {
            InputStream configurationFile = new ConfigurationFileLocator(CONFIG_FILE_NAME).getConfigurationFile();
            if (configurationFile == null) {
                setMinimumConfiguration(null, configuration);
                return;
            }

            ApplicationInsightsXmlConfiguration applicationInsightsConfig = builder.build(configurationFile);
            if (applicationInsightsConfig == null) {
                InternalLogger.INSTANCE.logAlways(InternalLogger.LoggingLevel.ERROR, "Failed to read configuration file");
                setMinimumConfiguration(applicationInsightsConfig, configuration);
                return;
            }

            setInternalLogger(applicationInsightsConfig.getSdkLogger(), configuration);

            setInstrumentationKey(applicationInsightsConfig, configuration);

            setChannel(applicationInsightsConfig.getChannel(), configuration);

            configuration.setTrackingIsDisabled(applicationInsightsConfig.isDisableTelemetry());

            setContextInitializers(applicationInsightsConfig.getContextInitializers(), configuration);
            setTelemetryInitializers(applicationInsightsConfig.getTelemetryInitializers(), configuration);
            setTelemetryModules(applicationInsightsConfig, configuration);

            initializeComponents(configuration);
        } catch (Exception e) {
            InternalLogger.INSTANCE.logAlways(InternalLogger.LoggingLevel.ERROR, "Failed to initialize configuration, exception: %s", e.getMessage());
        }
    }

    private void setMinimumConfiguration(ApplicationInsightsXmlConfiguration userConfiguration, TelemetryConfiguration configuration) {
        setInstrumentationKey(userConfiguration, configuration);
        configuration.setChannel(new InProcessTelemetryChannel());
    }

    private void setInternalLogger(SDKLoggerXmlElement sdkLogger, TelemetryConfiguration configuration) {
        if (sdkLogger == null) {
            return;
        }

        InternalLogger.INSTANCE.initialize(sdkLogger.getType(), sdkLogger.getData());
    }

    /**
     * Sets the configuration data of Telemetry Initializers in configuration class.
     * @param telemetryInitializers The configuration data.
     * @param configuration The configuration class.
     */
    private void setTelemetryInitializers(TelemetryInitializersXmlElement telemetryInitializers, TelemetryConfiguration configuration) {
        if (telemetryInitializers == null) {
            return;
        }

        List<TelemetryInitializer> initializerList = configuration.getTelemetryInitializers();
        loadComponents(TelemetryInitializer.class, initializerList, telemetryInitializers.getAdds());
    }

    /**
     * Sets the configuration data of Context Initializers in configuration class.
     * @param contextInitializers The configuration data.
     * @param configuration The configuration class.
     */
    private void setContextInitializers(ContextInitializersXmlElement contextInitializers, TelemetryConfiguration configuration) {
        List<ContextInitializer> initializerList = configuration.getContextInitializers();

        // To keep with prev version. A few will probably be moved to the configuration
        initializerList.add(new SdkVersionContextInitializer());
        initializerList.add(new DeviceInfoContextInitializer());

        if (contextInitializers != null) {
            loadComponents(ContextInitializer.class, initializerList, contextInitializers.getAdds());
        }
    }

    /**
     * Sets the configuration data of Modules Initializers in configuration class.
     * @param appConfiguration The configuration data.
     * @param configuration The configuration class.
     */
    private void setTelemetryModules(ApplicationInsightsXmlConfiguration appConfiguration, TelemetryConfiguration configuration) {
        TelemetryModulesXmlElement configurationModules = appConfiguration.getModules();
        List<TelemetryModule> modules = configuration.getTelemetryModules();

        if (configurationModules != null) {
            loadComponents(TelemetryModule.class, modules, configurationModules.getAdds());
        }

        List<TelemetryModule> pcModules = getPerformanceModules(appConfiguration.getPerformance());
        modules.addAll(pcModules);
    }

    /**
     * Setting an instrumentation key:
     * First we try the system property '-DAPPLICATION_INSIGHTS_IKEY=i_key'
     * Next we will try the environment variable 'APPLICATION_INSIGHTS_IKEY',
     * Next we will try to fetch the i-key from the ApplicationInsights.xml
     * @param userConfiguration The configuration that was represents the user's configuration in ApplicationInsights.xml.
     * @param configuration The configuration class.
     */
    private void setInstrumentationKey(ApplicationInsightsXmlConfiguration userConfiguration, TelemetryConfiguration configuration) {
        try {
            // First, check whether an i-key was provided as a java system property i.e. '-DAPPLICATION_INSIGHTS_IKEY=i_key'
            String ikey = System.getProperty(EXTERNAL_PROPERTY_IKEY_NAME);

            if (!Strings.isNullOrEmpty(ikey)) {
                configuration.setInstrumentationKey(ikey);
                return;
            }

            // Second, try to find the i-key as an environment variable 'APPLICATION_INSIGHTS_IKEY'
            ikey = System.getenv(EXTERNAL_PROPERTY_IKEY_NAME);
            if (!Strings.isNullOrEmpty(ikey)) {
                configuration.setInstrumentationKey(ikey);
                return;
            }

            // Else, try to find the i-key in ApplicationInsights.xml
            if (userConfiguration != null) {
                ikey = userConfiguration.getInstrumentationKey();
                if (ikey == null) {
                    return;
                }

                ikey = ikey.trim();
                if (ikey.length() == 0) {
                    return;
                }

                configuration.setInstrumentationKey(ikey);
            }
        } catch (Exception e) {
            InternalLogger.INSTANCE.error("Failed to set instrumentation key: '%s'", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<TelemetryModule> getPerformanceModules(PerformanceCountersXmlElement performanceConfigurationData) {
        ArrayList<TelemetryModule> modules = new ArrayList<TelemetryModule>();

        final List<String> performanceModuleNames =
                new AnnotationPackageScanner().scanForClassAnnotations(new Class[]{PerformanceModule.class}, performanceCountersSection);

        if (performanceModuleNames.size() == 0) {

            // Only a workaround for JBoss web servers.
            // Will be removed once the issue will be investigated and fixed.
            performanceModuleNames.addAll(getDefaultPerformanceModulesNames());
        }

        for (String performanceModuleName : performanceModuleNames) {
            TelemetryModule module = createInstance(performanceModuleName, TelemetryModule.class);
            if (module != null) {
                PerformanceModule pmAnnotation = module.getClass().getAnnotation(PerformanceModule.class);
                if (!performanceConfigurationData.isUseBuiltIn() && BUILT_IN_NAME.equals(pmAnnotation.value())) {
                    continue;
                }
                if (module instanceof PerformanceCounterConfigurationAware) {
                    PerformanceCounterConfigurationAware awareModule = (PerformanceCounterConfigurationAware)module;
                    try {
                        awareModule.addConfigurationData(performanceConfigurationData);
                    } catch (Exception e) {
                        InternalLogger.INSTANCE.error("Failed to add configuration data to performance module: '%s'", e.getMessage());
                    }
                }
                modules.add(module);
            } else {
                InternalLogger.INSTANCE.error("Failed to create performance module: '%s'", performanceModuleName);
            }
        }

        loadCustomJmxPCs(performanceConfigurationData.getJmxXmlElements());

        return modules;
    }

    /**
     * This method is only a workaround until the failure to load PCs in JBoss web servers will be solved.
     */
    private List<String> getDefaultPerformanceModulesNames() {
        InternalLogger.INSTANCE.trace("Default performance counters will be automatically loaded.");

        ArrayList<String> modules = new ArrayList<String>();
        modules.add("com.microsoft.applicationinsights.internal.perfcounter.ProcessPerformanceCountersModule");
        modules.add("com.microsoft.applicationinsights.web.internal.perfcounter.WebPerformanceCounterModule");

        return modules;
    }

    /**
     * The method will load the Jmx performance counters requested by the user to the system:
     * 1. Build a map where the key is the Jmx object name and the value is a list of requested attributes.
     * 2. Go through all the requested Jmx counters:
     *      a. If the object name is not in the map, add it with an empty list
     *         Else get the list
     *      b. Add the attribute to the list.
     *  3. Go through the map
     *      For every entry (object name and attributes)
     *          Build a {@link JmxMetricPerformanceCounter}
     *          Register the Performance Counter in the {@link PerformanceCounterContainer}
     *
     * @param jmxXmlElements
     */
    private void loadCustomJmxPCs(ArrayList<JmxXmlElement> jmxXmlElements) {
        try {
            if (jmxXmlElements == null) {
                return;
            }

            HashMap<String, Collection<JmxAttributeData>> data = new HashMap<String, Collection<JmxAttributeData>>();

            // Build a map of object name to its requested attributes
            for (JmxXmlElement jmxElement : jmxXmlElements) {
                Collection<JmxAttributeData> collection = data.get(jmxElement.getObjectName());
                if (collection == null) {
                    collection = new ArrayList<JmxAttributeData>();
                    data.put(jmxElement.getObjectName(), collection);
                }

                if (Strings.isNullOrEmpty(jmxElement.getObjectName())) {
                    InternalLogger.INSTANCE.error("JMX object name is empty, will be ignored");
                    continue;
                }

                if (Strings.isNullOrEmpty(jmxElement.getAttribute())) {
                    InternalLogger.INSTANCE.error("JMX attribute is empty for '%s', will be ignored", jmxElement.getObjectName());
                    continue;
                }

                if (Strings.isNullOrEmpty(jmxElement.getDisplayName())) {
                    InternalLogger.INSTANCE.error("JMX display name is empty for '%s', will be ignored", jmxElement.getObjectName());
                    continue;
                }

                collection.add(new JmxAttributeData(jmxElement.getDisplayName(), jmxElement.getAttribute(), jmxElement.getType()));
            }

            // Register each entry in the performance container
            for (Map.Entry<String, Collection<JmxAttributeData>> entry : data.entrySet()) {
                try {
                    if (PerformanceCounterContainer.INSTANCE.register(new JmxMetricPerformanceCounter(entry.getKey(), entry.getKey(), entry.getValue()))) {
                        InternalLogger.INSTANCE.trace("Registered JMX performance counter '%s'", entry.getKey());
                    } else {
                        InternalLogger.INSTANCE.trace("Failed to register JMX performance counter '%s'", entry.getKey());
                    }
                } catch (Exception e) {
                    InternalLogger.INSTANCE.error("Failed to register JMX performance counter '%s': '%s'", entry.getKey(), e.getMessage());
                }
            }
        } catch (Exception e) {
            InternalLogger.INSTANCE.error("Failed to register JMX performance counters: '%s'", e.getMessage());
        }
    }

    /**
     * Setting the channel.
     * @param channelXmlElement The configuration element holding the channel data.
     * @param configuration The configuration class.
     * @return True on success.
     */
    private boolean setChannel(ChannelXmlElement channelXmlElement, TelemetryConfiguration configuration) {
        String channelName = channelXmlElement.getType();
        if (channelName != null) {
            TelemetryChannel channel = createInstance(channelName, TelemetryChannel.class, Map.class, channelXmlElement.getData());
            if (channel != null) {
                configuration.setChannel(channel);
                return true;
            } else {
                InternalLogger.INSTANCE.error("Failed to create '%s', will create the default one with default arguments", channelName);
            }
        }

        try {
            // We will create the default channel and we assume that the data is relevant.
            configuration.setChannel(new InProcessTelemetryChannel(channelXmlElement.getData()));
            return true;
        } catch (Exception e) {
            InternalLogger.INSTANCE.error("Failed to create InProcessTelemetryChannel, exception: %s, will create the default one with default arguments", e.getMessage());
            configuration.setChannel(new InProcessTelemetryChannel());
            return true;
        }
    }

    /**
     * Generic method that creates instances based on their names and adds them to a Collection
     *
     * Note that the class does its 'best effort' to create an instance and will not fail the method
     * if an instance (or more) was failed to create. This is naturally, a policy we can easily replace
     *
     * @param clazz The class all instances should have
     * @param list The container of instances, this is where we store our instances that we create
     * @param classNames Classes to create.
     * @param <T>
     */
    private <T> void loadComponents(
            Class<T> clazz,
            List<T> list,
            Collection<AddTypeXmlElement> classNames) {
        if (classNames == null) {
            return;
        }

        for (AddTypeXmlElement className : classNames) {
            T initializer = null;

            // If parameters have been provided, we try to load the component with provided parameters map. Otherwise,
            // we fallback to initialize the component with the default ctor.
            if (className.getParameters().size() != 0) {
                initializer = createInstance(className.getType(), clazz, Map.class, className.getData());
            }

            if (initializer == null) {
                initializer = createInstance(className.getType(), clazz);
            }

            if (initializer != null) {
                list.add(initializer);
            }
        }
    }

    /**
     * Creates an instance from its name. We suppress Java compiler warnings for Generic casting
     *
     * Note that currently we 'swallow' all exceptions and simply return null if we fail
     *
     * @param className The class we create an instance of
     * @param interfaceClass The class' parent interface we wish to work with
     * @param <T> The class type to create
     * @return The instance or null if failed
     */
    @SuppressWarnings("unchecked")
    private <T> T createInstance(String className, Class<T> interfaceClass) {
        try {
            if (Strings.isNullOrEmpty(className)) {
                return null;
            }

            Class<?> clazz = Class.forName(className).asSubclass(interfaceClass);
            T instance = (T)clazz.newInstance();

            return instance;
        } catch (ClassCastException e) {
            InternalLogger.INSTANCE.error("Failed to create %s, %s", className, e.getMessage());
        } catch (ClassNotFoundException e) {
            InternalLogger.INSTANCE.error("Failed to create %s, %s", className, e.getMessage());
        } catch (InstantiationException e) {
            InternalLogger.INSTANCE.error("Failed to create %s, %s", className, e.getMessage());
        } catch (IllegalAccessException e) {
            InternalLogger.INSTANCE.error("Failed to create %s, %s", className, e.getMessage());
        } catch (Exception e) {
            InternalLogger.INSTANCE.error("Failed to create %s, %s", className, e.getMessage());
        }

        return null;
    }

    /**
     * Creates an instance from its name. We suppress Java compiler warnings for Generic casting
     * The class is created by using a constructor that has one parameter which is sent to the method
     *
     * Note that currently we 'swallow' all exceptions and simply return null if we fail
     *
     * @param className The class we create an instance of
     * @param interfaceClass The class' parent interface we wish to work with
     * @param argumentClass Type of class to use as argument for Ctor
     * @param argument The argument to pass the Ctor
     * @param <T> The class type to create
     * @param <A> The class type as the Ctor argument
     * @return The instance or null if failed
     */
    @SuppressWarnings("unchecked")
    private <T, A> T createInstance(String className, Class<T> interfaceClass, Class<A> argumentClass, A argument) {
        try {
            if (Strings.isNullOrEmpty(className)) {
                return null;
            }

            Class<?> clazz = Class.forName(className).asSubclass(interfaceClass);
            Constructor<?> clazzConstructor = clazz.getConstructor(argumentClass);
            T instance = (T)clazzConstructor.newInstance(argument);
            return instance;
        } catch (ClassCastException e) {
            InternalLogger.INSTANCE.error("Failed to create %s, %s", className, e.getMessage());
        } catch (ClassNotFoundException e) {
            InternalLogger.INSTANCE.error("Failed to create %s, %s", className, e.getMessage());
        } catch (InstantiationException e) {
            InternalLogger.INSTANCE.error("Failed to create %s, %s", className, e.getMessage());
        } catch (IllegalAccessException e) {
            InternalLogger.INSTANCE.error("Failed to create %s, %s", className, e.getMessage());
        } catch (Exception e) {
            InternalLogger.INSTANCE.error("Failed to create %s, %s", className, e.getMessage());
        }

        return null;
    }

    // TODO: include context/telemetry initializers - where do they initialized?
    private void initializeComponents(TelemetryConfiguration configuration) {
        List<TelemetryModule> telemetryModules = configuration.getTelemetryModules();

        for (TelemetryModule module : telemetryModules) {
            try {
                module.initialize(configuration);
            } catch (Exception e) {
                InternalLogger.INSTANCE.error(
                        "Failed to initialized telemetry module " + module.getClass().getSimpleName() + ". Excepption");
            }
        }
    }

    void setPerformanceCountersSection(String performanceCountersSection) {
        this.performanceCountersSection = performanceCountersSection;
    }

    void setBuilder(AppInsightsConfigurationBuilder builder) {
        this.builder = builder;
    }
}
