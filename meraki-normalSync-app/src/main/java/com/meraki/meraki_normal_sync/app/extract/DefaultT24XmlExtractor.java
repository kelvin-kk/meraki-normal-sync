package com.meraki.meraki_normal_sync.app.extract;

import com.meraki.meraki_normal_sync.core.extract.T24XmlExtractor;
import com.meraki.meraki_normal_sync.core.extract.transform.ValueTransformers;
import com.meraki.meraki_normal_sync.core.model.FieldMapping;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class DefaultT24XmlExtractor implements T24XmlExtractor {

    @Override
    public Map<String, Object> extract(String xmlRecord, Iterable<FieldMapping> mappings) {
        try {
            Document doc = parse(xmlRecord);
            Element row = doc.getDocumentElement(); // <row ...>

            Map<String, Object> out = new HashMap<>();

            for (FieldMapping fm : mappings) {
                String destCol = fm.getDestColumn();
                if (destCol == null || destCol.isBlank()) continue;

                String raw = extractRaw(row, fm);
                Object value = ValueTransformers.apply(fm.getTransform(), raw);
                out.put(destCol, value);
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract values from XMLRECORD", e);
        }
    }

    private String extractRaw(Element row, FieldMapping fm) {
        // Special: RECID from <row id='...'>
        if (fm.getFieldNumber() == -1) {
            String id = row.getAttribute("id");
            return (id == null || id.isBlank()) ? null : id.trim();
        }

        String tag = "c" + fm.getFieldNumber();
        NodeList nodes = row.getElementsByTagName(tag);
        if (nodes == null || nodes.getLength() == 0) return null;

        // fieldPos is 1-based occurrence index
        int index = Math.max(1, fm.getFieldPos()) - 1;
        if (index >= nodes.getLength()) return null;

        String text = nodes.item(index).getTextContent();
        if (text == null) return null;

        text = text.trim();
        return text.isEmpty() ? null : text;
    }

    private Document parse(String xml) throws Exception {
        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setNamespaceAware(false);
        var db = dbf.newDocumentBuilder();
        return db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
