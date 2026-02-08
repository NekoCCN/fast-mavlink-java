package com.chulise.mavlink.generator;

import com.chulise.mavlink.generator.model.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class XmlSchemaParser
{
    private final Path sourceDir;
    private final Map<String, List<MessageDef>> cache = new HashMap<>();

    public XmlSchemaParser(Path sourceDir)
    {
        this.sourceDir = sourceDir;
    }

    public List<MessageDef> parse(String filename)
    {
        if (cache.containsKey(filename)) return cache.get(filename);

        List<MessageDef> messages = new ArrayList<>();
        try
        {
            File xmlFile = sourceDir.resolve(filename).toFile();
            if (!xmlFile.exists()) throw new RuntimeException("File not found: " + xmlFile);

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList includes = doc.getElementsByTagName("include");
            for (int i = 0; i < includes.getLength(); i++)
            {
                String includeFile = includes.item(i).getTextContent().trim();
                messages.addAll(parse(includeFile));
            }

            NodeList msgNodes = doc.getElementsByTagName("message");
            for (int i = 0; i < msgNodes.getLength(); i++)
            {
                messages.add(parseMessage((Element) msgNodes.item(i)));
            }

            cache.put(filename, messages);
        } catch (Exception e)
        {
            throw new RuntimeException("Failed to parse " + filename, e);
        }
        return messages;
    }

    private MessageDef parseMessage(Element elem)
    {
        int id = Integer.parseInt(elem.getAttribute("id"));
        String name = elem.getAttribute("name");
        String desc = getTagValue(elem, "description");

        List<FieldDef> fields = new ArrayList<>();
        boolean isExtensionMode = false;

        NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE)
                continue;

            if ("extensions".equals(node.getNodeName()))
            {
                isExtensionMode = true;
            } else if ("field".equals(node.getNodeName()))
            {
                Element f = (Element) node;
                String typeStr = f.getAttribute("type");

                int arrLen = 1;
                if (typeStr.contains("["))
                {
                    String clean = typeStr.substring(typeStr.indexOf('[') + 1, typeStr.indexOf(']'));
                    arrLen = Integer.parseInt(clean);
                }

                fields.add(new FieldDef(
                        typeStr,
                        f.getAttribute("name"),
                        f.getTextContent().trim(),
                        isExtensionMode,
                        arrLen
                ));
            }
        }
        return new MessageDef(id, name, desc, fields);
    }

    private String getTagValue(Element e, String tag)
    {
        NodeList l = e.getElementsByTagName(tag);
        return l.getLength() > 0 ? l.item(0).getTextContent().trim() : "";
    }
}