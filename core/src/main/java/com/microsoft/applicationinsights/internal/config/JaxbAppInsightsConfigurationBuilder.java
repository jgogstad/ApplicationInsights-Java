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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.google.common.base.Strings;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;

/**
 * The JAXB implementation of the {@link com.microsoft.applicationinsights.internal.config.AppInsightsConfigurationBuilder}
 *
 * Created by gupele on 3/15/2015.
 */
class JaxbAppInsightsConfigurationBuilder implements AppInsightsConfigurationBuilder {
    @Override
    public ApplicationInsightsXmlConfiguration build(InputStream resourceFile) {
        if (resourceFile == null) {
            return null;
        }

        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(ApplicationInsightsXmlConfiguration.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            ApplicationInsightsXmlConfiguration applicationInsights = (ApplicationInsightsXmlConfiguration)unmarshaller.unmarshal(resourceFile);

            return applicationInsights;
        } catch (JAXBException e) {
            if (e.getCause() != null) {
                InternalLogger.INSTANCE.logAlways(InternalLogger.LoggingLevel.ERROR, "Failed to parse configuration file: '%s'", e.getCause().getMessage());
            } else {
                InternalLogger.INSTANCE.logAlways(InternalLogger.LoggingLevel.ERROR, "Failed to parse configuration file: '%s'", e.getMessage());
            }
        } finally {
            try {
                resourceFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }
}

