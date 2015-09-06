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

package com.microsoft.applicationinsights.channel.concrete.inprocess;

import com.microsoft.applicationinsights.internal.channel.TelemetriesTransmitter;
import com.microsoft.applicationinsights.internal.channel.TransmissionOutput;
import com.microsoft.applicationinsights.internal.channel.TransmitterFactory;
import com.microsoft.applicationinsights.internal.channel.TransmissionDispatcher;
import com.microsoft.applicationinsights.internal.channel.TransmissionsLoader;

import com.microsoft.applicationinsights.internal.channel.common.*;

/**
 * Created by gupele on 1/15/2015.
 */
final class InProcessTelemetryChannelFactory implements TransmitterFactory {
    @Override
    public TelemetriesTransmitter create(String endpoint, String maxTransmissionStorageCapacity) {
        final TransmissionPolicyManager transmissionPolicyManager = new TransmissionPolicyManager();

        // An active object with the network sender
        TransmissionNetworkOutput actualNetworkSender = TransmissionNetworkOutput.create(endpoint, transmissionPolicyManager);

        TransmissionPolicyStateFetcher stateFetcher = transmissionPolicyManager.getTransmissionPolicyState();

        TransmissionOutput networkSender = new ActiveTransmissionNetworkOutput(actualNetworkSender, stateFetcher);

        // An active object with the file system sender
        TransmissionFileSystemOutput fileSystemSender = new TransmissionFileSystemOutput(null, maxTransmissionStorageCapacity);
        TransmissionOutput activeFileSystemOutput = new ActiveTransmissionFileSystemOutput(fileSystemSender, stateFetcher);

        // The dispatcher works with the two active senders
        TransmissionDispatcher dispatcher = new NonBlockingDispatcher(new TransmissionOutput[] {networkSender, activeFileSystemOutput});
        actualNetworkSender.setTransmissionDispatcher(dispatcher);

        // The loader works with the file system loader as the active one does
        TransmissionsLoader transmissionsLoader = new ActiveTransmissionLoader(fileSystemSender, stateFetcher, dispatcher);

        // The Transmitter manage all
        TelemetriesTransmitter telemetriesTransmitter = new TransmitterImpl(dispatcher, new GzipTelemetrySerializer(), transmissionsLoader);

        return telemetriesTransmitter;
    }
}
