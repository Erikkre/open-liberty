/*******************************************************************************
 * Copyright (c) 2017, 2018 IBM Corporation and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************
 * Copyright © 2013 Antonin Stefanutti (antonin.stefanutti@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package io.astefanutti.metrics.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Vetoed;
import javax.inject.Inject;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.HitCounted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.ParallelCounted;
import org.eclipse.microprofile.metrics.annotation.Timed;

@ApplicationScoped
public class MetricResolver {

    @Inject
    protected MetricsExtension extension;

    @Inject
    protected MetricRegistry registry;

    @Inject
    protected MetricName metricName;

    public <E extends Member & AnnotatedElement> Of<Counted> counted(Class<?> topClass, E element) {
        return resolverOf(topClass, element, Counted.class);
    }

    public <E extends Member & AnnotatedElement> Of<HitCounted> hitCounted(Class<?> topClass, E element) {
        return resolverOf(topClass, element, HitCounted.class);
    }

    public <E extends Member & AnnotatedElement> Of<ParallelCounted> parallelCounted(Class<?> topClass, E element) {
        return resolverOf(topClass, element, ParallelCounted.class);
    }

    public Of<Gauge> gauge(Class<?> topClass, Method method) {
        return resolverOf(topClass, method, Gauge.class);
    }

    public <E extends Member & AnnotatedElement> Of<Metered> metered(Class<?> topClass, E element) {
        return resolverOf(topClass, element, Metered.class);
    }

    public <E extends Member & AnnotatedElement> Of<Timed> timed(Class<?> bean, E element) {
        return resolverOf(bean, element, Timed.class);
    }

    private <E extends Member & AnnotatedElement, T extends Annotation> Of<T> resolverOf(Class<?> bean, E element, Class<T> metric) {
        if (element.isAnnotationPresent(metric))
            return elementResolverOf(element, metric);
        else
            return beanResolverOf(element, metric, bean);
    }

    protected <E extends Member & AnnotatedElement, T extends Annotation> Of<T> elementResolverOf(E element, Class<T> metric) {

        T annotation = element.getAnnotation(metric);

        // See if we have the name cached
        String name = extension.getMetricNameForMember(element, annotation);

        boolean initialDiscovery = false;
        if (name == null) {
            name = metricName(element, metric, metricName(annotation), isMetricAbsolute(annotation));
            initialDiscovery = true;
        }

        MetadataBuilder metadataBuider = Metadata.builder().withName(name).withType(this.getType(annotation)).withUnit(this.getUnit(annotation)).withDescription(this.getDescription(annotation)).withDisplayName(this.getDisplayname(annotation));
        for (String tag : this.getTags(annotation)) {
            metadataBuider.addTag(tag);
        }

        if (getReusable(annotation)) {
            metadataBuider.reusable();
        } else {
            metadataBuider.notReusable();
        }

        // If the metric has been registered before (eg. metrics found in RequestScoped beans),
        // we don't need to worry about re-usable
        if (initialDiscovery) {
            Metadata existingMetadata = registry.getMetadata().get(name);
            if (existingMetadata != null && (existingMetadata.isReusable() == false || getReusable(annotation) == false)) {
                throw new IllegalArgumentException("Cannot reuse metric for " + name);
            }
        }
        return new DoesHaveMetric<>(annotation, name, metadataBuider, initialDiscovery);
    }

    protected <E extends Member & AnnotatedElement, T extends Annotation> Of<T> beanResolverOf(E element, Class<T> metric, Class<?> bean) {
        if (bean.isAnnotationPresent(metric)) {
            T annotation = bean.getAnnotation(metric);

            // See if we have the name cached
            String name = extension.getMetricNameForMember(element, annotation);

            boolean initialDiscovery = false;
            if (name == null) {
                name = metricName(bean, element, metric, metricName(annotation), isMetricAbsolute(annotation));
                initialDiscovery = true;
            }

            MetadataBuilder metadataBuider = Metadata.builder().withName(name).withType(this.getType(annotation)).withUnit(this.getUnit(annotation)).withDescription(this.getDescription(annotation)).withDisplayName(this.getDisplayname(annotation));
            for (String tag : this.getTags(annotation)) {
                metadataBuider.addTag(tag);
            }

            if (getReusable(annotation)) {
                metadataBuider.reusable();
            } else {
                metadataBuider.notReusable();
            }

            // If the metric has been registered before (eg. metrics found in RequestScoped beans),
            // we don't need to worry about re-usable
            if (initialDiscovery) {
                Metadata existingMetadata = registry.getMetadata().get(name);
                if (existingMetadata != null && (existingMetadata.isReusable() == false || getReusable(annotation) == false)) {
                    throw new IllegalArgumentException("Cannot reuse metric for " + name);
                }
            }

            return new DoesHaveMetric<>(annotation, name, metadataBuider, initialDiscovery);
        } else if (bean.getSuperclass() != null) {
            return beanResolverOf(element, metric, bean.getSuperclass());
        }
        return new DoesNotHaveMetric<>();
    }

    // TODO: should be grouped with the metric name strategy
    private <E extends Member & AnnotatedElement> String metricName(E element, Class<? extends Annotation> type, String name, boolean absolute) {
        String metric = name.isEmpty() ? defaultName(element, type) : metricName.of(name);
        return absolute ? metric : MetricRegistry.name(element.getDeclaringClass(), metric);
    }

    private <E extends Member & AnnotatedElement> String metricName(Class<?> bean, E element, Class<? extends Annotation> type, String name, boolean absolute) {
        String metric = name.isEmpty() ? bean.getSimpleName() : metricName.of(name);
        return absolute ? MetricRegistry.name(metric, defaultName(element, type)) : MetricRegistry.name(bean.getPackage().getName(), metric, defaultName(element, type));
    }

    private <E extends Member & AnnotatedElement> String defaultName(E element, Class<? extends Annotation> type) {
        return memberName(element);
    }

    // While the Member Javadoc states that the getName method should returns
    // the simple name of the underlying member or constructor, the FQN is returned
    // for constructors. See JDK-6294399:
    // http://bugs.java.com/view_bug.do?bug_id=6294399
    private String memberName(Member member) {
        if (member instanceof Constructor)
            return member.getDeclaringClass().getSimpleName();
        else
            return member.getName();
    }

    private String metricName(Annotation annotation) {
        if (Counted.class.isInstance(annotation))
            return ((Counted) annotation).name();
        else if (HitCounted.class.isInstance(annotation))
            return ((HitCounted) annotation).name();
        else if (ParallelCounted.class.isInstance(annotation))
            return ((ParallelCounted) annotation).name();
        else if (Gauge.class.isInstance(annotation))
            return ((Gauge) annotation).name();
        else if (Metered.class.isInstance(annotation))
            return ((Metered) annotation).name();
        else if (Timed.class.isInstance(annotation))
            return ((Timed) annotation).name();
        else
            throw new IllegalArgumentException("Unsupported Metrics forMethod [" + annotation.getClass().getName() + "]");
    }

    private boolean isMetricAbsolute(Annotation annotation) {
        if (extension.getParameters().contains(MetricsParameter.useAbsoluteName))
            return true;

        if (Counted.class.isInstance(annotation))
            return ((Counted) annotation).absolute();
        else if (HitCounted.class.isInstance(annotation))
            return ((HitCounted) annotation).absolute();
        else if (ParallelCounted.class.isInstance(annotation))
            return ((ParallelCounted) annotation).absolute();
        else if (Gauge.class.isInstance(annotation))
            return ((Gauge) annotation).absolute();
        else if (Metered.class.isInstance(annotation))
            return ((Metered) annotation).absolute();
        else if (Timed.class.isInstance(annotation))
            return ((Timed) annotation).absolute();
        else
            throw new IllegalArgumentException("Unsupported Metrics forMethod [" + annotation.getClass().getName() + "]");
    }

    private String[] getTags(Annotation annotation) {
        if (Counted.class.isInstance(annotation))
            return ((Counted) annotation).tags();
        else if (HitCounted.class.isInstance(annotation))
            return ((HitCounted) annotation).tags();
        else if (ParallelCounted.class.isInstance(annotation))
            return ((ParallelCounted) annotation).tags();
        else if (Gauge.class.isInstance(annotation))
            return ((Gauge) annotation).tags();
        else if (Metered.class.isInstance(annotation))
            return ((Metered) annotation).tags();
        else if (Timed.class.isInstance(annotation))
            return ((Timed) annotation).tags();
        else
            throw new IllegalArgumentException("Unsupported Metrics forMethod [" + annotation.getClass().getName() + "]");

    }

    private String getDisplayname(Annotation annotation) {
        if (Counted.class.isInstance(annotation))
            return ((Counted) annotation).displayName();
        else if (HitCounted.class.isInstance(annotation))
            return ((HitCounted) annotation).displayName();
        else if (ParallelCounted.class.isInstance(annotation))
            return ((ParallelCounted) annotation).displayName();
        else if (Gauge.class.isInstance(annotation))
            return ((Gauge) annotation).displayName();
        else if (Metered.class.isInstance(annotation))
            return ((Metered) annotation).displayName();
        else if (Timed.class.isInstance(annotation))
            return ((Timed) annotation).displayName();
        else
            throw new IllegalArgumentException("Unsupported Metrics forMethod [" + annotation.getClass().getName() + "]");

    }

    private String getDescription(Annotation annotation) {
        if (Counted.class.isInstance(annotation))
            return ((Counted) annotation).description();
        else if (HitCounted.class.isInstance(annotation))
            return ((HitCounted) annotation).description();
        else if (ParallelCounted.class.isInstance(annotation))
            return ((ParallelCounted) annotation).description();
        else if (Gauge.class.isInstance(annotation))
            return ((Gauge) annotation).description();
        else if (Metered.class.isInstance(annotation))
            return ((Metered) annotation).description();
        else if (Timed.class.isInstance(annotation))
            return ((Timed) annotation).description();
        else
            throw new IllegalArgumentException("Unsupported Metrics forMethod [" + annotation.getClass().getName() + "]");
    }

    private MetricType getType(Annotation annotation) {
        if (Counted.class.isInstance(annotation))
            return MetricType.COUNTER;
        else if (HitCounted.class.isInstance(annotation))
            return MetricType.HIT_COUNTER;
        else if (ParallelCounted.class.isInstance(annotation))
            return MetricType.PARALLEL_COUNTER;
        else if (Gauge.class.isInstance(annotation))
            return MetricType.GAUGE;
        else if (Metered.class.isInstance(annotation))
            return MetricType.METERED;
        else if (Timed.class.isInstance(annotation))
            return MetricType.TIMER;
        else
            throw new IllegalArgumentException("Unsupported Metrics forMethod [" + annotation.getClass().getName() + "]");
    }

    private String getUnit(Annotation annotation) {
        if (Counted.class.isInstance(annotation))
            return ((Counted) annotation).unit();
        else if (HitCounted.class.isInstance(annotation))
            return ((HitCounted) annotation).unit();
        else if (ParallelCounted.class.isInstance(annotation))
            return ((ParallelCounted) annotation).unit();
        else if (Gauge.class.isInstance(annotation))
            return ((Gauge) annotation).unit();
        else if (Metered.class.isInstance(annotation))
            return ((Metered) annotation).unit();
        else if (Timed.class.isInstance(annotation))
            return ((Timed) annotation).unit();
        else
            throw new IllegalArgumentException("Unsupported Metrics forMethod [" + annotation.getClass().getName() + "]");
    }

    private boolean getReusable(Annotation annotation) {
        if (Counted.class.isInstance(annotation))
            return ((Counted) annotation).reusable();
        else if (HitCounted.class.isInstance(annotation))
            return ((HitCounted) annotation).reusable();
        else if (ParallelCounted.class.isInstance(annotation))
            return ((ParallelCounted) annotation).reusable();
        else if (Gauge.class.isInstance(annotation))
            return false;
        else if (Metered.class.isInstance(annotation))
            return ((Metered) annotation).reusable();
        else if (Timed.class.isInstance(annotation))
            return ((Timed) annotation).reusable();
        else
            throw new IllegalArgumentException("Unsupported Metrics forMethod [" + annotation.getClass().getName() + "]");
    }

    public interface Of<T extends Annotation> {

        boolean isPresent();

        String metricName();

        Metadata metadata();

        T metricAnnotation();

        boolean isInitialDiscovery();

        MetadataBuilder metadataBuilder();
    }

    protected static final class DoesHaveMetric<T extends Annotation> implements Of<T> {

        private final T annotation;

        private final String name;

        private final MetadataBuilder metadataBuilder;

        private final boolean initialDiscovery;

        private DoesHaveMetric(T annotation, String name, MetadataBuilder metadataBuilder, boolean initialDiscovery) {
            this.annotation = annotation;
            this.name = name;
            this.metadataBuilder = metadataBuilder;
            this.initialDiscovery = initialDiscovery;
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public String metricName() {
            return name;
        }

        @Override
        public T metricAnnotation() {
            return annotation;
        }

        @Override
        public Metadata metadata() {
            return metadataBuilder.build();
        }

        @Override
        public boolean isInitialDiscovery() {
            return initialDiscovery;
        }

        @Override
        public MetadataBuilder metadataBuilder() {
            return metadataBuilder;
        }
    }

    @Vetoed
    protected static final class DoesNotHaveMetric<T extends Annotation> implements Of<T> {

        private DoesNotHaveMetric() {}

        @Override
        public boolean isPresent() {
            return false;
        }

        @Override
        public String metricName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public T metricAnnotation() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Metadata metadata() {
            return null;
        }

        @Override
        public boolean isInitialDiscovery() {
            return false;
        }

        @Override
        public MetadataBuilder metadataBuilder() {
            return null;
        }
    }
}
