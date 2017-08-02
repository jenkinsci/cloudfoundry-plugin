/**
 * © Copyright 2015 Hewlett Packard Enterprise Development LP
 * Copyright (c) ActiveState 2014 - ALL RIGHTS RESERVED.
 * © 2017 The original author or authors.
 */

package com.hpe.cloudfoundryjenkins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import org.kohsuke.stapler.DataBoundConstructor;
import java.util.ArrayList;
import java.util.List;


public class CloudFoundryPushPublisher extends Recorder {

    public String target;
    public String organization;
    public String cloudSpace;
    public String credentialsId;
    public boolean selfSigned;
    public boolean resetIfExists;
    public int pluginTimeout;
    public List<Service> servicesToCreate;
    public ManifestChoice manifestChoice;

    /**
     * The constructor is databound from the Jenkins config page, which is defined in config.jelly.
     */
    @DataBoundConstructor
    public CloudFoundryPushPublisher(String target, String organization, String cloudSpace,
                                     String credentialsId, boolean selfSigned,
                                     boolean resetIfExists, int pluginTimeout, List<Service> servicesToCreate,
                                     ManifestChoice manifestChoice) {
        this.target = target;
        this.organization = organization;
        this.cloudSpace = cloudSpace;
        this.credentialsId = credentialsId;
        this.selfSigned = selfSigned;
        this.resetIfExists = resetIfExists;
        if (pluginTimeout == 0) {
            this.pluginTimeout = CloudFoundryUtils.DEFAULT_PLUGIN_TIMEOUT;
        } else {
            this.pluginTimeout = pluginTimeout;
        }
        if (servicesToCreate == null) {
            this.servicesToCreate = new ArrayList<Service>();
        } else {
            this.servicesToCreate = servicesToCreate;
        }
        if (manifestChoice == null) {
            this.manifestChoice = ManifestChoice.defaultManifestFileConfig();
        } else {
            this.manifestChoice = manifestChoice;
        }
    }

    /**
     * This is the main method, which gets called when the plugin must run as part of a build.
     */
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        // We don't want to push if the build failed
        Result result = build.getResult();
        if (result != null && result.isWorseThan(Result.SUCCESS))
            return true;

        CloudFoundryPushTask task = new CloudFoundryPushTask(target, organization, cloudSpace, credentialsId, selfSigned, pluginTimeout, servicesToCreate, manifestChoice);
        return task.perform(build.getWorkspace(), build.getProject(), launcher, listener);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * This class contains the choice of using either a manifest file or the optional Jenkins configuration.
     * It also contains all the variables of either choice, which will be non-null only if their choice was selected.
     * It bothers me that a single class has these multiple uses, but everything is contained in the radioBlock tags
     * in config.jelly and must be databound to a single class. It doesn't seem like there is an alternative.
     */
    public static class ManifestChoice {
        // This should only be either "manifestFile" or "jenkinsConfig"
        public final String value;

        // Variable of the choice "manifestFile". Will be null if 'value' is "jenkinsConfig".
        public final String manifestFile;

        // Variables of the choice "jenkinsConfig". Will all be null (or 0 or false) if 'value' is "manifestFile".
        public final String appName;
        public final int memory;
        public final String hostname;
        public final int instances;
        public final int timeout;
        public final boolean noRoute;
        public final String appPath;
        public final String buildpack;
        public final String stack;
        public final String command;
        public final String domain;
        public final List<EnvironmentVariable> envVars;
        public final List<ServiceName> servicesNames;


        @DataBoundConstructor
        public ManifestChoice(String value, String manifestFile,
                              String appName, int memory, String hostname, int instances, int timeout, boolean noRoute,
                              String appPath, String buildpack, String stack, String command, String domain,
                              List<EnvironmentVariable> envVars, List<ServiceName> servicesNames) {
            if (value == null) {
                this.value = "manifestFile";
            } else {
                this.value = value;
            }
            if (manifestFile == null || manifestFile.isEmpty()) {
                this.manifestFile = CloudFoundryUtils.DEFAULT_MANIFEST_PATH;
            } else {
                this.manifestFile = manifestFile;
            }

            this.appName = appName;
            this.memory = memory;
            this.hostname = hostname;
            this.instances = instances;
            this.timeout = timeout;
            this.noRoute = noRoute;
            this.appPath = appPath;
            this.buildpack = buildpack;
            this.stack = stack;
            this.command = command;
            this.domain = domain;
            this.envVars = envVars;
            this.servicesNames = servicesNames;
        }

        /**
         * Constructs a ManifestChoice with the default settings for using a manifest file.
         * This is mostly for easier unit tests.
         */
        public static ManifestChoice defaultManifestFileConfig() {
            return new ManifestChoice("manifestFile", CloudFoundryUtils.DEFAULT_MANIFEST_PATH,
                    null, 0, null, 0, 0, false, null, null, null, null, null, null, null);
        }
    }

    public static class EnvironmentVariable {
        public final String key;
        public final String value;

        @DataBoundConstructor
        public EnvironmentVariable(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    // This class is for services to bind to the app. We only get the name of the service.
    public static class ServiceName {
        public final String name;

        @DataBoundConstructor
        public ServiceName(String name) {
            this.name = name;
        }
    }

    // This class is for services to create. We need name, type and plan for this.
    public static class Service {
        public final String name;
        public final String type;
        public final String plan;
        public final boolean resetService;

        @DataBoundConstructor
        public Service(String name, String type, String plan, boolean resetService) {
            this.name = name;
            this.type = type;
            this.plan = plan;
            this.resetService = resetService;
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractCloudFoundryPushDescriptor<Publisher> {
    }

    /**
     * This method is called after a plugin upgrade, to convert an old configuration into a new one.
     * See: https://wiki.jenkins-ci.org/display/JENKINS/Hint+on+retaining+backward+compatibility
     */
    @SuppressWarnings("unused")
    private Object readResolve() {
        if (servicesToCreate == null) { // Introduced in 1.4
            this.servicesToCreate = new ArrayList<Service>();
        }
        if (pluginTimeout == 0) { // Introduced in 1.5
            this.pluginTimeout = CloudFoundryUtils.DEFAULT_PLUGIN_TIMEOUT;
        }
        return this;
    }

    public boolean isResetIfExists() {
        return resetIfExists;
    }
}
