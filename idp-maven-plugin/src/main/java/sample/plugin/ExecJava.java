package sample.plugin;

import java.io.File;
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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;


@Mojo(name = "exec", defaultPhase=LifecyclePhase.INSTALL)
public class ExecJava extends AbstractMojo{
	
	private static final String FILE_TEMPLATE_PREFIX= "";

	@Parameter( defaultValue = "${project.build.directory}", property = "outputDir", required = true )
    private File outputDirectory;
	
	@Parameter(property="project.basedir")
	private File basePath;
	
/*	@Parameter( defaultValue = "${defaultToken}", property = "token", required = true )
    private String token;*/
	
	@Parameter
	private Map<String, String> replace;
	
	public void execute() throws MojoExecutionException, MojoFailureException {
		
		if(replace == null){
			replace = new HashMap<String, String>();
		}
		
		try {			
			Files.walkFileTree(Paths.get(basePath.getPath()), new FileVisitor<Path>() {

				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					// TODO Auto-generated method stub
					return FileVisitResult.CONTINUE;
				}

				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					replace(file);
					return FileVisitResult.CONTINUE;
				}

				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					// TODO Auto-generated method stub
					return FileVisitResult.CONTINUE;
				}

				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					// TODO Auto-generated method stub
					return FileVisitResult.CONTINUE;
				}});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	
	void replace(Path p){
		for(Entry<String, String> e: replace.entrySet()){
			
			String filename = p.getFileName().toString();
			String before = FILE_TEMPLATE_PREFIX+e.getKey();
			if(filename.startsWith(before)){
				String after = p.getParent().toString() +'/'+  filename.replace(before, e.getValue());
				getLog().info("Renaming " + before + " to " + after);
				p.toFile().renameTo(new File(after));
			}
		}
	}

}
