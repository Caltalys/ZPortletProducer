package lan.test.portlet.zk.wsrp;

import lan.test.config.ApplicationContextProvider;
import lan.test.portlet.zk.component.fileupload.FileUploadServlet;
import lan.test.portlet.zk.wsrp.encoder.WebcenterPortletURLEncoder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.portlet.util.PortletUtils;
import org.zkoss.lang.Classes;
import org.zkoss.lang.Exceptions;
import org.zkoss.lang.Library;
import org.zkoss.mesg.Messages;
import org.zkoss.web.Attributes;
import org.zkoss.web.portlet.Portlets;
import org.zkoss.web.portlet.RenderHttpServletRequest;
import org.zkoss.web.portlet.RenderHttpServletResponse;
import org.zkoss.web.portlet.ResourceHttpServletRequest;
import org.zkoss.web.portlet.ResourceHttpServletResponse;
import org.zkoss.web.servlet.Charsets;
import org.zkoss.web.servlet.http.Encodes;
import org.zkoss.web.util.resource.ClassWebResource;
import org.zkoss.zk.au.http.AuExtension;
import org.zkoss.zk.au.http.DHtmlUpdateServlet;
import org.zkoss.zk.mesg.MZk;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.Richlet;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.http.DHtmlLayoutPortlet;
import org.zkoss.zk.ui.http.DHtmlLayoutServlet;
import org.zkoss.zk.ui.http.DesktopRecycles;
import org.zkoss.zk.ui.http.ExecutionImpl;
import org.zkoss.zk.ui.http.I18Ns;
import org.zkoss.zk.ui.http.WebManager;
import org.zkoss.zk.ui.impl.RequestInfoImpl;
import org.zkoss.zk.ui.metainfo.PageDefinition;
import org.zkoss.zk.ui.metainfo.PageDefinitions;
import org.zkoss.zk.ui.sys.PageCtrl;
import org.zkoss.zk.ui.sys.PageRenderPatch;
import org.zkoss.zk.ui.sys.RequestInfo;
import org.zkoss.zk.ui.sys.SessionCtrl;
import org.zkoss.zk.ui.sys.SessionsCtrl;
import org.zkoss.zk.ui.sys.UiFactory;
import org.zkoss.zk.ui.sys.WebAppCtrl;
import org.zkoss.zk.ui.util.DesktopRecycle;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.ClientDataRequest;
import javax.portlet.GenericPortlet;
import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;
import javax.portlet.PortletRequest;
import javax.portlet.PortletSession;
import javax.portlet.ProcessAction;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.portlet.ResourceURL;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Omplementation WSRP portlet class
 * @see <a href="https://github.com/v0v87/zkoss-wsrp">zkoss-wsrp</a>
 * @author nik-lazer 28.05.2013   15:09
 */
public class WSRPDhtmlLayoutPortlet extends GenericPortlet {
	private static final Logger log = LoggerFactory.getLogger(DHtmlLayoutPortlet.class);

	/** The parameter or attribute to specify the path of the ZUML page. */
	private static final String ATTR_PAGE = "zk_page";
	/** The parameter or attribute to specify the path of the richlet. */
	private static final String ATTR_RICHLET = "zk_richlet";
	/**
	 * The parameter or attribute to removing gzip-compressing form request headers
	 */
	private static final String ATTR_REMOVE_COMPRESS = "remove_compress_headers";
	private static final String ATTR_REMOTE_USER = "attr_remote_user";
	/** The default page. */
	private String _defpage;
	/** Check if support JSR 286 */
	private boolean isJSR286 = true;

	private boolean removeCompressHeaders = false;

	private String attrRemoteUser;


	public void init() throws PortletException {
		_defpage = getPortletConfig().getInitParameter(ATTR_PAGE);
		try {
			Class.forName("javax.portlet.ResourceURL");
		} catch (ClassNotFoundException e) {
			isJSR286 = false;
		}
		String removeCompressHeadersValue = getPortletConfig().getInitParameter(ATTR_REMOVE_COMPRESS);
		if (Boolean.parseBoolean(removeCompressHeadersValue)) {
			removeCompressHeaders = Boolean.valueOf(removeCompressHeadersValue);
		}
		attrRemoteUser = getPortletConfig().getInitParameter(ATTR_REMOTE_USER);
	}

	protected void doView(RenderRequest request, RenderResponse response)
			throws PortletException, IOException {
		HttpServletRequest httpServletRequest = RenderHttpServletRequest.getInstance(request);
		HttpServletRequestWrapper httpreq = new PortletHttpServletRequestWrapper<PortletRequest>(httpServletRequest, request);
		ApplicationContextProvider.getPreAuthService().preAuth(httpreq);
		//try parameter first and then attribute
		boolean bRichlet = false;
		String path = request.getParameter(ATTR_PAGE);
		if (path == null) {
			path = (String) request.getAttribute(ATTR_PAGE);
			if (path == null) {
				PortletPreferences prefs = request.getPreferences();
				path = prefs.getValue(ATTR_PAGE, null);
				if (path == null) {
					path = request.getParameter(ATTR_RICHLET);
					bRichlet = path != null;
					if (!bRichlet) {
						path = (String) request.getAttribute(ATTR_RICHLET);
						bRichlet = path != null;
						if (!bRichlet) {
							path = prefs.getValue(ATTR_RICHLET, null);
							bRichlet = path != null;
							if (!bRichlet) {
								path = _defpage;
							}
						}
					}
				}
			}
		}

		final Session sess = getSession(request, true);
		if (!SessionsCtrl.requestEnter(sess)) {
			handleError(sess, request, response, path, null,
					Messages.get(MZk.TOO_MANY_REQUESTS));
			return;
		}
		SessionsCtrl.setCurrent(sess);
		try {
			// Bug ZK-1179: process I18N in portlet environment
			httpreq.setAttribute(WebcenterPortletURLEncoder.ORACLE_WEBCENTER_PORTLET_RESPONSE, response);

			HttpServletResponse httpres = RenderHttpServletResponse.getInstance(response);

			final Object old = I18Ns.setup(httpreq.getSession(), httpreq, httpres,
					sess.getWebApp().getConfiguration().getResponseCharset());
			try {
				if (!process(sess, request, response, path, bRichlet)) {
					handleError(sess, request, response, path, null, null);
				}
			} catch (Throwable ex) {
				log.error("Error in porlet doView method ", ex);
				handleError(sess, request, response, path, ex, null);
			} finally {
				I18Ns.cleanup(httpreq, old);
			}
		} finally {
			SessionsCtrl.requestExit(sess);
			SessionsCtrl.setCurrent((Session) null);
		}
	}

	/**
	 * Process AJAX request here instead of DHtmlUpdateServlet if the Portal Container support JSR 286.
	 * @since 6.5.2
	 */
	public void serveResource(ResourceRequest request, ResourceResponse response)
			throws PortletException, IOException {
		final WebManager webman = getWebManager();
		final WebApp wapp = webman.getWebApp();

		final HttpServletRequest httpServletRequest = ResourceHttpServletRequest.getInstance(request);
		HttpServletRequestWrapper httpreq = new ClientDataHttpServletRequestWrapper(httpServletRequest, request);
		final HttpServletResponse httpres = ResourceHttpServletResponse.getInstance(response);
		ApplicationContextProvider.getPreAuthService().preAuth(httpreq);
		final Session sess = getSession(request, false);

		final DHtmlUpdateServlet updateServlet = DHtmlUpdateServlet.getUpdateServlet(wapp);
		boolean compress = false; //Some portal container (a.k.a GateIn) doesn't work with gzipped output stream.
		final String sid = httpreq.getHeader("ZK-SID");
		if (sid != null) {
			response.setProperty("ZK-SID", sid);
		}
		if (sess == null) {
			try {
				updateServlet.denoteSessionTimeout(wapp, httpreq, httpres, compress);
			} catch (ServletException e) {
				e.printStackTrace();
			}
			return;
		}

		final Object old = I18Ns.setup(httpreq.getSession(), httpreq, httpres, "UTF-8");
		SessionsCtrl.setCurrent(sess);
		try {
			httpreq.setAttribute(WebcenterPortletURLEncoder.ORACLE_WEBCENTER_PORTLET_RESPONSE, response);
			String resourceID = request.getResourceID();
			Enumeration headerNames = httpreq.getHeaderNames();
			log.debug("WSRP serveResource resourceID:" + resourceID);
			String uploadUrl = httpreq.getContextPath() + "/upload";
			if (uploadUrl.equals(resourceID)) {
				FileUploadServlet uploadServlet = FileUploadServlet.getFileUploadServlet(wapp);
				uploadServlet.process(httpreq, httpres);
				return;
			}
			String pathInfo = StringUtils.substringAfter(resourceID, "/zkau");
			if (pathInfo.startsWith(ClassWebResource.PATH_PREFIX)) {
				String url = StringUtils.substringAfter(resourceID, ClassWebResource.PATH_PREFIX);
				ClassWebResource webResource = webman.getClassWebResource();
				webResource.service(httpreq, httpres, url);
				return;
			} else if (StringUtils.isNotEmpty(pathInfo)) {
				String auExtensionName = pathInfo.substring(1, pathInfo.indexOf("/", 1));
				final AuExtension aue = updateServlet.getAuExtension("/view");
				if (aue == null) {
					httpres.sendError(HttpServletResponse.SC_NOT_FOUND);
					log.debug("Unknown path info: " + pathInfo);
					return;
				}

				Charsets.setup(null, httpreq, httpres, "UTF-8");
				try {
					boolean isView = "view".equals(auExtensionName);
					// For Liferay we have to fix header before calling method service
					if (isView) {
						WSRPUtils.fixContentDisposition(response, httpreq, resourceID);
					}
					aue.service(httpreq, httpres, pathInfo);
					if (isView) {
						response.setContentType("application/octet-stream");
					}
				} finally {
					I18Ns.cleanup(httpreq, old);
				}
				return; //done
			}

			response.setProperty("Pragma", "no-cache");
			response.setProperty("Cache-Control", "no-cache");
			response.setProperty("Cache-Control", "no-store");
			response.setProperty("Expires", "-1");

			updateServlet.process(sess, httpreq, httpres, compress);
		} catch (ServletException e) {
			e.printStackTrace();
		} finally {
			I18Ns.cleanup(httpreq, old);
			SessionsCtrl.requestExit(sess);
			SessionsCtrl.setCurrent((Session) null);
		}
	}

	/** Returns the session. */
	private Session getSession(Object request, boolean create)
			throws PortletException {
		final WebApp wapp = getWebManager().getWebApp();

		PortletSession psess = null;
		if (request instanceof RenderRequest) {
			psess = ((RenderRequest) request).getPortletSession();
		} else if (request instanceof ResourceRequest) {
			psess = ((ResourceRequest) request).getPortletSession();
		}

		Session sess = SessionsCtrl.getSession(wapp, psess);
		if (sess == null && create) {
			sess = SessionsCtrl.newSession(wapp, psess, request);
		}

		return sess;
	}

	/**
	 * Process a portlet request.
	 * @return false if the page is not found.
	 * @since 3.0.0
	 */
	protected boolean process(Session sess, RenderRequest request,
	                          RenderResponse response, String path, boolean bRichlet)
			throws PortletException, IOException {
//		if (log.isDebugEnabled()) log.debug("Creates from "+path);
		final WebManager webman = getWebManager();
		final WebApp wapp = webman.getWebApp();
		final WebAppCtrl wappc = (WebAppCtrl) wapp;

		final HttpServletRequest httpServletRequest = RenderHttpServletRequest.getInstance(request);
		HttpServletRequestWrapper httpreq = new PortletHttpServletRequestWrapper<PortletRequest>(httpServletRequest, request);
		final HttpServletResponse httpres = RenderHttpServletResponse.getInstance(response);
		final ServletContext svlctx = wapp.getServletContext();


		try {
			httpreq.setAttribute("javax.zkoss.zk.lang.js.generated", Boolean.TRUE);
			response.getWriter().print("<script src=" + WebcenterPortletURLEncoder.createResourceUrl(svlctx, httpreq, httpres, "~./js/zk.wpd") + "></script>\n");
			response.getWriter().print("<script src=" + WebcenterPortletURLEncoder.createResourceUrl(svlctx, httpreq, httpres, "~./js/zul.lang.wpd") + "></script>\n");
			response.getWriter().print("<script src=" + Encodes.encodeURL(svlctx, httpreq, httpres, "/zksandbox.js.dsp") + "></script>\n");

		} catch (ServletException e) {
			throw new PortletException(e);
		}

		final DesktopRecycle dtrc = wapp.getConfiguration().getDesktopRecycle();
		Desktop desktop = dtrc != null ?
		                  DesktopRecycles.beforeService(dtrc, svlctx, sess, httpreq, httpres, path) : null;

		try {
			if (desktop != null) { //recycle
				final Page page = getMainPage(desktop);
				if (page != null) {
					final Execution exec =
							new ExecutionImpl(svlctx, httpreq, httpres, desktop, page);
					fixContentType(response);
					wappc.getUiEngine()
							.recycleDesktop(exec, page, response.getWriter());
				} else {
					desktop = null; //something wrong (not possible; just in case)
				}
			}

			if (desktop == null) {
				desktop = webman.getDesktop(sess, httpreq, httpres, path, true);
				if (desktop == null) //forward or redirect
				{
					return true;
				}

				final RequestInfo ri = new RequestInfoImpl(
						wapp, sess, desktop, httpreq,
						PageDefinitions.getLocator(wapp, path));
				((SessionCtrl) sess).notifyClientRequest(true);

				final Page page;
				final PageRenderPatch patch = getRenderPatch();
				final Writer out = patch.beforeRender(ri);
				final UiFactory uf = wappc.getUiFactory();
				if (uf.isRichlet(ri, bRichlet)) {
					final Richlet richlet = uf.getRichlet(ri, path);
					if (richlet == null) {
						return false; //not found
					}

					page = WebManager.newPage(uf, ri, richlet, httpres, path);
					final Execution exec =
							new ExecutionImpl(svlctx, httpreq, httpres, desktop, page);
					fixContentType(response);
					if (isJSR286) {
						ResourceURL url = response.createResourceURL();
						page.setAttribute("org.zkoss.portlet2.resourceURL", response.encodeURL(url.toString()), Page.PAGE_SCOPE);
						page.setAttribute("org.zkoss.portlet2.namespace", getNamespace(response), Page.PAGE_SCOPE);
					}
					wappc.getUiEngine().execNewPage(exec, richlet, page,
							out != null ? out : response.getWriter());
				} else if (path != null) {
					final PageDefinition pagedef = uf.getPageDefinition(ri, path);
					if (pagedef == null) {
						return false; //not found
					}

					page = WebManager.newPage(uf, ri, pagedef, httpres, path);
					final Execution exec =
							new ExecutionImpl(svlctx, httpreq, httpres, desktop, page);
					fixContentType(response);
					if (isJSR286) {
						ResourceURL url = response.createResourceURL();
						url.setResourceID("/zkau");
						page.setAttribute("org.zkoss.portlet2.resourceURL", url.toString(), Page.PAGE_SCOPE);
						page.setAttribute("org.zkoss.portlet2.namespace", getNamespace(response), Page.PAGE_SCOPE);
					}
					wappc.getUiEngine().execNewPage(exec, pagedef, page,
							out != null ? out : response.getWriter());
				} else {
					return true; //nothing to do
				}

				if (out != null) {
					patch.patchRender(ri, page, out, response.getWriter());
				}
			}
		} finally {
			if (dtrc != null) {
				DesktopRecycles.afterService(dtrc, desktop);
			}
		}
		return true; //success
	}

	/**
	 * Returns the namespace for resource request parameters
	 * <p>
	 * Default: "".
	 * @since 6.5.6
	 */
	protected String getNamespace(RenderResponse response) {
		final String s = Library.getProperty("org.zkoss.zk.portlet2.namespacedParameter.enabled");
		if (s == null || "false".equals(s)) {
			return "";
		}
		return response.getNamespace();
	}

	private static PageRenderPatch getRenderPatch() {
		if (_prpatch != null) {
			return _prpatch;
		}

		synchronized (DHtmlLayoutPortlet.class) {
			if (_prpatch != null) {
				return _prpatch;
			}

			final PageRenderPatch patch;
			final String clsnm = Library.getProperty(
					org.zkoss.zk.ui.sys.Attributes.PORTLET_RENDER_PATCH_CLASS);
			if (clsnm == null) {
				patch = new PageRenderPatch() {
					public Writer beforeRender(RequestInfo reqInfo) {
						return null;
					}

					public void patchRender(RequestInfo reqInfo, Page page, Writer result, Writer out)
							throws IOException {
					}
				};
			} else {
				try {
					patch = (PageRenderPatch) Classes.newInstanceByThread(clsnm);
				} catch (ClassCastException ex) {
					throw new UiException(clsnm + " must implement " + PageRenderPatch.class.getName());
				} catch (Throwable ex) {
					throw UiException.Aide.wrap(ex, "Unable to instantiate");
				}
			}
			return _prpatch = patch;
		}
	}

	private static volatile PageRenderPatch _prpatch;

	private static void fixContentType(RenderResponse response) {
		//Bug 1548478: content-type is required for some implementation (JBoss Portal)
		if (response.getContentType() == null) {
			response.setContentType("text/html;charset=UTF-8");
		}
	}

	/**
	 * Returns the layout servlet.
	 */
	private final WebManager getWebManager()
			throws PortletException {
		final WebManager webman =
				(WebManager) getPortletContext().getAttribute(/*WebManager.ATTR_WEB_MANAGER*/"javax.zkoss.zk.ui.WebManager");
		if (webman == null) {
			throw new PortletException("The Layout Servlet not found. Make sure <load-on-startup> is specified for " + DHtmlLayoutServlet.class.getName());
		}
		return webman;
	}

	/**
	 * Handles exception being thrown when rendering a page.
	 * @param ex the exception being throw. If null, it means the page
	 *           is not found.
	 */
	private void handleError(Session sess, RenderRequest request,
	                         RenderResponse response, String path, Throwable err, String msg)
			throws PortletException, IOException {
		if (err != null) {
			//Bug 1714094: we have to handle err, because Web container
			//didn't allow developer to intercept errors caused by inclusion
			final String errpg = sess.getWebApp().getConfiguration()
					.getErrorPage(sess.getDeviceType(), err);
			if (errpg != null) {
				try {
					request.setAttribute("javax.servlet.error.message", Exceptions.getMessage(err));
					request.setAttribute("javax.servlet.error.exception", err);
					request.setAttribute("javax.servlet.error.exception_type", err.getClass());
					request.setAttribute("javax.servlet.error.status_code", new Integer(500));
					if (process(sess, request, response, errpg, false)) {
						return; //done
					}
					log.warn("The error page not found: " + errpg);
				} catch (IOException ex) { //eat it (connection off)
				} catch (Throwable ex) {
					log.warn("Failed to load the error page: " + errpg, ex);
				}
			}

			if (msg == null) {
				msg = Messages.get(MZk.PAGE_FAILED,
						new Object[]{path, Exceptions.getMessage(err),
								Exceptions.formatStackTrace(null, err, null, 6)});
			}
		} else {
			if (msg == null) {
				msg = path != null ?
				      Messages.get(MZk.PAGE_NOT_FOUND, new Object[]{path}) :
				      Messages.get(MZk.PORTLET_PAGE_REQUIRED);
			}
		}

		final Map<String, String> attrs = new HashMap<String, String>();
		attrs.put(Attributes.ALERT_TYPE, "error");
		attrs.put(Attributes.ALERT, msg);
		Portlets.include(getPortletContext(), request, response,
				"~./html/alert.dsp", attrs, Portlets.OVERWRITE_URI);
		//Portlets doesn't support PASS_THRU_ATTR yet (because
		//protlet request will mangle attribute name)
	}

	/**
	 * Returns the main page of the desktop.
	 * It assumes there is at most one main page (that is, a page without owner)
	 */
	/*package*/
	static Page getMainPage(Desktop desktop) {
		for (Iterator it = desktop.getPages().iterator(); it.hasNext(); ) {
			final Page page = (Page) it.next();
			if (((PageCtrl) page).getOwner() == null) {
				return page;
			}
		}
		return null;
	}

	class PortletHttpServletRequestWrapper<T extends PortletRequest> extends HttpServletRequestWrapper {
		protected final T portletRequest;

		public PortletHttpServletRequestWrapper(HttpServletRequest request, T portletRequest) {
			super(request);
			this.portletRequest = portletRequest;
		}

		@Override
		public String getHeader(String name) {
			String ret = null;
			final String value = portletRequest.getProperty(name);
			if (value != null) {
				ret = value;
			}
			ret = super.getHeader(name);
			// remove gzip form header - disabling gzip-compressing for WAS
			if (removeCompressHeaders) {
				if (name.equalsIgnoreCase("accept-encoding")) {
					ret = WSRPUtils.removeTokens(ret, "gzip", ",");
				}
			}
			return ret;
		}

		@Override
		public StringBuffer getRequestURL() {
			StringBuffer stringBuffer = super.getRequestURL();
			if (stringBuffer == null) { // It's right for WebSphere
				stringBuffer = new StringBuffer();
			}
			return stringBuffer;
		}

		@Override
		public String getContentType() {
			if (portletRequest instanceof ClientDataRequest) {
				ClientDataRequest clientDataRequest = (ClientDataRequest) portletRequest;
				if (clientDataRequest.getContentType() != null) {
					return clientDataRequest.getContentType();
				}
			}
			return super.getContentType();
		}

		@Override
		public String getRemoteUser() {
			if (attrRemoteUser != null) {
				return (String) portletRequest.getPortletSession().getAttribute(attrRemoteUser);
			}
			return super.getRemoteUser();
		}

	}

	class ClientDataHttpServletRequestWrapper extends PortletHttpServletRequestWrapper<ClientDataRequest> {

		public ClientDataHttpServletRequestWrapper(HttpServletRequest request, ClientDataRequest portletRequest) {
			super(request, portletRequest);
		}

		@Override
		public String getContentType() {
			final String value = portletRequest.getContentType();
			return value != null ? value : super.getContentType();
		}

		@Override
		public String getMethod() {
			final String value = portletRequest.getMethod();
			return value != null ? value : super.getMethod();
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			final InputStream portletInputStream = portletRequest.getPortletInputStream();
			return new ServletInputStream() {
				@Override
				public int read() throws IOException {
					return portletInputStream.read();
				}

				@Override
				public void close() throws IOException {
					portletInputStream.close();
				}
			};
		}
	}

}