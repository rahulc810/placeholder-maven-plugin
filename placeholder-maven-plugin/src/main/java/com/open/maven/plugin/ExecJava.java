package com.open.maven.plugin;

import static com.open.maven.plugin.common.ReplaceUtility.FILE_CONTENT_TEMPLATE_PREFIX;
import static com.open.maven.plugin.common.ReplaceUtility.FILE_CONTENT_TEMPLATE_SUFFIX;
import static com.open.maven.plugin.common.ReplaceUtility.FILE_NAME_TEMPLATE_PREFIX;
import static com.open.maven.plugin.common.ReplaceUtility.FILE_NAME_TEMPLATE_SUFFIX;
import static com.open.maven.plugin.common.ReplaceUtility.IS_PLACEHODLER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.open.maven.plugin.common.ReplaceUtility;

@Mojo(name = "exec")
public class ExecJava extends AbstractMojo {

	private boolean isInitialized;
	private Properties replace;
	private Map<String, Pattern> replacePatterns;
	private Map<String, Pattern> replaceNamePatterns;
	private List<Path> renameList;
	private Map<Path, Path> renameMapping;

	@Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = false)
	private File outputDirectory;

	@Parameter(property = "project.basedir")
	private File basePath;

	@Parameter(property = "tenantPropLocation", required = true)
	private String tenantPropLocation;

	public ExecJava() throws FileNotFoundException, IOException {
		super();

		replace = new Properties();

		/**
		 * Map for pre-compiled regex patterns for file names
		 */
		this.replacePatterns = new HashMap<String, Pattern>();
		/**
		 * Map for pre-compiled regex patterns for file contents
		 */
		this.replaceNamePatterns = new HashMap<String, Pattern>();

		/**
		 * Internal list to keep track of which files need be renamed.
		 */
		this.renameList = new ArrayList<Path>();

		/**
		 * Mapping for before -> after file path mapping
		 */
		this.renameMapping = new HashMap<Path, Path>();
	}

	private void init() throws FileNotFoundException, IOException {
		if (replace == null || replace.size() < 1) {
			replace = new Properties();
			replace.load(new FileInputStream(new File(tenantPropLocation)));

			ReplaceUtility.updatePropertiesWithDefault(replace, basePath.toPath());
			ReplaceUtility.updatePropertiesWithSpecialConventions(replace, basePath.toPath());

			try {
				replace.put(
						"idpCert",
						ReplaceUtility.getCertificateFromKeyStore(
								Paths.get(basePath.toString(), replace.getProperty("ks.idpKeystore")),
								replace.getProperty("ks.certificateAlias"), replace.getProperty("ks.keystorePass")));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			//resolve indirections
			for (Entry<Object, Object> e : replace.entrySet()) {
				Object valueObj = e.getValue();
				String value = valueObj==null?"": (String)e.getValue();
				Matcher m = IS_PLACEHODLER.matcher(value);
				
				while(value!= null && m.find()){
					String extractedVal = m.group(1);
					extractedVal = replace.getProperty(extractedVal);
					e.setValue(IS_PLACEHODLER.matcher(value).replaceAll(extractedVal));
				}
			}

			for (Object keyObj : this.replace.keySet()) {
				if (keyObj == null)
					continue;

				String key = (String) keyObj;

				replacePatterns.put(key,
						Pattern.compile(FILE_CONTENT_TEMPLATE_PREFIX + key + FILE_CONTENT_TEMPLATE_SUFFIX));
				replaceNamePatterns.put(key,
						Pattern.compile(FILE_NAME_TEMPLATE_PREFIX + key + FILE_NAME_TEMPLATE_SUFFIX));

				getLog().debug(replacePatterns.toString());
				getLog().debug(replaceNamePatterns.toString());
			}

		}

		this.isInitialized = true;
	}

	public void execute() throws MojoExecutionException, MojoFailureException {

		try {
			if (!isInitialized) {
				try {
					init();
				} catch (FileNotFoundException e) {
					throw new MojoExecutionException("Could not load properties :" + tenantPropLocation, e);
				} catch (IOException e) {
					throw new MojoExecutionException("Could not load properties :" + tenantPropLocation, e);
				}
			}

			try {
				Files.walkFileTree(Paths.get(basePath.getPath()), new FileVisitor<Path>() {

					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
						replace(dir);
						return FileVisitResult.CONTINUE;
					}

					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						replace(file);
						return FileVisitResult.CONTINUE;
					}

					public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
						replace(file);
						return FileVisitResult.CONTINUE;
					}

					public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
						replace(dir);
						return FileVisitResult.CONTINUE;
					}

				});
			} catch (IOException e) {
				throw new MojoExecutionException("Failed during traversing the directory. " + e);
			}

			for (Path fn : renameList) {
				updateRenameMapping(fn);
			}

			List<Path> sorted = new ArrayList<Path>(renameMapping.keySet());
			Collections.sort(sorted, new Comparator<Path>() {

				public int compare(Path o1, Path o2) {
					int l1 = o1.toString().length();
					int l2 = o2.toString().length();

					if (l1 > l2) {
						return 1;
					} else if (l1 < l2) {
						return -1;
					}
					return 0;
				}
			});

			for (Path p : renameMapping.keySet()) {
				try {
					copyPaths(p, renameMapping.get(p));
				} catch (IOException e) {
					throw new MojoExecutionException("Failed to write to path: " + renameMapping.get(p));
				}
			}

			// now retraverse everything and delete placeholder heirarchies

			// cleanup();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	void cleanup() throws MojoExecutionException {
		try {
			Files.walkFileTree(Paths.get(basePath.getPath()), new FileVisitor<Path>() {

				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					handleDelete(dir);
					return FileVisitResult.CONTINUE;
				}

				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					handleDelete(file);
					return FileVisitResult.CONTINUE;
				}

				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					handleDelete(file);
					return FileVisitResult.CONTINUE;
				}

				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					handleDelete(dir);
					return FileVisitResult.CONTINUE;
				}

			});
		} catch (IOException e) {
			throw new MojoExecutionException("Failed during traversing the directory. " + e);
		}
	}

	void replace(Path p) throws IOException {
		List<String> linesBuffer = new ArrayList<String>();
		String temp = "";

		File current = p.toFile();

		renameList.add(p);

		if (current.isFile()) {
			getLog().info("Replacing content for  " + current.getPath());
						
			for (String line : FileUtils.readLines(current)) {
				temp = line;
				for (Entry<Object, Object> e : replace.entrySet()) {
					// fetch the pattern for the key and replace it in line
					temp = replacePatterns.get(e.getKey()).matcher(temp).replaceAll((String) e.getValue());
				}
				linesBuffer.add(temp);
			}
			FileUtils.writeLines(current, linesBuffer, false);
		}
	}

	void updateRenameMapping(Path p) {
		String filename = p.toString();
		String temp = ReplaceUtility.renamePath(p, replace, replaceNamePatterns);

		if (!filename.equals(temp)) {
			// String parentPath = p.getParent().toString() + '/';
			renameMapping.put(p, Paths.get(temp));
		}

	}

	private void copyPaths(Path src, Path target) throws IOException {
		if (src.toFile().isDirectory()) {
			FileUtils.copyDirectory(src.toFile(), target.toFile());
		} else {
			FileUtils.copyFile(src.toFile(), target.toFile());
		}
	}

	private void handleDelete(Path p) {
		for (Entry<String, Pattern> e : replaceNamePatterns.entrySet()) {
			// fetch the pattern for the key and replace it in line
			String normalize = e.getValue().toString();
			normalize = normalize.replaceAll("\\\\", "");
			if (p.toString().contains(normalize)) {
				getLog().info("DEleteing: " + p.toString());
				FileUtils.deleteQuietly(p.toFile());
			}
		}
	}
	
	public static void main(String[] args) {

		Map<String, String> replace = new HashMap<String, String>();
		replace.put("url", "http://${appliance}.com");
		replace.put("appliance", "${tenant}");
		replace.put("tenant", "cmq");
		
		
		for (Entry<String, String> e : replace.entrySet()) {
			Object valueObj = e.getValue();
			String value = valueObj==null?"": (String)e.getValue();
			Matcher m = IS_PLACEHODLER.matcher(value);
			
			while(value!= null && m.find()){
				String extractedVal = m.group(1);
				extractedVal = replace.get(extractedVal);
				e.setValue(IS_PLACEHODLER.matcher(value).replaceAll(extractedVal));
			}
		}
	
	}
}
