/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

/**
 * {@code BeanPostProcessor} that detects beans which implement the {@code ApplicationListener}
 * interface. This catches beans that can't reliably be detected by {@code getBeanNamesForType}
 * and related operations which only work against top-level beans.
 *
 * <p>With standard Java serialization, this post-processor won't get serialized as part of
 * {@code DisposableBeanAdapter} to begin with. However, with alternative serialization
 * mechanisms, {@code DisposableBeanAdapter.writeReplace} might not get used at all, so we
 * defensively mark this post-processor's field state as {@code transient}.
 *
 * @author Juergen Hoeller
 * @since 4.3.4
 */
//早期的ApplicationListener注册器，主要为了检查内部的bean比如各种后处理器
class ApplicationListenerDetector implements DestructionAwareBeanPostProcessor, MergedBeanDefinitionPostProcessor {

	private static final Log logger = LogFactory.getLog(ApplicationListenerDetector.class);

	private final transient AbstractApplicationContext applicationContext;

	private final transient Map<String, Boolean> singletonNames = new ConcurrentHashMap<>(256);


	public ApplicationListenerDetector(AbstractApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}


	//merge完beanDefinition后 把ApplicationListener的名字和我想要的bean的配置信息，存到全局变量中
	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
		if (ApplicationListener.class.isAssignableFrom(beanType)) {
			this.singletonNames.put(beanName, beanDefinition.isSingleton());
		}
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	//初始化bean后 如果是ApplicationListener就把listener注册到spring容器中
	//这块比较精妙由于需要知道bean是否为单例模式的，不是单例的就不注册为Listener
	// 所以需要拿到BeanDefinition，那就还需要实现MergeBeanDefinitionPostProcessor获取bean定义
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof ApplicationListener) {
			// potentially not detected as a listener by getBeanNamesForType retrieval
			Boolean flag = this.singletonNames.get(beanName);
			if (Boolean.TRUE.equals(flag)) {
				// singleton bean (top-level or inner): register on the fly
				this.applicationContext.addApplicationListener((ApplicationListener<?>) bean);
			}
			else if (Boolean.FALSE.equals(flag)) {
				if (logger.isWarnEnabled() && !this.applicationContext.containsBean(beanName)) {
					// inner bean with other scope - can't reliably process events
					logger.warn("Inner bean '" + beanName + "' implements ApplicationListener interface " +
							"but is not reachable for event multicasting by its containing ApplicationContext " +
							"because it does not have singleton scope. Only top-level listener beans are allowed " +
							"to be of non-singleton scope.");
				}
				this.singletonNames.remove(beanName);
			}
		}
		return bean;
	}

	@Override
	public void postProcessBeforeDestruction(Object bean, String beanName) {
		if (bean instanceof ApplicationListener) {
			try {
				ApplicationEventMulticaster multicaster = this.applicationContext.getApplicationEventMulticaster();
				multicaster.removeApplicationListener((ApplicationListener<?>) bean);
				multicaster.removeApplicationListenerBean(beanName);
			}
			catch (IllegalStateException ex) {
				// ApplicationEventMulticaster not initialized yet - no need to remove a listener
			}
		}
	}

	@Override
	public boolean requiresDestruction(Object bean) {
		return (bean instanceof ApplicationListener);
	}


	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof ApplicationListenerDetector &&
				this.applicationContext == ((ApplicationListenerDetector) other).applicationContext));
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHashCode(this.applicationContext);
	}

}
