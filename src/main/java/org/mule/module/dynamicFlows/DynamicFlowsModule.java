/**
 * Mule Development Kit
 * Copyright 2010-2011 (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
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
 */

/**
 * This file was automatically generated by the Mule Development Kit
 */
package org.mule.module.dynamicFlows;

import org.mule.DefaultMuleEvent;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.annotations.Module;
import org.mule.api.annotations.Processor;
import org.mule.api.config.ConfigurationBuilder;
import org.mule.api.context.MuleContextAware;
import org.mule.api.context.MuleContextBuilder;
import org.mule.api.context.MuleContextFactory;
import org.mule.api.context.notification.MuleContextNotificationListener;
import org.mule.config.ConfigResource;
import org.mule.config.spring.SpringXmlConfigurationBuilder;
import org.mule.construct.Flow;
import org.mule.context.DefaultMuleContextBuilder;
import org.mule.context.DefaultMuleContextFactory;
import org.mule.context.notification.MuleContextNotification;
import org.mule.context.notification.NotificationException;
import org.mule.session.DefaultMuleSession;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.management.RuntimeErrorException;
import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * Adds, removes and runs dynamic Flows
 *
 * @author MuleSoft, Inc.
 */
@Module(name="dynamicflows", schemaVersion="1.0")
public class DynamicFlowsModule implements ApplicationContextAware, MuleContextNotificationListener<MuleContextNotification>, MuleContextAware
{
    private ApplicationContext applicationContext;
    private MuleContext mainContext;
    private Map<String,MuleContext> contexts = Collections.synchronizedMap(new HashMap<String, MuleContext>());

    /**
     * Adds a dynamic context to the main application.
     *
     * {@sample.xml ../../../doc/DynamicFlows-connector.xml.sample dynamicflows:add}
     *
     * @param contextName The context identifier
     * @param configs The configuration files of the context that is going to be added.
     */
    @Processor
    public void add(String contextName, List<String> configs)
    {
        checkExistenceOf(contextName);

        try{
            MuleContextFactory muleContextFactory = new DefaultMuleContextFactory();
            List<ConfigurationBuilder> builders = new ArrayList<ConfigurationBuilder>();
            builders.add( springApplicationBuilderUsing(configs));
            MuleContextBuilder contextBuilder = new DefaultMuleContextBuilder();
            MuleContext context = muleContextFactory.createMuleContext(builders, contextBuilder);

            context.start();

            if (context.isStarted())
                contexts.put(contextName, context);
        }
        catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes a context from the main application
     *
     *{@sample.xml ../../../doc/DynamicFlows-connector.xml.sample dynamicflows:remove}
     *
     * @param contextName The context identifier
     */
    @Processor
    public void remove(String contextName)
    {
        if ( contexts.containsKey(contextName) )
        {
            try {
                contexts.get(contextName).stop();
                contexts.get(contextName).dispose();
            } catch (MuleException e) {
                throw new RuntimeException(e);
            }

            contexts.remove(contextName);
        }
    }


    /**
     * Runs the flow added dynamically using a VM transport (request-response)
     *
     * {@sample.xml ../../../doc/DynamicFlows-connector.xml.sample dynamicflows:run}
     *
     * @param contextName The context identifier
     * @param flowName The flow identifier
     * @param message The flow's payload
     * @return The mule Message
     */
    @Processor
    public MuleMessage run(String contextName, String flowName, MuleMessage message) throws MuleException
    {
        MuleContext context = getContextWith(contextName);
        Flow flow = getFlowUsing(flowName,context);
        return flow.process(new DefaultMuleEvent(message, MessageExchangePattern.REQUEST_RESPONSE, new DefaultMuleSession(flow, context) )).getMessage();
    }


    /**
     * Runs the flow added dynamically using a vm inbound
     *
     * {@sample.xml ../../../doc/DynamicFlows-connector.xml.sample dynamicflows:vmRun}
     *
     * @param contextName The context identifier
     * @param flowName The flow identifier with the VM inbound
     * @param message The flow's payload
     * @return The mule Message
     */
    @Processor
    public MuleMessage vmRun(String contextName, String flowName, MuleMessage message) throws MuleException
    {
        MuleContext context = getContextWith(contextName);
        return context.getClient().send("vm://" + flowName, message);
    }

    /**
     * Sets the parent application context.
     *
     * @see{org.springframework.context.ApplicationContextAware}
     * @param applicationContext
     * @throws BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        this.applicationContext = applicationContext;

    }

    @Override
    public void onNotification(MuleContextNotification notification) {
        if ( notification.getAction() == MuleContextNotification.CONTEXT_STOPPED )
        {
            stopAndDisposeContexts();
        }
    }

    @Override
    public void setMuleContext(MuleContext context) {
        this.mainContext = context;
        registerAsListener();
    }

    private SpringXmlConfigurationBuilder springApplicationBuilderUsing(List<String> payload) {
        ConfigResource[] resources = createResources(payload);
        SpringXmlConfigurationBuilder springXmlConfigurationBuilder = new SpringXmlConfigurationBuilder(resources);
        springXmlConfigurationBuilder.setParentContext(this.applicationContext);
        return springXmlConfigurationBuilder;
    }

    private ConfigResource[] createResources(List<String> muleConfigs) {

        ConfigResource[] configResources = new ConfigResource[muleConfigs.size()];

        Iterator<String> it = muleConfigs.iterator();
        for (int i=0; it.hasNext(); i++) {
            String muleConfig = it.next();
            configResources[i] = new ConfigResource("context"+i+".xml", new ByteArrayInputStream(muleConfig.getBytes()));
        }

        return configResources;
    }

    private Flow getFlowUsing(String flowName, MuleContext context) {
        Flow flow = (Flow) context.getRegistry().lookupFlowConstruct(flowName);
        if (flow == null) throw new RuntimeErrorException(new Error("flow does not exist"));
        return flow;
    }

    private MuleContext getContextWith(String contextName) {
        MuleContext context = contexts.get(contextName);

        if (context == null ) throw new RuntimeErrorException(new Error("Context does not exist"));
        return context;
    }

    private void checkExistenceOf(String contextName) {
        if (contexts.containsKey(contextName))
            throw new RuntimeErrorException(new Error("Context already exists"));
    }



    private void stopAndDisposeContexts() {
        List<String> unstoppedContexts = new ArrayList<String>();
        for ( MuleContext context: contexts.values())
        {
            try {
                context.stop();
                context.dispose();
            } catch (MuleException e) {
                unstoppedContexts.add(context.getUniqueIdString());
            }
        }

        if ( !unstoppedContexts.isEmpty() )
            throw new RuntimeException(new Error("This contexts could not be stopped: " + join(unstoppedContexts)));
    }



    private void registerAsListener() {
        try {
            this.mainContext.registerListener(this);
        } catch (NotificationException e) {
            throw new RuntimeException(new Error("Could not be register as listener, aborting..."));
        }
    }

    private String join(List<String> coll)
    {
        StringBuilder sb = new StringBuilder();

        for (String x : coll)
            sb.append(x + ",");

        sb.delete(sb.length()-1, sb.length());

        return sb.toString();
    }
}
