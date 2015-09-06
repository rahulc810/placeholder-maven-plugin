package com.open.maven.plugin.common;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Pattern;

import org.codehaus.plexus.util.Base64;

public class ReplaceUtility {

	public static final String FILE_NAME_TEMPLATE_PREFIX = "\\[";
	public static final String FILE_NAME_TEMPLATE_SUFFIX = "\\]";

	public static final String FILE_CONTENT_TEMPLATE_PREFIX = "\\$\\{";
	public static final String FILE_CONTENT_TEMPLATE_SUFFIX = "\\}";
	
	public static final String FLAG_FOR_DEFAULT_FALLBACK = "NA";
	public static final String DEFAULT_PROPERTIES_LOCATION = "/default.properties";

	public static String renamePath(Path p, Map<Object, Object> target, Map<String, Pattern> lookUp) {
		String temp = "";

		String filename = p.toString();
		temp = filename;
		for (Entry<Object, Object> e : target.entrySet()) {
			if (e.getValue() == null || e.getKey() == null) {
				continue;
			}
			temp = lookUp.get(e.getKey()).matcher(temp).replaceAll((String) e.getValue());
		}

		return temp;
	}

	public static String getCertificateFromKeyStore(Path keyStore, String certAlias, String keystorePass)
			throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {

		FileInputStream is = new FileInputStream(keyStore.toFile());
		KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
		keystore.load(is, keystorePass.toCharArray());

		return new String(Base64.encodeBase64(keystore.getCertificate(certAlias).getEncoded(), true));
	}
	
	public static void updatePropertiesWithDefault(Properties inc, Path basePath) throws FileNotFoundException, IOException{
		Properties defaultProps = new Properties();
		defaultProps.load(new FileInputStream(basePath.toString() + DEFAULT_PROPERTIES_LOCATION));
		
		for(Entry<Object, Object> entry: defaultProps.entrySet() ){
			if(!inc.containsKey(entry.getKey()) || inc.get(entry.getKey()).equals(FLAG_FOR_DEFAULT_FALLBACK)){
				inc.put(entry.getKey(), entry.getValue());
			}
		}
	}
}
