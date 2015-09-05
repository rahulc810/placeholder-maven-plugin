package com.open.maven.plugin.common;

import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public class ReplaceUtility {
	

	public static final String FILE_NAME_TEMPLATE_PREFIX = "\\[";
	public static final String FILE_NAME_TEMPLATE_SUFFIX = "\\]";

	public static final String FILE_CONTENT_TEMPLATE_PREFIX = "\\$\\{";
	public static final String FILE_CONTENT_TEMPLATE_SUFFIX = "\\}";

	
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
	
}
