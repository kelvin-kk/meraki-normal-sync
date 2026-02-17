package com.meraki.meraki_normal_sync.app.extract;

import com.meraki.meraki_normal_sync.core.extract.T24XmlExtractor;
import com.meraki.meraki_normal_sync.core.extract.transform.ValueTransformers;
import com.meraki.meraki_normal_sync.core.model.FieldMapping;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

                // Keep uppercase consistency if you prefer:
                out.put(destCol.trim().toUpperCase(Locale.ROOT), value);
                // or keep original:
                // out.put(destCol, value);
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

        // MULTI VALUE MODE
        if (fm.isMultiValue()) {
            // if at least one node has attribute m => expand-by-m (T24 multivalue)
            if (hasAnyM(nodes)) {
                return expandByM(nodes);
            }
            // else: just join all occurrences by ^
            return joinAllOccurrences(nodes);
        }

        // SINGLE VALUE MODE (fieldPos is 1-based occurrence index)
        int index = Math.max(1, fm.getFieldPos()) - 1;
        if (index >= nodes.getLength()) return null;

        String text = nodes.item(index).getTextContent();
        if (text == null) return null;

        text = text.trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Returns true if any element in NodeList has attribute "m".
     */
    private boolean hasAnyM(NodeList nodes) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element e = (Element) n;
            String m = e.getAttribute("m");
            if (m != null && !m.isBlank()) return true;
        }
        return false;
    }

    /**
     * Expand all m positions from 1..maxM and keep empty slots.
     * Also supports multiple values for the same m (different s) -> joined by ^.
     *
     * Example:
     * c179 m=9 => "1001"
     * c179 m=10 => "8100"
     * result includes empty m=1..8 => "^^^^^^^^1001^8100..."
     */
    private String expandByM(NodeList nodes) {
        // Collect (m, s, value)
        List<Msv> list = new ArrayList<>();
        int maxM = 0;

        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;

            Element e = (Element) n;
            String mStr = e.getAttribute("m");
            if (mStr == null || mStr.isBlank()) continue;

            int m = parseIntSafe(mStr.trim(), 0);
            if (m <= 0) continue;

            int s = 0;
            String sStr = e.getAttribute("s");
            if (sStr != null && !sStr.isBlank()) {
                s = parseIntSafe(sStr.trim(), 0);
            }

            String val = e.getTextContent();
            if (val == null) val = "";
            val = val.trim(); // do NOT split by whitespace (your rule)

            maxM = Math.max(maxM, m);
            list.add(new Msv(m, s, val));
        }

        if (maxM == 0) return null;

        // Sort by m then s
        list.sort(Comparator
                .comparingInt((Msv x) -> x.m)
                .thenComparingInt(x -> x.s));

        // Build slots 1..maxM
        // each slot can have multiple values (s=0, s=2, etc)
        List<List<String>> slots = new ArrayList<>(maxM + 1);
        slots.add(Collections.emptyList()); // index 0 unused
        for (int m = 1; m <= maxM; m++) slots.add(new ArrayList<>());

        for (Msv x : list) {
            // keep empty values out (optional). If you want to keep them, remove the if.
            if (x.value != null && !x.value.isEmpty()) {
                slots.get(x.m).add(x.value);
            }
        }

        // Join: for each m position -> join values inside slot by ^
        // and keep empty slot as "" so separators remain
        StringBuilder sb = new StringBuilder();
        for (int m = 1; m <= maxM; m++) {
            List<String> vals = slots.get(m);
            if (!vals.isEmpty()) {
                sb.append(String.join("^", vals));
            }
            if (m < maxM) sb.append("^");
        }

        String result = sb.toString();
        return result.isEmpty() ? null : result;
    }

    /**
     * If there is no 'm' attribute, but field is multiValue, just join all tag occurrences by ^.
     */
    private String joinAllOccurrences(NodeList nodes) {
        List<String> vals = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String v = nodes.item(i).getTextContent();
            if (v == null) continue;
            v = v.trim();
            if (!v.isEmpty()) vals.add(v);
        }
        if (vals.isEmpty()) return null;
        return String.join("^", vals);
    }

    private int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private static class Msv {
        final int m;
        final int s;
        final String value;

        private Msv(int m, int s, String value) {
            this.m = m;
            this.s = s;
            this.value = value;
        }
    }

    private Document parse(String xml) throws Exception {
        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setNamespaceAware(false);
        var db = dbf.newDocumentBuilder();
        return db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
