package lan.test.portlet.zk;

import org.zkoss.web.servlet.ServletOutputStreamWrapper;

import javax.portlet.ResourceResponse;
import javax.servlet.http.HttpServletResponse;

/**
 * TODO: comment
 * @author nik-lazer  24.07.2015   16:34
 */
public class TracedResourceHttpServletResponse implements HttpServletResponse {
	private final ResourceResponse _res;
	public static HttpServletResponse getInstance(ResourceResponse res) {
		if (res instanceof HttpServletResponse)
			return (HttpServletResponse)res;
		return new TracedResourceHttpServletResponse(res);
	}
	private TracedResourceHttpServletResponse(ResourceResponse res) {
		if (res == null)
			throw new IllegalArgumentException("null");
		_res = res;
	}

	//-- ServletResponse --//
	public void flushBuffer() throws java.io.IOException {
		_res.flushBuffer();
	}
	public int getBufferSize() {
		return _res.getBufferSize();
	}
	public String getCharacterEncoding() {
		return _res.getCharacterEncoding();
	}
	public String getContentType() {
		return _res.getContentType();
	}
	public java.util.Locale getLocale() {
		return _res.getLocale();
	}
	public javax.servlet.ServletOutputStream getOutputStream()
			throws java.io.IOException {
		return ServletOutputStreamWrapper.getInstance(_res.getPortletOutputStream());
	}
	public java.io.PrintWriter getWriter() throws java.io.IOException {
		//Bug 1548478: content-type is required for some implementation (JBoss Portal)
		if (_res.getContentType() == null)
			_res.setContentType("text/html;charset=UTF-8");
		return _res.getWriter();
	}
	public boolean isCommitted() {
		return _res.isCommitted();
	}
	public void reset() {
		_res.reset();
	}
	public void resetBuffer() {
		_res.resetBuffer();
	}
	public void setBufferSize(int size) {
		_res.setBufferSize(size);
	}
	public void setCharacterEncoding(String charset) {
	}
	public void setContentLength(int len) {
	}
	public void setContentType(String type) {
		_res.setContentType(type);
	}
	public void setLocale(java.util.Locale loc)  {
	}

	//-- HttpServletResponse --//
	public void addCookie(javax.servlet.http.Cookie cookie) {
	}
	public void addDateHeader(String name, long date) {
	}
	public void addHeader(String name, String value) {
	}
	public void addIntHeader(String name, int value) {
	}
	public boolean containsHeader(String name) {
		return false;
	}
	/**
	 * @deprecated
	 */
	public String encodeRedirectUrl(String url) {
		return encodeRedirectURL(url);
	}
	public String encodeRedirectURL(String url) {
		return encodeURL(url); //try our best
	}
	/**
	 * @deprecated
	 */
	public String encodeUrl(String url) {
		return encodeURL(url);
	}
	public String encodeURL(String url) {
		return _res.encodeURL(url);
	}
	public void sendError(int sc) {
	}
	public void sendError(int sc, String msg) {
	}
	public void sendRedirect(String location) {
	}
	public void setDateHeader(String name, long date) {
	}
	public void setHeader(String name, String value) {
	}
	public void setIntHeader(String name, int value) {
	}
	public void setStatus(int sc) {
	}
	/**
	 * @deprecated
	 */
	public void setStatus(int sc, String sm)  {
	}


	//Object//
	public int hashCode() {
		return _res.hashCode();
	}
	public boolean equals(Object o) {
		if (this == o)
			return true;
		ResourceResponse val =
				o instanceof ResourceResponse ? (ResourceResponse)o:
				o instanceof TracedResourceHttpServletResponse ? ((TracedResourceHttpServletResponse)o)._res: null;
		return val != null && val.equals(_res);
	}

}
