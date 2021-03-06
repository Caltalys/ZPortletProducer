package lan.test.portlet.zk.history;

import lan.test.config.ApplicationContextProvider;
import lan.test.portlet.zk.util.UIUtils;
import org.apache.commons.lang3.StringUtils;
import org.zkoss.json.JSONValue;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.util.Clients;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;

/**
 * Browser history manager
 * @author nik-lazer  15.05.2015   12:19
 */
public class WebBrowserHistoryManager {
	public static final String URL_HISTORY_KEY = "URL_HISTORY_STACK";
	private static String defaultPage;

	private boolean allowUrlRewrite;

	public void addNewUrlToStackAndReplace(Map<String, ?> state, String title, String url) {
		LinkedList<String> stack = (LinkedList<String>) Executions.getCurrent().getSession().getAttribute(URL_HISTORY_KEY);
		if (stack == null) {
			stack = new LinkedList<String>();
			Executions.getCurrent().getSession().setAttribute(URL_HISTORY_KEY, stack);
		}
		final String json = getJSON("replaceState", state, title, url);
		stack.push(json);
		evalJavaScript(json);
	}

	public String popUrlFromStackAndReplace() {
		LinkedList<String> stack = (LinkedList<String>) Executions.getCurrent().getSession().getAttribute(URL_HISTORY_KEY);
		if (stack == null || stack.isEmpty()) {
			//делать нечего - вообще нет истории.
			evalJavaScript(getDefaultPage());
			return null;
		}
		String old = stack.pop();
		if (stack.isEmpty()) {
			evalJavaScript(getDefaultPage());
		} else {
			evalJavaScript(stack.getFirst());
		}
		return old;
	}

	public void addNewUrlToStackAndReplace(String json) {
		LinkedList<String> stack = (LinkedList<String>) Executions.getCurrent().getSession().getAttribute(URL_HISTORY_KEY);
		if (stack == null) {
			stack = new LinkedList<String>();
			Executions.getCurrent().getSession().setAttribute(URL_HISTORY_KEY, stack);
		}
		stack.push(json);
		evalJavaScript(json);
	}

	/**
	 * Adds new element into browser history and rewrites URL
	 * @param state URL params
	 * @param title Browser window title
	 * @param url   new URL
	 */
	public void pushState(Map<String, ?> state, String title, String url) {
		evalJavaScript(getJSON("pushState", state, title, url));
	}

	private String getDefaultPage() {
		if (defaultPage == null) {
			String page = StringUtils.equals(UIUtils.getServletContextPath(), "/") ? "/index.zul" : UIUtils.getServletContextPath() + "/index.zul";
			defaultPage = getJSON("replaceState", Collections.EMPTY_MAP, null, page);
		}
		return defaultPage;
	}

	private String getJSON(String function, Map<String, ?> state, String title, String url) {
		StringBuilder js = new StringBuilder("if(!!(window.history && history.pushState)){ history." + function + "(");
		js.append(JSONValue.toJSONString(state));
		js.append(",'");
		js.append(title);
		js.append("','");
		js.append(url);
		js.append("')}");
		return js.toString();
	}

	private void evalJavaScript(String javaScript) {
		if (isUrlRewrite()) {
			Clients.evalJavaScript(javaScript);
		}
	}

	private boolean isUrlRewrite() {
		return allowUrlRewrite && isNeedRewriteUrl();
	}

	public boolean isAllowUrlRewrite() {
		return allowUrlRewrite;
	}

	public void setAllowUrlRewrite(boolean allowUrlRewrite) {
		this.allowUrlRewrite = allowUrlRewrite;
	}

	public boolean isNeedRewriteUrl() {
		return ApplicationContextProvider.getConfig().isNeedRewriteUrl();
	}
}

