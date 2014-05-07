package br.com.christ.html2pdf.converter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.ajax4jsf.org.w3c.tidy.Tidy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xhtmlrenderer.pdf.ITextRenderer;

import br.com.christ.html2pdf.exception.ConversionException;
import br.com.christ.html2pdf.factory.B64OrPreloadedReplacedElementFactory;
import br.com.christ.html2pdf.loader.ResourceLoader;
import com.lowagie.text.DocumentException;

public class Converter {

	private List<ConversionListener> listeners = new ArrayList<ConversionListener>();

	private static class StyleNode {
		String source = null;
		String media = null;
	}

	private static void preloadStylesheets(ResourceLoader loader, Document document) throws IOException {
		NodeList nodes = document.getElementsByTagName("link");
		List<StyleNode> styleSheets = new ArrayList<StyleNode>();
		List<Node> itemsToRemove = new ArrayList<Node>();
		Node head = document.getElementsByTagName("head").item(0);
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			Node hrefNode = node.getAttributes().getNamedItem("href");
			if (hrefNode != null) {

				StyleNode styleNode = new StyleNode();
                styleNode.source = loader.getStringFromReference(hrefNode.getNodeValue());
				if (node.getAttributes().getNamedItem("media") != null)
					styleNode.media = node.getAttributes().getNamedItem("media").getNodeValue();
				styleSheets.add(styleNode);
			}
			itemsToRemove.add(node);
            assert hrefNode != null;
            hrefNode.setNodeValue("");
		}
		for (Node node : itemsToRemove) {
			head.removeChild(node);
		}

		for (StyleNode styleNode : styleSheets) {
			Element styleElement = document.createElement("style");
			styleElement.setAttribute("type", "text/css");
			if (styleNode.media != null) {
				styleElement.setAttribute("media", styleNode.media);
			}
			styleElement.appendChild(document.createTextNode(styleNode.source));
			head.appendChild(styleElement);
		}
	}

	public byte[] convertHtmlToPDF(ConverterContext context) throws ConversionException {
		ByteArrayOutputStream pdfStream = new ByteArrayOutputStream();
		Document xhtmlContent = null;
		Tidy tidy = new Tidy();
		try {
			tidy.setFixUri(true);
			tidy.setXHTML(true);
			tidy.setShowWarnings(false);
			tidy.setAsciiChars(true);
			tidy.setNumEntities(true);
            tidy.setOutputEncoding(context.getInputEncoding());
            tidy.setInputEncoding(context.getInputEncoding());
			xhtmlContent = tidy.parseDOM(new ByteArrayInputStream(context.getHtmlContent().getBytes()), pdfStream);
		} catch (Exception e) {
			throw new ConversionException(e);
		}
		try {
            ITextRenderer renderer = new ITextRenderer();
			B64OrPreloadedReplacedElementFactory replacementFactory = new B64OrPreloadedReplacedElementFactory();
			replacementFactory.setResourceLoader(context.getResourceLoader());
			renderer.getSharedContext().setReplacedElementFactory(replacementFactory);
			if (context.isPreloadResources()) {
				preloadStylesheets(context.getResourceLoader(), xhtmlContent);
				replacementFactory.setPreloadAllImages(true);
			}
			renderer.setDocument(xhtmlContent, context.getUrl());
			for (ConversionListener listener : listeners) {
				listener.beforeConvert(context);
			}
			renderer.layout();
			pdfStream.reset();

			renderer.createPDF(pdfStream);

		} catch (IOException e) {
			throw new ConversionException(e);
		} catch (DocumentException e) {
            throw new ConversionException(e);
        }
        for (ConversionListener listener : listeners) {
			listener.afterConvert(context);
		}

        return pdfStream.toByteArray();
	}

	public void addListener(ConversionListener listener) {
		this.listeners.add(listener);
	}
}