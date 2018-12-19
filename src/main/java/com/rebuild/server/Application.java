/*
rebuild - Building your system freely.
Copyright (C) 2018 devezhao <zhaofang123@gmail.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package com.rebuild.server;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.rebuild.server.bizz.privileges.UserStore;
import com.rebuild.server.helper.cache.RecordOwningCache;
import com.rebuild.server.metadata.DynamicMetadataFactory;
import com.rebuild.server.service.CommonService;
import com.rebuild.server.service.SQLExecutor;
import com.rebuild.server.service.base.GeneralEntityService;
import com.rebuild.server.service.notification.NotificationService;
import com.rebuild.server.service.query.QueryFactory;
import com.rebuild.web.OnlineSessionStore;

import cn.devezhao.persist4j.PersistManagerFactory;
import cn.devezhao.persist4j.Query;
import cn.devezhao.persist4j.engine.ID;

/**
 * 后台类入口
 * 
 * @author zhaofang123@gmail.com
 * @since 05/18/2018
 */
public final class Application {
	
	/** Rebuild Version
	 */
	public static final String VER = "1.0.0-SNAPSHOT";
	
	/** Logging for Global
	 */
	public static final Log LOG = LogFactory.getLog(Application.class);
	
	
	// SPRING
	private static ApplicationContext APPLICATION_CTX;
	// 业务实体对应的服务类
	private static Map<Integer, GeneralEntityService> ESS = new HashMap<>();
	
	protected Application(ApplicationContext ctx) {
		APPLICATION_CTX = ctx;
	}
	
	/**
	 * 初始化
	 */
	protected void init(long startingAt) {
		boolean serversReady = ServerStatus.checkAll();
		if (!serversReady) {
			LOG.info("Rebuild Booting failed!");
			return;
		}
		
		// 自定义实体
		LOG.info("Loading customized entities ...");
		((DynamicMetadataFactory) APPLICATION_CTX.getBean(PersistManagerFactory.class).getMetadataFactory()).refresh(false);
		
		// 实体对应的服务类
		for (Map.Entry<String, GeneralEntityService> es : APPLICATION_CTX.getBeansOfType(GeneralEntityService.class).entrySet()) {
			GeneralEntityService ges = es.getValue();
			if (ges.getEntityCode() > 0) {
				ESS.put(ges.getEntityCode(), ges);
			}
		}
		
		LOG.info("Rebuild Boot successful in " + (System.currentTimeMillis() - startingAt) + " ms");
	}
	
	/**
	 * 非 Server 环境下启动
	 * 
	 * @return
	 */
	protected static ApplicationContext debug() {
		if (APPLICATION_CTX == null) {
			LOG.info("Rebuild Booting in DEBUG mode ...");
			long at = System.currentTimeMillis();
			ApplicationContext ctx = new ClassPathXmlApplicationContext(new String[] { "application-ctx.xml" });
			new Application(ctx).init(at);
		}
		return APPLICATION_CTX;
	}
	
	/**
	 * 是否开发模式
	 * 
	 * @return
	 */
	public static boolean devMode() {
		return org.apache.commons.lang.SystemUtils.IS_OS_WINDOWS || "1".equals(System.getProperty("dev"));
	}
	
	/**
	 * 添加服务停止钩子
	 * 
	 * @param hook
	 */
	public static void addShutdownHook(Thread hook) {
		LOG.warn("Add shutdown hook : " + hook.getName());
		Runtime.getRuntime().addShutdownHook(hook);
	}
	
	public static <T> T getBean(Class<T> beanClazz) {
		return APPLICATION_CTX.getBean(beanClazz);
	}

	public static OnlineSessionStore getSessionStore() {
		return getBean(OnlineSessionStore.class);
	}
	
	public static ID currentCallerUser() {
		return getSessionStore().getCurrentCaller();
	}

	public static PersistManagerFactory getPersistManagerFactory() {
		return getBean(PersistManagerFactory.class);
	}
	
	public static DynamicMetadataFactory getMetadataFactory() {
		return (DynamicMetadataFactory) getPersistManagerFactory().getMetadataFactory();
	}

	public static UserStore getUserStore() {
		return getBean(UserStore.class);
	}
	
	public static RecordOwningCache getRecordOwningCache() {
		return getBean(RecordOwningCache.class);
	}

	public static com.rebuild.server.bizz.privileges.SecurityManager getSecurityManager() {
		return getBean(com.rebuild.server.bizz.privileges.SecurityManager.class);
	}

	public static QueryFactory getQueryFactory() {
		return getBean(QueryFactory.class);
	}
	
	public static Query createQuery(String ajql) {
		return getQueryFactory().createQuery(ajql);
	}
	
	public static Query createQueryNoFilter(String ajql) {
		return getQueryFactory().createQueryNoFilter(ajql);
	}

	public static SQLExecutor getSQLExecutor() {
		return getBean(SQLExecutor.class);
	}
	
	public static NotificationService getNotifications() {
		return getBean(NotificationService.class);
	}

	public static CommonService getCommonService() {
		return getBean(CommonService.class);
	}

	public static GeneralEntityService getGeneralEntityService(int entityCode) {
		if (ESS.containsKey(entityCode)) {
			return ESS.get(entityCode);
		} else {
			return (GeneralEntityService) APPLICATION_CTX.getBean("generalEntityService");
		}
	}
}
