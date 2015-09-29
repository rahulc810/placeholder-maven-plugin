package com.open.maven.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.Commandline.Argument;
import org.codehaus.plexus.util.cli.StreamConsumer;

import com.open.maven.plugin.common.ReplaceUtility;

@Mojo(name = "build")
public class BuildProcessor extends AbstractMojo {

	private boolean isInitialized;
	private Properties replace;

	@Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = false)
	private File outputDirectory;

	@Parameter(property = "project.basedir")
	private File basePath;

	@Parameter(property = "tenantPropLocation", required = true)
	private String tenantPropLocation;

	public BuildProcessor() throws FileNotFoundException, IOException {
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
				throw new MojoExecutionException("Could not load properties :" + tenantPropLocation);
			} catch (IOException e) {
				throw new MojoExecutionException("Could not load properties :" + tenantPropLocation);
			}
		}

		Commandline cmd = new Commandline();
		cmd.setExecutable("mvn");
		cmd.addEnvironment("tenant", replace.getProperty("tenant"));
		cmd.addEnvironment("appliance", replace.getProperty("appliance"));
		cmd.addEnvironment("appliance.version", replace.getProperty("appliance.version"));

		cmd.addEnvironment("tenantPropLocation", System.getProperty("tenantPropLocation"));

		Argument arg = new Commandline.Argument();
		arg.setLine("-f 'tenant-root/"+replace.getProperty("tenant")+"-"+ replace.getProperty("appliance.version") +"/pom.xml' clean install");
		
		
		
		cmd.addArg(arg);
				
		try {
			CommandLineUtils.executeCommandLine(cmd, new StreamConsumer() {

				public void consumeLine(String line) {
					getLog().info(line);

				}
			}, new StreamConsumer() {
				public void consumeLine(String line) {
					getLog().error(line);

				}
			});			
			
		} catch (CommandLineException e) {
			e.printStackTrace();
		} finally {

		}

	}
}
