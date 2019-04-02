/*******************************************************************************
 * Copyright (c) 2018,2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.concurrent.mp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.enterprise.concurrent.ManagedExecutorService;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import com.ibm.websphere.ras.annotation.Trivial;
import com.ibm.ws.concurrency.policy.ConcurrencyPolicy;
import com.ibm.ws.concurrent.service.AbstractManagedExecutorService;
import com.ibm.ws.threading.PolicyExecutor;
import com.ibm.wsspi.application.lifecycle.ApplicationRecycleComponent;
import com.ibm.wsspi.application.lifecycle.ApplicationRecycleCoordinator;
import com.ibm.wsspi.kernel.service.utils.AtomicServiceReference;
import com.ibm.wsspi.resource.ResourceFactory;
import com.ibm.wsspi.threadcontext.WSContextService;

/**
 * Super class of ManagedExecutorServiceImpl to be used with Java 8 and above.
 * This class provides implementation of the MicroProfile Context Propagation methods.
 * These methods can be collapsed into ManagedExecutorServiceImpl once there is
 * no longer a need for OpenLiberty to support Java 7.
 */
@Component(configurationPid = "com.ibm.ws.concurrent.managedExecutorService", configurationPolicy = ConfigurationPolicy.REQUIRE,
           service = { ExecutorService.class, ManagedExecutor.class, ManagedExecutorService.class, ResourceFactory.class, ApplicationRecycleComponent.class },
           reference = @Reference(name = "ApplicationRecycleCoordinator", service = ApplicationRecycleCoordinator.class),
           property = { "creates.objectClass=java.util.concurrent.ExecutorService",
                        "creates.objectClass=javax.enterprise.concurrent.ManagedExecutorService",
                        "creates.objectClass=org.eclipse.microprofile.context.ManagedExecutor" })
public class ManagedExecutorImpl extends AbstractManagedExecutorService implements ManagedExecutor {
    private final boolean allowLifeCycleMethods;

    /**
     * Hash code for this instance.
     */
    private final int hash;

    /**
     * Unique name for this instance.
     */
    private final String name;

    /**
     * Constructor for OSGi code path.
     */
    @Trivial
    public ManagedExecutorImpl() {
        super();
        allowLifeCycleMethods = false;
        hash = super.hashCode();
        name = "ManagedExecutor@" + Integer.toHexString(hash);
    }

    /**
     * Constructor for MicroProfile ManagedExecutorBuilder.
     */
    public ManagedExecutorImpl(String name, int hash, PolicyExecutor policyExecutor, WSContextService mpThreadContext,
                               AtomicServiceReference<com.ibm.wsspi.threadcontext.ThreadContextProvider> tranContextProviderRef) {
        super(name, policyExecutor, mpThreadContext, tranContextProviderRef);
        allowLifeCycleMethods = true;
        this.hash = hash;
        this.name = name;
    }

    @Activate
    @Override
    @Trivial
    protected void activate(ComponentContext context, Map<String, Object> properties) {
        super.activate(context, properties);
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return allowLifeCycleMethods //
                        ? getNormalPolicyExecutor().awaitTermination(timeout, unit) //
                        : super.awaitTermination(timeout, unit);
    }

    @Override
    public <U> CompletableFuture<U> completedFuture(U value) {
        return ManagedCompletableFuture.completedFuture(value, this);
    }

    @Override
    public <U> CompletionStage<U> completedStage(U value) {
        return ManagedCompletableFuture.completedStage(value, this);
    }

    @Deactivate
    @Override
    @Trivial
    protected void deactivate(ComponentContext context) {
        super.deactivate(context);
    }

    @Override
    public <U> CompletableFuture<U> failedFuture(Throwable ex) {
        return ManagedCompletableFuture.failedFuture(ex, this);
    }

    @Override
    public <U> CompletionStage<U> failedStage(Throwable ex) {
        return ManagedCompletableFuture.failedStage(ex, this);
    }

    @Override
    @Trivial
    public int hashCode() {
        return hash;
    }

    @Override
    @Trivial
    public boolean isShutdown() {
        return allowLifeCycleMethods //
                        ? getNormalPolicyExecutor().isShutdown() //
                        : super.isShutdown();
    }

    @Override
    @Trivial
    public boolean isTerminated() {
        return allowLifeCycleMethods //
                        ? getNormalPolicyExecutor().isTerminated() //
                        : super.isTerminated();
    }

    @Override
    @Modified
    @Trivial
    protected void modified(final ComponentContext context, Map<String, Object> properties) {
        super.modified(context, properties);
    }

    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        if (ManagedCompletableFuture.JAVA8)
            return new ManagedCompletableFuture<U>(new CompletableFuture<U>(), this, null);
        else
            return new ManagedCompletableFuture<U>(this, null);
    }

    @Override
    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return ManagedCompletableFuture.runAsync(runnable, this);
    }

    @Override
    @Reference(policy = ReferencePolicy.DYNAMIC, target = "(id=unbound)")
    @Trivial
    protected void setConcurrencyPolicy(ConcurrencyPolicy svc) {
        super.setConcurrencyPolicy(svc);
    }

    @Override
    @Reference(policy = ReferencePolicy.DYNAMIC, target = "(id=unbound)")
    @Trivial
    protected void setContextService(ServiceReference<WSContextService> ref) {
        super.setContextService(ref);
    }

    @Override
    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL, target = "(id=unbound)")
    @Trivial
    protected void setLongRunningPolicy(ConcurrencyPolicy svc) {
        super.setLongRunningPolicy(svc);
    }

    @Override
    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL, target = "(component.name=com.ibm.ws.transaction.context.provider)")
    @Trivial
    protected void setTransactionContextProvider(ServiceReference<com.ibm.wsspi.threadcontext.ThreadContextProvider> ref) {
        super.setTransactionContextProvider(ref);
    }

    @Override
    @Trivial
    public final void shutdown() {
        if (allowLifeCycleMethods)
            getNormalPolicyExecutor().shutdown();
        else
            super.shutdown();
    }

    @Override
    @Trivial
    public final List<Runnable> shutdownNow() {
        return allowLifeCycleMethods //
                        ? getNormalPolicyExecutor().shutdownNow() //
                        : super.shutdownNow();
    }

    @Override
    public <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return ManagedCompletableFuture.supplyAsync(supplier, this);
    }

    @Override
    @Trivial
    public String toString() {
        return name;
    }

    @Override
    @Trivial
    protected void unsetConcurrencyPolicy(ConcurrencyPolicy svc) {
        super.unsetConcurrencyPolicy(svc);
    }

    @Override
    @Trivial
    protected void unsetContextService(ServiceReference<WSContextService> ref) {
        super.unsetContextService(ref);
    }

    @Override
    @Trivial
    protected void unsetLongRunningPolicy(ConcurrencyPolicy svc) {
        super.unsetLongRunningPolicy(svc);
    }

    @Override
    @Trivial
    protected void unsetTransactionContextProvider(ServiceReference<com.ibm.wsspi.threadcontext.ThreadContextProvider> ref) {
        super.unsetTransactionContextProvider(ref);
    }
}
