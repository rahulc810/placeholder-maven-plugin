package com.open.maven.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

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
			replace = ReplaceUtility.resolveBaseProperties(basePath, tenantPropLocation, getLog());
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
		
		String spMetadata = ReplaceUtility.getStringValueAfterNullCheck(replace, "sp.metadata");
		
		Set<String> spKeyNames = new HashSet<String>();
				
		int i = 0;
		String spKeyName = "";
				
		while (replace.keySet().contains(spKeyName = SP_NAME_KEY + ++i)) {
			spKeyNames.add(spKeyName);
		}
		
		//If no specifically names sps, include every xml under the spmetadata location
		if(spKeyNames.size() < 1){
			spKeyNames.addAll(JossoUtility.prepareListOfSPMetadataXMLs(Paths.get(spMetadata)));
		}
				
		for (String spKey : spKeyNames) {
			try {
				
				String baseResourceDirectory = ReplaceUtility.getStringValueAfterNullCheck(replace, "baseResourceDirectory");
				String resourceDirectory = ReplaceUtility.getStringValueAfterNullCheck(replace, "resourceDirectory");
				
				JossoUtility.handleMetadata(basePath.toPath(), Paths.get(spMetadata), spKey, (String) replace.get(spKey));
				JossoUtility.handleReferenceToMD(basePath.toPath(), spKey);
				JossoUtility.updateIdpConfig(basePath.toPath(), spKey);
				JossoUtility.updateBeans(basePath.toPath(), spKey);
				//Copy default resources
				JossoUtility.copyResources(basePath.toPath(), Paths.get(baseResourceDirectory), false);
				//Copy tenant specific resources
				JossoUtility.copyResources(basePath.toPath(), Paths.get(resourceDirectory),true);
			} catch (Exception e) {
				getLog().error("Error accoured while adding placeholders for " + spKey, e);
			}
		}

	}

}
