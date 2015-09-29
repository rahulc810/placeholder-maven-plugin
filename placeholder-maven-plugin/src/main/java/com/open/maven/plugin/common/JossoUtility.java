package com.open.maven.plugin.common;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class JossoUtility {

	private static final Pattern SP_NAME_KEY_IN_TEMPLATE = Pattern.compile("\\$\\{sp\\.name\\}");
	private static final Pattern ENTITY_KEY_IN_TEMPLATE = Pattern.compile("\\$\\{entityId\\}");
	
	//Some resources have placeholders, so we dont want them to be overritten
	private static final List<String> EXCLUDED_RECOURCES_FOR_COPY = Arrays.asList(new String[]{"labels.json","labels_es.json", "app.js"});
	
	private static final List<String> EXCLUDED_RECOURCES_FOR_REPLACE = Arrays.asList(new String[]{".jks"});
	
	private static final FileFilter FILTER = new FileFilter() {
		
		public boolean accept(File pathname) {
	
			if(EXCLUDED_RECOURCES_FOR_COPY.contains(pathname.toPath().getFileName().toString())){
				return false;
			}

			return true;
		}
	};
	
	public static void handleMetadata(Path baseLocation, Path metadataRoot, String spName, String spValue) throws IOException {

		if(metadataRoot == null || StringUtils.isEmpty(metadataRoot.toString()) ){
			return;
		}
		Path idauMetadataLocation = Paths.get(baseLocation.toString(),"/tenant-root/[tenant]-[appliance.version]/idau/src/main/resources/com/hcentive/iam/[tenant]/idau/["
				+ spName + "]/[" + spName + "]-samlr2-metadata.xml");

		File src = new File(metadataRoot.toFile(), spValue + ".xml");
		File target = idauMetadataLocation.toFile();

		FileUtils.copyFile(src, target);
	}

	public static void handleReferenceToMD(Path baseLocation, String spName) throws IOException {

		Path idauConfigLocation = Paths.get(baseLocation.toString(),
				"/tenant-root/[tenant]-[appliance.version]/idau/src/main/resources/META-INF/spring/[" 
						+ spName + "]/[" + spName + "]-config.xml");
				
		Path template = Paths.get(baseLocation.toString(), "/tenant-root/sp.template-config.xml");

		File src = template.toFile();
		File target = idauConfigLocation.toFile();

		FileUtils.copyFile(src, target);
		
		
		//read and extract entityId
	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	factory.setValidating(true);
	factory.setIgnoringElementContentWhitespace(true);
	String entityId = "\\$\\{" + spName + "\\}";
	
	
	try {
		DocumentBuilder builder = factory.newDocumentBuilder();
		File file = Paths.get(baseLocation.toString(),"/tenant-root/[tenant]-[appliance.version]/idau/src/main/resources/com/hcentive/iam/[tenant]/idau/["
				+ spName + "]/[" + spName + "]-samlr2-metadata.xml").toFile();
;
		Document doc = builder.parse(file);

		XPath xpath = XPathFactory.newInstance().newXPath();

		XPathExpression expr = xpath.compile("/*[local-name() = 'EntityDescriptor']");
		
		Node entityNode = (Node) expr.evaluate(doc, XPathConstants.NODE);

		entityId = entityNode.getAttributes().getNamedItem("entityID").getTextContent();
		

		} catch (Exception e) {
			e.printStackTrace();
		}
		// replace content: sp.name by incoming spName
		String temp = "";
		List<String> linesBuffer = new ArrayList<String>();
		for (String line : FileUtils.readLines(target)) {
			temp = line;

			// fetch the pattern for the key and replace it in line
			temp = SP_NAME_KEY_IN_TEMPLATE.matcher(temp).replaceAll("\\$\\{" + spName + "\\}");
			temp = ENTITY_KEY_IN_TEMPLATE.matcher(temp).replaceAll(entityId);
			linesBuffer.add(temp);
		}
		FileUtils.writeLines(target, linesBuffer, false);
	}

	public static void updateIdpConfig(Path baseLocation, String spName) throws XPathExpressionException,
			TransformerConfigurationException {
		Path idpConfig = Paths
				.get(baseLocation.toString(),
						"/tenant-root/[tenant]-[appliance.version]/idau/src/main/resources/META-INF/spring/[tenant]idp/[tenant]idp-config.xml");

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(true);
		factory.setIgnoringElementContentWhitespace(true);
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			File file = idpConfig.toFile();
			Document doc = builder.parse(file);

			XPath xpath = XPathFactory.newInstance().newXPath();

			XPathExpression expr = xpath.compile("/beans/bean/property[@name='trustedProviders']/set");

			NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

			for (int i = 0; i < nodes.getLength(); i++) {
				Node n = nodes.item(i);
				Element append = doc.createElement("ref");
				append.setAttribute("bean", "${" +spName + "}");
				n.appendChild(append);
			}

			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(idpConfig.toFile());

			// Output to console for testing
			// StreamResult result = new StreamResult(System.out);

			transformer.transform(source, result);

		} catch (ParserConfigurationException e) {
		} catch (SAXException e) {
		} catch (IOException e) {
		} catch (TransformerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void updateBeans(Path baseLocation, String spName) throws XPathExpressionException,
			TransformerConfigurationException {
		Path idpConfig = Paths.get(baseLocation.toString(),
				"/tenant-root/[tenant]-[appliance.version]/idau/src/main/resources/META-INF/spring/beans.xml");

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(true);
		factory.setIgnoringElementContentWhitespace(true);
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			File file = idpConfig.toFile();
			Document doc = builder.parse(file);

			XPath xpath = XPathFactory.newInstance().newXPath();

			// 1. Update depends-on attr
			XPathExpression dependsOnXpr = xpath.compile("/beans/bean[@name='${tenant}-cot']");
			Node dependsOn = (Node) dependsOnXpr.evaluate(doc, XPathConstants.NODE);

			Node nodeAttr = dependsOn.getAttributes().getNamedItem("depends-on");
			nodeAttr.setTextContent(nodeAttr.getTextContent() + "," + "${" +spName + "}");

			// 2. Add ref bean to providers
			XPathExpression expr = xpath.compile("/beans/bean[@name='${tenant}-cot']/property[@name='providers']/set");

			NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

			for (int i = 0; i < nodes.getLength(); i++) {
				Node n = nodes.item(i);
				Element append = doc.createElement("ref");
				append.setAttribute("bean", "${" +spName + "}");
				n.appendChild(append);
			}

			// 3. add import for metadata
			XPathExpression beansXpr = xpath.compile("/beans");

			Element importElement = doc.createElement("import");
			importElement.setAttribute("resource", "${" + spName + "}/" + "${" + spName + "}" + "-config.xml");
			Node beans = (Node) beansXpr.evaluate(doc, XPathConstants.NODE);
			beans.appendChild(importElement);

			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(idpConfig.toFile());

			transformer.transform(source, result);

		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	public static void copyKeyStoreToIdp(Path baseLocation, Path keyStoreLocation) throws IOException{
		//	keyStoreLocation is relative path
		
		Path idauMetadataLocation = Paths.get(baseLocation.toString(),
				"/tenant-root/[tenant]-[appliance.version]/idau/src/main/resources/com/hcentive/iam/[tenant]/idau/[tenant]idp/" + keyStoreLocation.getFileName());

		File src = Paths.get(baseLocation.toString(),keyStoreLocation.toString()).toFile();
		File target = idauMetadataLocation.toFile();

		FileUtils.copyFile(src, target);
	}
	
	public static void copyResources(Path baseLocation, Path src, boolean copyToScim) throws IOException{
		
		if(src == null || StringUtils.isEmpty(src.toString()) ){
			return;
		}
		
		if(!src.isAbsolute()){
			src = Paths.get(baseLocation.toString() + src.toString());
		}
		
		Path brandingResourceLocation = Paths.get(baseLocation.toString(),
				"/tenant-root/[tenant]/src/main/resources/org/atricore/idbus/capabilities/sso/ui/resources/");

		Path scimResourceLocation = Paths.get(baseLocation.toString(),
				"/tenant-root/[serverType]/[serverName]/[app]/[tenant]/[tenant]/");

		FileUtils.copyDirectory(src.toFile(), brandingResourceLocation.toFile(), FILTER);
		if(copyToScim){			
			FileUtils.copyDirectory(src.toFile(), scimResourceLocation.toFile(), FILTER);
		}
	}	
	
	static void update(Path p){
		
		String filename = p.getFileName().toString().replaceAll("ahim", "[tenant]");
		System.out.println("renaming " + p + ">>>>>" + p.getParent().toString() + "/" + filename);
		Path t = Paths.get(p.getParent().toString() + "/" + filename);
		
		p.toFile().renameTo(t.toFile());
		
	}
	
	public static Set<String> prepareListOfSPMetadataXMLs(Path basePath){
		final Set<String> spKeyNames = new HashSet<String>();
		try {
			Files.walkFileTree(basePath, new FileVisitor<Path>() {

				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					return FileVisitResult.CONTINUE;
				}

				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if(file.getFileName().toString().endsWith(".xml")){
						spKeyNames.add(file.getFileName().toString().split("\\.xml")[0]);
					}
					return FileVisitResult.CONTINUE;
				}

				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}

				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}

			});
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return spKeyNames;
	}
	
	
	public static void main(String[] args) {
		System.out.println(prepareListOfSPMetadataXMLs(Paths.get("/home/rahul/workspace/sso/shared_services/IdentityManager/")));
	}
	

}
