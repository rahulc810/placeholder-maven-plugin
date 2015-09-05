package com.open.maven.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.open.maven.plugin.common.JossoUtility;

@Mojo(name = "prepare")
public class BeforeProcessor extends AbstractMojo {

	private static final String SP_NAME_KEY = "sp.name";

	private boolean isInitialized;
	private Properties replace;

	@Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = false)
	private File outputDirectory;

	@Parameter(property = "project.basedir")
	private File basePath;

	@Parameter(property = "tenantPropLocation", required = true)
	private String tenantPropLocation;

	public BeforeProcessor() throws FileNotFoundException, IOException {
		super();
		replace = new Properties();

	}

	private void init() throws FileNotFoundException, IOException {
		if (replace == null || replace.size() < 1) {
			replace = new Properties();
			replace.load(new FileInputStream(new File(tenantPropLocation)));
		}

		this.basePath = this.basePath.getParentFile();

		this.isInitialized = true;
	}

	public void execute() throws MojoExecutionException, MojoFailureException {

		if (!isInitialized) {
			try {
				init();
			} catch (FileNotFoundException e) {
				throw new MojoExecutionException("Could not load properties :" + tenantPropLocation);
			} catch (IOException e) {
				throw new MojoExecutionException("Could not load properties :" + tenantPropLocation);
			}
		}
		
		int i = 0;
		String spKey = "";
		while (replace.keySet().contains(spKey = SP_NAME_KEY + ++i)) {
			try {
				JossoUtility.handleMetadata(basePath.toPath(), Paths.get((String) replace.get("sp.metadata")), spKey, (String) replace.get(spKey));
				JossoUtility.handleReferenceToMD(basePath.toPath(), spKey);
				JossoUtility.updateIdpConfig(basePath.toPath(), spKey);
				JossoUtility.updateBeans(basePath.toPath(), spKey);
			} catch (Exception e) {
				getLog().error("Error accoured while adding placeholders for " + spKey, e);

			}
			//spKey = SP_NAME_KEY + i++;
		}

	}

}
