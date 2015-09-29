package com.open.maven.plugin.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.Base64;
import org.codehaus.plexus.util.StringUtils;

public class ReplaceUtility {

	public static final String FILE_NAME_TEMPLATE_PREFIX = "\\[";
	public static final String FILE_NAME_TEMPLATE_SUFFIX = "\\]";

	public static final String FILE_CONTENT_TEMPLATE_PREFIX = "\\$\\{";
	public static final String FILE_CONTENT_TEMPLATE_SUFFIX = "\\}";

	public static final String FLAG_FOR_DEFAULT_FALLBACK = "NA";
	public static final String PROPERTIES_FILE_SUFFIX = ".properties";
	public static final String IDP_URL_PART_KEY = "urlTenant";
	public static final String IDP_CERT_KEY = "idpCert";
	public static final String TENANT_KEY = "tenant";

	public static final Pattern IS_PLACEHODLER = Pattern.compile(".*?\\$\\{(.*?)\\}.*?");
	public static final Pattern IS_PROPERTIES_FILE = Pattern.compile(".*?(\\d+)\\.properties");
	public static final String PROPERTIES_FILE_DELIMITER = ",";

	public static final Properties resolveBaseProperties(File basePath, String tenantPropLocation, Log log)
			throws FileNotFoundException, IOException {
		Properties replace = new Properties();
		replace = new Properties();
		
		//assuming decrementing priority
		List<String> propertiesInOrder = getPropertiesFromDelimitedString(tenantPropLocation, PROPERTIES_FILE_DELIMITER);
		
		for (int i = propertiesInOrder.size() -1; i > -1; i--) {
			try {
				mergeProperties(replace, basePath.toString() + "/"+ propertiesInOrder.get(i));
			} catch (Exception e1) {
				log.error("Could not load properties: " + propertiesInOrder.get(i) + ". Continuing with other properties");
			}
		}
		
		//ReplaceUtility.updatePropertiesWithDefault(replace, basePath.toPath());
		ReplaceUtility.updatePropertiesWithSpecialConventions(replace, basePath.toPath());

		// resolve indirections within base properties
		//urlTenant=${tenant} -> urlTenant=tenantName
		for (Entry<Object, Object> e : replace.entrySet()) {
			Object valueObj = e.getValue();
			String value = valueObj == null ? "" : (String) e.getValue();
			Matcher m = IS_PLACEHODLER.matcher(value);

			while (value != null && m.find()) {
				String extractedVal = m.group(1);
				extractedVal = replace.getProperty(extractedVal);
				e.setValue(IS_PLACEHODLER.matcher(value).replaceAll(extractedVal));
			}
		}
		
		log.debug("Configuration to be used:");
		for(Entry<Object, Object> e: replace.entrySet()){
			log.info(e.getKey() + "=" + e.getValue());
		}		
		
		return replace;
	}
	
	public static final void updateRegexMaps(Map<String, Pattern> replacePatterns, Map<String, Pattern> replaceNamePatterns,
			Properties replace){
		for (Object keyObj : replace.keySet()) {
			if (keyObj == null)
				continue;

			String key = (String) keyObj;
			replacePatterns.put(key,
					Pattern.compile(FILE_CONTENT_TEMPLATE_PREFIX + key + FILE_CONTENT_TEMPLATE_SUFFIX));
			replaceNamePatterns.put(key,
					Pattern.compile(FILE_NAME_TEMPLATE_PREFIX + key + FILE_NAME_TEMPLATE_SUFFIX));
			
		}
	}

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

	public static String getStringValueAfterNullCheck(Map<Object, Object> lookup, String key) {

		Object o = lookup.get(key);
		if (o != null) {
			return (String) o;
		} else {
			return "";
		}
	}
	
	public static List<String> getPropertiesFromDelimitedString(String delimitedString, String delimiterRegex){
		if(StringUtils.isEmpty(delimitedString) || delimiterRegex == null){
			return new ArrayList<String>(0);
		}
		
		return Arrays.asList(delimitedString.split(delimiterRegex));
	}
	
	private static String getCertificateFromKeyStore(Path keyStore, String certAlias, String keystorePass)
			throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {

		FileInputStream is = new FileInputStream(keyStore.toFile());
		KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
		keystore.load(is, keystorePass.toCharArray());

		return new String(Base64.encodeBase64(keystore.getCertificate(certAlias).getEncoded(), true));
	}

	private static void updatePropertiesWithDefault(Properties inc, Path basePath) throws FileNotFoundException,
			IOException {
		File[] propertiesToconsider = basePath.getParent().toFile().listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return IS_PROPERTIES_FILE.matcher(name).find();
			}
		});

		List<File> orderedFiles = Arrays.asList(propertiesToconsider);

		//sort by descending order
		Collections.sort(orderedFiles, new Comparator<File>() {
			public int compare(File o1, File o2) {
				int or1 = extractOrder(o1.getName());
				int or2 = extractOrder(o2.getName());
				if (or1 > or2) {
					return -1;
				} else if (or1 < or2) {
					return 1;
				} else {
					return 0;
				}
			}
		});
		Properties defaultProps = new Properties();
		
		for(File f: orderedFiles){
			defaultProps.load(new FileInputStream(f));			
		}
		
		//Now the all properties have been super imposed according to their priority


		// if the property is NA use default value, If default is also NA, set
		// value as
		for (Entry<Object, Object> entry : defaultProps.entrySet()) {
			if (!inc.containsKey(entry.getKey()) || inc.get(entry.getKey()).equals(FLAG_FOR_DEFAULT_FALLBACK)) {
				inc.put(entry.getKey(), entry.getValue());
			}
		}

	}
	
	
	private static void mergeProperties(Properties base, String deltaPropertiesFile ) throws FileNotFoundException, IOException{
		// if the property is NA use default value, If default is also NA, set value as
		Properties delta = new Properties();
		delta.load(new FileInputStream(deltaPropertiesFile));
		
		for (Entry<Object, Object> entry : delta.entrySet()) {
				if (!base.containsKey(entry.getKey()) || base.get(entry.getKey()).equals(FLAG_FOR_DEFAULT_FALLBACK)) {
					base.put(entry.getKey(), entry.getValue());
				}
		}
	}

	private static void updatePropertiesWithSpecialConventions(Properties inc, Path basePath)
			throws FileNotFoundException, IOException {
		if ("".equals(getStringValueAfterNullCheck(inc, IDP_URL_PART_KEY))) {
			// if urlTenant is empty use tenant in uppercase
			inc.put(IDP_URL_PART_KEY, getStringValueAfterNullCheck(inc, TENANT_KEY).toUpperCase());
		}
		

		try {
			inc.put(
					IDP_CERT_KEY,
					ReplaceUtility.getCertificateFromKeyStore(
							Paths.get(basePath.toString(), inc.getProperty("ks.idpKeystore")),
							inc.getProperty("ks.certificateAlias"), inc.getProperty("ks.keystorePass")));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	private static int extractOrder(String propName) {
		Matcher m = IS_PROPERTIES_FILE.matcher(propName);
		String out = "";
		if (m.find()) {
			out = m.group(1);
		}

		if (StringUtils.isEmpty(out)) {
			return 0;
		} else {
			return Integer.valueOf(out);
		}
	}

	
	public static void main(String[] args) throws FileNotFoundException, IOException {
		
		resolveBaseProperties(new File("/home/rahul/"), "/home/rahul/workspace/temp/tenant.properties,/home/rahul/workspace/temp/env.properties,/home/rahul/workspace/temp/default.properties", null);
		
	}
}
