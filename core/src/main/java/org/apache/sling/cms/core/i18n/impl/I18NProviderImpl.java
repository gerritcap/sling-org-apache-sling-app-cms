/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.cms.core.i18n.impl;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.cms.i18n.I18NDictionary;
import org.apache.sling.cms.i18n.I18NProvider;
import org.apache.sling.commons.osgi.Order;
import org.apache.sling.commons.osgi.ServiceUtil;
import org.apache.sling.i18n.DefaultLocaleResolver;
import org.apache.sling.i18n.LocaleResolver;
import org.apache.sling.i18n.RequestLocaleResolver;
import org.apache.sling.i18n.ResourceBundleProvider;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

@SuppressWarnings("deprecation")
@Component(service = I18NProvider.class)
public class I18NProviderImpl implements I18NProvider {

    private final DefaultLocaleResolver defaultLocaleResolver = new DefaultLocaleResolver();

    private volatile LocaleResolver localeResolver = defaultLocaleResolver;

    private volatile RequestLocaleResolver requestLocaleResolver = defaultLocaleResolver;

    private final Map<Object, ResourceBundleProvider> providers = new TreeMap<>();

    private volatile ResourceBundleProvider[] sortedProviders = new ResourceBundleProvider[0];

    @Override
    public I18NDictionary getDictionary(SlingHttpServletRequest request) {
        List<Locale> locales = requestLocaleResolver.resolveLocale(request);

        CombinedBundleProvider cbp = new CombinedBundleProvider();
        ResourceBundle bundle = null;
        for (Locale locale : locales) {
            bundle = cbp.getResourceBundle(locale);
            if (bundle != null) {
                break;
            }
        }
        return new I18NDictionaryImpl(bundle);
    }

    @Override
    public I18NDictionary getDictionary(ResourceResolver resolver) {

        // TODO: make the locale a user field
        Locale locale = Locale.ENGLISH;

        CombinedBundleProvider cbp = new CombinedBundleProvider();
        return new I18NDictionaryImpl(cbp.getResourceBundle(locale));
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    protected void bindLocaleResolver(final LocaleResolver resolver) {
        this.localeResolver = resolver;
    }

    protected void unbindLocaleResolver(final LocaleResolver resolver) {
        if (this.localeResolver == resolver) {
            this.localeResolver = defaultLocaleResolver;
        }
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    protected void bindRequestLocaleResolver(final RequestLocaleResolver resolver) {
        this.requestLocaleResolver = resolver;
    }

    protected void unbindRequestLocaleResolver(final RequestLocaleResolver resolver) {
        if (this.requestLocaleResolver == resolver) {
            this.requestLocaleResolver = defaultLocaleResolver;
        }
    }

    @Reference(service = ResourceBundleProvider.class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    protected void bindResourceBundleProvider(final ResourceBundleProvider provider, final Map<String, Object> props) {
        synchronized (this.providers) {
            this.providers.put(ServiceUtil.getComparableForServiceRanking(props, Order.ASCENDING), provider);
            this.sortedProviders = this.providers.values().toArray(new ResourceBundleProvider[this.providers.size()]);
        }
    }

    protected void unbindResourceBundleProvider(final ResourceBundleProvider provider,
            final Map<String, Object> props) {
        synchronized (this.providers) {
            this.providers.remove(ServiceUtil.getComparableForServiceRanking(props, Order.ASCENDING));
            this.sortedProviders = this.providers.values().toArray(new ResourceBundleProvider[this.providers.size()]);
        }
    }

    // ---------- internal -----------------------------------------------------

    /**
     * Provider that goes through a list of registered providers and takes the first
     * non-null responses
     */
    private class CombinedBundleProvider implements ResourceBundleProvider {

        @Override
        public Locale getDefaultLocale() {
            // ask all registered providers, use the first one that returns
            final ResourceBundleProvider[] sp = sortedProviders;
            for (int i = sp.length - 1; i >= 0; i--) {
                final ResourceBundleProvider provider = sp[i];
                final Locale locale = provider.getDefaultLocale();
                if (locale != null) {
                    return locale;
                }
            }
            return null;
        }

        @Override
        public ResourceBundle getResourceBundle(final Locale locale) {
            // ask all registered providers, use the first one that returns
            final ResourceBundleProvider[] sp = sortedProviders;
            for (int i = sp.length - 1; i >= 0; i--) {
                final ResourceBundleProvider provider = sp[i];
                final ResourceBundle bundle = provider.getResourceBundle(locale);
                if (bundle != null) {
                    return bundle;
                }
            }
            return null;
        }

        @Override
        public ResourceBundle getResourceBundle(final String baseName, final Locale locale) {
            // ask all registered providers, use the first one that returns
            final ResourceBundleProvider[] sp = sortedProviders;
            for (int i = sp.length - 1; i >= 0; i--) {
                final ResourceBundleProvider provider = sp[i];
                final ResourceBundle bundle = provider.getResourceBundle(baseName, locale);
                if (bundle != null) {
                    return bundle;
                }
            }
            return null;
        }
    }

}
