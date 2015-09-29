package com.open.maven.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.open.maven.plugin.common.ReplaceUtility;

@Mojo(name = "cleanup")
public class RemovePlaceholder extends AbstractMojo {

	private boolean isInitialized;
	private Properties replace;
	private Map<String, Pattern> replacePatterns;
	private Map<String, Pattern> replaceNamePatterns;

	@Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = false)
	private File outputDirectory;

	@Parameter(property = "project.basedir")
	private File basePath;

	@Parameter(property = "tenantPropLocation", required = true)
	private String tenantPropLocation;

	public RemovePlaceholder() throws FileNotFoundException, IOException {
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

	}

	private void init() throws FileNotFoundException, IOException {
		if (replace == null || replace.size() < 1) {
			replace = ReplaceUtility.resolveBaseProperties(basePath, tenantPropLocation, getLog());
			ReplaceUtility.updateRegexMaps(replacePatterns, replaceNamePatterns, replace);

			getLog().debug(replacePatterns.toString());
			getLog().debug(replaceNamePatterns.toString());

		}

		this.basePath = this.basePath.getParentFile();

		this.isInitialized = true;
	}

	public void execute() throws MojoExecutionException, MojoFailureException {

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

	private void handleDelete(Path p) {
		for (Entry<String, Pattern> e : replaceNamePatterns.entrySet()) {
			// fetch the pattern for the key and replace it in line
			String normalize = e.getValue().toString();
			normalize = normalize.replaceAll("\\\\", "");
			if (p.toString().contains(normalize)) {
				getLog().info("Deleteing: " + p.toString());
				FileUtils.deleteQuietly(p.toFile());
			}
		}
	}
}
