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

package com.microsoft.applicationinsights.internal.agent;

import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

import com.microsoft.applicationinsights.agent.internal.coresync.AgentNotificationsHandler;
import com.microsoft.applicationinsights.agent.internal.coresync.InstrumentedClassType;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.agent.internal.coresync.impl.ImplementationsCoordinator;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.internal.schemav2.DependencyKind;
import com.microsoft.applicationinsights.internal.util.ThreadLocalCleaner;
import com.microsoft.applicationinsights.telemetry.Duration;
import com.microsoft.applicationinsights.telemetry.ExceptionTelemetry;
import com.microsoft.applicationinsights.telemetry.RemoteDependencyTelemetry;

/**
 * The Core's implementation: the methods are called for instrumented methods.
 * The implementation can measure time in nano seconds, fetch Sql/Http data and report exceptions
 *
 * Created by gupele on 5/7/2015.
 */
final class CoreAgentNotificationsHandler implements AgentNotificationsHandler {

    /**
     * The class holds the data gathered on a method
     */
    private static class MethodData {
        public String name;
        public Object[] arguments;
        public long interval;
        public InstrumentedClassType type;
        public Object result;
    }

    private static class ThreadData {
        public final LinkedList<MethodData> methods = new LinkedList<MethodData>();
    }

    static final class ThreadLocalData extends ThreadLocal<ThreadData> {
        private ThreadData threadData;

        @Override
        protected ThreadData initialValue() {
            threadData = new ThreadData();
            return threadData;
        }
    };

    private final ThreadLocalCleaner cleaner = new ThreadLocalCleaner() {
        @Override
        public void clean() {
            threadDataThreadLocal.remove();
        }
    };

    private ThreadLocalData threadDataThreadLocal = new ThreadLocalData();

    private TelemetryClient telemetryClient = new TelemetryClient();

    private final String name;

    public ThreadLocalCleaner getCleaner() {
        return cleaner;
    }

    public CoreAgentNotificationsHandler(String name) {
        this.name = name;
    }

    @Override
    public void exceptionCaught(String classAndMethodNames, Throwable throwable) {
        try {
            if (throwable instanceof Exception) {
                telemetryClient.trackException((Exception)throwable);
            }
        } catch (Throwable t) {
        }
    }

    @Override
    public void httpMethodStarted(String classAndMethodNames, String url) {
        startMethod(InstrumentedClassType.HTTP, name, url);
    }

    @Override
    public void sqlStatementExecuteQueryPossibleQueryPlan(String name, Statement statement, String sqlStatement) {
        startSqlMethod(statement, sqlStatement, null);
    }

    @Override
    public void preparedStatementMethodStarted(String classAndMethodNames, PreparedStatement statement, String sqlStatement, Object[] args) {
        startSqlMethod(statement, sqlStatement, args);
    }

    @Override
    public void sqlStatementMethodStarted(String name, Statement statement, String sqlStatement) {
        startSqlMethod(statement, sqlStatement, null);
    }

    @Override
    public void preparedStatementExecuteBatchMethodStarted(String classAndMethodNames, PreparedStatement statement, String sqlStatement, int batchCounter) {
        final String batchData = String.format("Batch of %d", batchCounter);
        startSqlMethod(statement, sqlStatement, new Object[]{batchData});
    }

    public String getName() {
        return name;
    }

    @Override
    public void methodStarted(String name) {
        startMethod(InstrumentedClassType.OTHER, name, new String[]{});
    }

    @Override
    public void methodFinished(String name, Throwable throwable) {
        if (!finalizeMethod(null, throwable)) {
            InternalLogger.INSTANCE.error("Agent has detected a 'Finish' method '%s' with exception '%s' event without a 'Start'",
                    name, throwable == null ? "unknown" : throwable.getClass().getName());
        }
    }

    @Override
    public void methodFinished(String name) {
        if (!finalizeMethod(null, null)) {
            InternalLogger.INSTANCE.error("Agent has detected a 'Finish' method ('%s') event without a 'Start'", name);
        }
    }

    @Override
    public void methodFinished(String classAndMethodNames, long deltaInNS, Object[] args, Throwable throwable) {
        long durationInMS =nanoToMilliseconds(deltaInNS);
        Duration duration = new Duration(durationInMS);
        RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry(classAndMethodNames, null, duration, throwable == null);
        telemetry.setDependencyKind(DependencyKind.Other);

        if (args != null) {
            String argsAsString = new ArgsFormatter().format(args);
            telemetry.getContext().getProperties().put("Args", argsAsString);
        }

        InternalLogger.INSTANCE.trace("Sending RDD event for '%s', duration=%s ms", classAndMethodNames, durationInMS);

        telemetryClient.track(telemetry);
        if (throwable != null) {
            ExceptionTelemetry exceptionTelemetry = new ExceptionTelemetry(throwable);
            telemetryClient.track(exceptionTelemetry);
        }
    }

    private void startSqlMethod(Statement statement, String sqlStatement, Object[] additionalArgs) {

        try {
            Connection connection = null;
            DatabaseMetaData metaData;
            String url = null;
            if (statement != null) {
                try {
                    connection = statement.getConnection();
                    if (connection != null) {
                        metaData = connection.getMetaData();

                        if (metaData != null) {
                            url = metaData.getURL();
                        }
                    }
                } catch (Throwable t) {
                    url = "jdbc:Unknown DB URL (failed to fetch from connection)";
                }
            }

            Object[] sqlMetaData;
            if (additionalArgs == null) {
                sqlMetaData = new Object[] {url, sqlStatement, connection};
            } else {
                sqlMetaData = new Object[] {url, sqlStatement, connection, additionalArgs};
            }
            startSqlMethod(InstrumentedClassType.SQL, name, sqlMetaData);
            ThreadData localData = threadDataThreadLocal.get();

        } catch (Throwable e) {
        }
    }

    private void startMethod(InstrumentedClassType type, String name, String... arguments) {
        long start = System.nanoTime();

        ThreadData localData = threadDataThreadLocal.get();
        MethodData methodData = new MethodData();
        methodData.interval = start;
        methodData.type = type;
        methodData.arguments = arguments;
        methodData.name = name;
        localData.methods.addFirst(methodData);
    }

    private void startSqlMethod(InstrumentedClassType type, String name, Object... arguments) {
        long start = System.nanoTime();

        ThreadData localData = threadDataThreadLocal.get();
        MethodData methodData = new MethodData();
        methodData.interval = start;
        methodData.type = type;
        methodData.arguments = arguments;
        methodData.name = name;
        localData.methods.addFirst(methodData);
    }

    private boolean finalizeMethod(Object result, Throwable throwable) {
        long finish = System.nanoTime();

        ThreadData localData = threadDataThreadLocal.get();
        if (localData.methods == null || localData.methods.isEmpty()) {
            return false;
        }

        MethodData methodData = localData.methods.removeFirst();
        if (methodData == null) {
            return true;
        }

        methodData.interval = finish - methodData.interval;
        methodData.result = result;

        report(methodData, throwable);

        return true;
    }

    private void report(MethodData methodData, Throwable throwable) {
        switch (methodData.type) {
            case SQL:
                sendSQLTelemetry(methodData, throwable);
                break;

            case HTTP:
                sendHTTPTelemetry(methodData, throwable);
                break;

            default:
                sendInstrumentationTelemetry(methodData, throwable);
                break;
        }
    }

    private void sendInstrumentationTelemetry(MethodData methodData, Throwable throwable) {
        Duration duration = new Duration(nanoToMilliseconds(methodData.interval));
        RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry(methodData.name, null, duration, throwable == null);
        telemetry.setDependencyKind(DependencyKind.Other);

        InternalLogger.INSTANCE.trace("Sending RDD event for '%s'", methodData.name);

        telemetryClient.track(telemetry);
        if (throwable != null) {
            ExceptionTelemetry exceptionTelemetry = new ExceptionTelemetry(throwable);
            telemetryClient.track(exceptionTelemetry);
        }
    }

    private void sendHTTPTelemetry(MethodData methodData, Throwable throwable) {
        if (methodData.arguments != null && methodData.arguments.length == 1) {
            String url = methodData.arguments[0].toString();
            long durationInMilliSeconds = nanoToMilliseconds(methodData.interval);
            Duration duration = new Duration(durationInMilliSeconds);

            InternalLogger.INSTANCE.trace("Sending HTTP RDD event, URL: '%s', duration=%s ms", url, durationInMilliSeconds);

            RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry(url, null, duration, throwable == null);
            telemetry.setDependencyKind(DependencyKind.Http);
            telemetryClient.trackDependency(telemetry);
            if (throwable != null) {
                ExceptionTelemetry exceptionTelemetry = new ExceptionTelemetry(throwable);
                telemetryClient.track(exceptionTelemetry);
            }
        }
    }

    private void sendSQLTelemetry(MethodData methodData, Throwable throwable) {
        if (methodData.arguments != null && methodData.arguments.length >= 3 && methodData.arguments[1] != null) {
            try {
                String dependencyName = null;
                if (methodData.arguments[0] != null) {
                    dependencyName = methodData.arguments[0].toString();
                }
                String commandName = null;
                if (methodData.arguments[1] == null) {
                    return;
                }

                commandName = methodData.arguments[1].toString();
                long durationInMilliSeconds = nanoToMilliseconds(methodData.interval);
                Duration duration = new Duration(durationInMilliSeconds);

                RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry(
                        dependencyName,
                        commandName,
                        duration,
                        throwable == null);
                telemetry.setDependencyKind(DependencyKind.SQL);

                StringBuilder sb = null;
                if (methodData.arguments.length > 3) {
                    sb = formatAdditionalSqlArguments(methodData);
                    if (sb != null) {
                        telemetry.getContext().getProperties().put("Args", sb.toString());
                    }
                } else {
                    if (durationInMilliSeconds > ImplementationsCoordinator.INSTANCE.getQueryPlanThresholdInMS()) {
                        sb = fetchExplainQuery(commandName, methodData.arguments[2]);
                        if (sb != null) {
                            telemetry.getContext().getProperties().put("Query Plan", sb.toString());
                        }
                    }
                }

                InternalLogger.INSTANCE.trace("Sending Sql RDD event for '%s', command: '%s', duration=%s ms", dependencyName, commandName, durationInMilliSeconds);

                telemetryClient.track(telemetry);
                if (throwable != null) {
                    InternalLogger.INSTANCE.trace("Sending Sql exception");
                    ExceptionTelemetry exceptionTelemetry = new ExceptionTelemetry(throwable);
                    telemetryClient.track(exceptionTelemetry);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private static long nanoToMilliseconds(long nanoSeconds) {
        return nanoSeconds / 1000000;
    }

    private StringBuilder formatAdditionalSqlArguments(MethodData methodData) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(" [");
            Object[] args = (Object[])methodData.arguments[3];
            if (args != null && args.length > 0) {
                for (Object arg : args) {
                    if (arg == null) {
                        sb.append("null,");
                    } else {
                        sb.append(arg.toString());
                        sb.append(',');
                    }
                }
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append(']');
            return sb;
        } catch (Throwable t) {
            return null;
        }
    }

    private StringBuilder fetchExplainQuery(String commandName, Object object) {
        StringBuilder explainSB = null;
        Statement explain = null;
        ResultSet rs = null;
        try {
            if (commandName.startsWith("SELECT ")) {
                Connection connection = (Connection)object;
                if (connection == null) {
                    return explainSB;
                }
                explain = connection.createStatement();
                rs = explain.executeQuery("EXPLAIN " + commandName);
                explainSB = new StringBuilder();
                while (rs.next()) {
                    explainSB.append('[');
                    int columns = rs.getMetaData().getColumnCount();
                    if (columns == 1) {
                        explainSB.append(rs.getString(1));
                    } else {
                        for (int i1 = 1; i1 < rs.getMetaData().getColumnCount(); ++i1) {
                            explainSB.append(rs.getMetaData().getColumnName(i1));
                            explainSB.append(':');
                            Object obj = rs.getObject(i1);
                            explainSB.append(obj == null ? "" : obj.toString());
                            explainSB.append(',');
                        }
                        explainSB.deleteCharAt(explainSB.length() - 1);
                    }
                    explainSB.append("],");
                }
                explainSB.deleteCharAt(explainSB.length() - 1);
            }
        } catch (Throwable t) {
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                }
            }
            if (explain != null) {
                try {
                    explain.close();
                } catch (SQLException e) {
                }
            }
        }

        return explainSB;
    }
}
