/*
Copyright 2018 DEVEZHAO(zhaofang123@gmail.com)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.rebuild.web;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.rebuild.server.Application;
import com.rebuild.server.RebuildException;
import com.rebuild.server.Startup;
import com.rebuild.utils.AppUtils;

import cn.devezhao.commons.CodecUtils;
import cn.devezhao.commons.ThrowableUtils;
import cn.devezhao.commons.web.ServletUtils;
import cn.devezhao.persist4j.engine.ID;

/**
 * @author Zhao Fangfang
 * @version $Id: RequestWatchHandler.java 3313 2017-04-09 05:32:57Z devezhao $
 * @since 1.0, 2013-6-24
 */
public class RequestWatchHandler extends HandlerInterceptorAdapter {

	private static final Log LOG = LogFactory.getLog(RequestWatchHandler.class);
	
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
			Object handler) throws Exception {
		boolean chain = super.preHandle(request, response, handler);
		if (chain) {
			return verfiyPass(request, response);
		}
		return false;
	}
	
	@Override
	public void afterCompletion(HttpServletRequest request,
			HttpServletResponse response, Object handler, Exception exception)
			throws Exception {
		super.afterCompletion(request, response, handler, exception);
		
		final ID REQUSER = Application.getCurrentCaller().get(true);
		
		Application.getCurrentCaller().clear();
		logProgressTime(request);
		
		if (exception != null) {
			Throwable rootCause = ThrowableUtils.getRootCause(exception);
			String errorMsg = "系统繁忙";
			if (rootCause instanceof RebuildException) {
				errorMsg = ((RebuildException) rootCause).toClientMsgString();
			}
			
			StringBuffer sb = new StringBuffer()
					.append("\n++ EXECUTE REQUEST ERROR(s) TRACE +++++++++++++++++++++++++++++++++++++++++++++")
					.append("\nUser      : ").append(REQUSER == null ? "-" : REQUSER)
					.append("\nHandler   : ").append(request.getRequestURI() + " [ " + handler + " ]")
					.append("\nIP        : ").append(ServletUtils.getRemoteAddr(request))
					.append("\nReferer   : ").append(StringUtils.defaultIfEmpty(ServletUtils.getReferer(request), "-"))
					.append("\nUserAgent : ").append(StringUtils.defaultIfEmpty(request.getHeader("user-agent"), "-"))
					.append("\nCause     : ").append(rootCause.getClass().getName())
					.append("\nMessage   : ").append(rootCause.getMessage());
			LOG.error(sb, rootCause);
			ServletUtils.writeJson(response, 
					AppUtils.formatClientMsg(BaseControll.CODE_ERROR, errorMsg));
		}
	}
	
	/**
	 * 处理时间 LOG
	 * 
	 * @param request
	 */
	protected void logProgressTime(HttpServletRequest request) {
		Long startTime = (Long) request.getAttribute(TIMEOUT_KEY);
		startTime = System.currentTimeMillis() - startTime;
		if (startTime > 500) {
			String url = request.getRequestURI();
			String qstr = request.getQueryString();
			if (qstr != null) url += '?' + qstr;
			LOG.warn("Method handle time " + startTime + " ms. Request URL [ " + url + " ] from [ " + StringUtils.defaultIfEmpty(ServletUtils.getReferer(request), "-") + " ]");
		}
	}
	
	// --
	
	private static final String TIMEOUT_KEY = "ErrorHandler_TIMEOUT";
	
	private static final Set<String> IGNORE_RES = new HashSet<>();
	static {
		IGNORE_RES.add("/user/");
	}
	
	/**
	 * @param request
	 * @param response
	 * @return
	 * @throws IOException
	 */
	public static boolean verfiyPass(HttpServletRequest request, HttpServletResponse response) throws IOException {
		request.setAttribute(TIMEOUT_KEY, System.currentTimeMillis());
		
		ID user = AppUtils.getRequestUser(request);
		if (user != null) {
			Application.getCurrentCaller().set(user);
		} else {
			String rUrl = request.getRequestURI();
			boolean isIgnore = false;
			for (String r : IGNORE_RES) {
				if (rUrl.contains(r)) {
					isIgnore = true;
					break;
				}
			}
			
			if (!isIgnore) {
				LOG.warn("Unauthorized access [ " + rUrl + " ] from [ " + ServletUtils.getReferer(request) + " ]");
				if (ServletUtils.isAjaxRequest(request)) {
					ServletUtils.writeJson(response, AppUtils.formatClientMsg(403, "非授权访问"));
				} else {
					String reqUrl = request.getRequestURI();
					response.sendRedirect(Startup.getContextPath() + "/user/login?nexturl=" + CodecUtils.urlEncode(reqUrl));
				}
				return false;
			}
		}
		return true;
	}
}