package lan.tomcat.jndi;

import java.util.Properties;

/**
 * String JNDI resource factory
 * @author nik-lazer  24.06.2015   12:15
 */
public class StringFactory {
	private static final String VALUE_PROPERTY = "value";
	private Properties properties;

	public String create() {
		String value = properties.getProperty(VALUE_PROPERTY);
		return value;
	}

	public Properties getProperties() {
		return properties;
	}

	public void setProperties(final Properties properties) {
		this.properties = properties;
	}
}
