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
import com.open.maven.plugin.common.ReplaceUtility;

@Mojo(name = "prepare")
public class BeforeProcessor extends AbstractMojo {

	private static final String SP_NAME_KEY = "sp.name";

	private boolean isInitialized;
	private Properties replace;

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
		ReplaceUtility.updatePropertiesWithDefault(replace, basePath.toPath());
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
		
		int i = 0;
		String spKey = "";
		while (replace.keySet().contains(spKey = SP_NAME_KEY + ++i)) {
			try {
				
				String baseResourceDirectory = ReplaceUtility.getStringValueAfterNullCheck(replace, "baseResourceDirectory");
				String resourceDirectory = ReplaceUtility.getStringValueAfterNullCheck(replace, "resourceDirectory");
				String spMetadata = ReplaceUtility.getStringValueAfterNullCheck(replace, "sp.metadata");
				
				JossoUtility.handleMetadata(basePath.toPath(), Paths.get(spMetadata), spKey, (String) replace.get(spKey));
				JossoUtility.handleReferenceToMD(basePath.toPath(), spKey);
				JossoUtility.updateIdpConfig(basePath.toPath(), spKey);
				JossoUtility.updateBeans(basePath.toPath(), spKey);
				//Copy default resources
				JossoUtility.copyResources(basePath.toPath(), Paths.get(baseResourceDirectory));
				//Copy tenant specific resources
				JossoUtility.copyResources(basePath.toPath(), Paths.get(resourceDirectory));
			} catch (Exception e) {
				getLog().error("Error accoured while adding placeholders for " + spKey, e);

			}
		}

	}

}
