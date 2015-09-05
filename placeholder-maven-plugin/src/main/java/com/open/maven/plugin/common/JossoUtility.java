package com.open.maven.plugin.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static com.open.maven.plugin.common.ReplaceUtility.FILE_CONTENT_TEMPLATE_PREFIX;
import static com.open.maven.plugin.common.ReplaceUtility.FILE_CONTENT_TEMPLATE_SUFFIX;
import static com.open.maven.plugin.common.ReplaceUtility.FILE_NAME_TEMPLATE_PREFIX;
import static com.open.maven.plugin.common.ReplaceUtility.FILE_NAME_TEMPLATE_SUFFIX;

public class JossoUtility {

	private static final Pattern SP_NAME_KEY_IN_TEMPLATE = Pattern.compile("\\$\\{sp\\.name\\}");

	// create folder at
	// /tenant-root/[tenant]-[appliance.version]/idau/src/main/resources/com/hcentive/iam/[tenant]/iam/[spname]
	// copy contents of [sp.metadata]/[spname].xml to
	// /tenant-root/[tenant]-[appliance.version]/idau/src/main/resources/com/hcentive/iam/[tenant]/iam/[spname]/[spname]-samlr2-metadata.xml

	// create folder at
	// /tenant-root/[tenant]-[appliance.version]/idau/src/main/resources/META-INF/spring/[spname]
	// copy contents of /[sp.name]-config.xml to
	// /tenant-root/[tenant]-[appliance.version]/idau/src/main/resources/META-INF/spring/

	public static void handleMetadata(Path baseLocation, Path metadataRoot, String spName, String spValue) throws IOException {

		Path idauMetadataLocation = Paths.get(baseLocation.toString(),
				"/tenant-root/[tenant]-[appliance.version]/idau/src/main/resources/com/hcentive/iam/[tenant]/idau/["
						+ spName + "]/[" + spName + "]-samlr2-metadata.xml");

		File src = new File(metadataRoot.toFile(), spValue + ".xml");
		File target = idauMetadataLocation.toFile();

		FileUtils.copyFile(src, target);
	}

	public static void handleReferenceToMD(Path baseLocation, String spName) throws IOException {

		Path idauConfigLocation = Paths.get(baseLocation.toString(),
				"/tenant-root/[tenant]-[appliance.version]/idau/src/main/resources/META-INF/spring/[" + spName + "]/[" + spName
						+ "]-config.xml");
		Path template = Paths.get(baseLocation.toString(), "/tenant-root/sp.template-config.xml");

		File src = template.toFile();
		File target = idauConfigLocation.toFile();

		FileUtils.copyFile(src, target);

		// replace content: sp.name by incoming spName
		String temp = "";
		List<String> linesBuffer = new ArrayList<String>();
		for (String line : FileUtils.readLines(target)) {
			temp = line;

			// fetch the pattern for the key and replace it in line
			temp = SP_NAME_KEY_IN_TEMPLATE.matcher(temp).replaceAll("\\$\\{" + spName + "\\}");
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

	public static void main(String[] args) throws Exception {
		updateBeans(Paths.get("/home/rahul/workspace/sswig/shared_services/tenant-root"), "sp1");
	}

}
