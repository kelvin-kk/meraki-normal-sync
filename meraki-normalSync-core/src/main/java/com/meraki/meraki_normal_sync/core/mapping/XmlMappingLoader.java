package com.meraki.meraki_normal_sync.core.mapping;

import com.meraki.meraki_normal_sync.core.model.FieldMapping;
import com.meraki.meraki_normal_sync.core.model.TableMapping;
import com.meraki.meraki_normal_sync.core.model.TargetMapping;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class XmlMappingLoader implements MappingLoader {


    private final Path mappingsDir;

    public XmlMappingLoader(Path mappingsDir) {
        this.mappingsDir = Objects.requireNonNull(mappingsDir, "mappingsDir");
    }

    @Override
    public Map<String, TableMapping> loadAll() {
        if (!Files.isDirectory(mappingsDir)) {
            throw new RuntimeException("Mappings directory not found: " + mappingsDir.toAbsolutePath());
        }

        try {
            List<Path> files = Files.list(mappingsDir)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .collect(Collectors.toList());

            Map<String, TableMapping> out = new HashMap<>();
            for (Path f : files) {
                TableMapping tm = loadOne(f);
                out.put(tm.sourceTable(), tm);
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load mappings from " + mappingsDir.toAbsolutePath(), e);
        }
    }

    private TableMapping loadOne(Path file) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setExpandEntityReferences(false);

            Document doc = dbf.newDocumentBuilder().parse(file.toFile());
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement(); // <Customer ...>
            String sourceTable = attr(root, "sourceTable")
                    .orElseThrow(() -> new IllegalStateException("Missing sourceTable attribute in " + file));
            String destTable = attr(root, "destTable")
                    .orElseThrow(() -> new IllegalStateException("Missing destTable attribute in " + file));

            NodeList list = root.getElementsByTagName("t24report");
            List<FieldMapping> fields = new ArrayList<>();

            for (int i = 0; i < list.getLength(); i++) {
                Node n = list.item(i);
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;

                FieldMapping fm = parseField((Element) n);
                fm.validateBasic();
                fields.add(fm);
            }

            if (fields.stream().noneMatch(FieldMapping::isPrimaryKey)) {
                throw new IllegalStateException("No primary key mapping (isPrimaryKey=true) in " + file);
            }

            return new TableMapping(sourceTable, destTable, fields);
        } catch (Exception e) {
            throw new RuntimeException("Failed parsing mapping file: " + file.toAbsolutePath(), e);
        }
    }

    private FieldMapping parseField(Element r) {
        FieldMapping fm = new FieldMapping();

        fm.setFieldName(reqText(r, "fieldName"));
        fm.setFieldNumber(Integer.parseInt(reqText(r, "fieldNumber")));
        fm.setFieldPos(Integer.parseInt(reqText(r, "fieldPos")));
        fm.setDestColumn(reqText(r, "destColumn"));

        // optional
        fm.setTransform(optText(r, "transform").orElse(null));
        fm.setMultiValue(Boolean.parseBoolean(optText(r, "multiValue").orElse("false")));
        fm.setPrimaryKey(Boolean.parseBoolean(optText(r, "isPrimaryKey").orElse("false")));

        // NEW optional typing fields
        fm.setDataType(optText(r, "dataType").orElse(null));
        fm.setLength(optInt(r, "length").orElse(null));
        fm.setPrecision(optInt(r, "precision").orElse(null));
        fm.setScale(optInt(r, "scale").orElse(null));

        return fm;
    }

    private Optional<String> attr(Element e, String name) {
        String v = e.getAttribute(name);
        if (v == null) return Optional.empty();
        v = v.trim();
        return v.isEmpty() ? Optional.empty() : Optional.of(v);
    }

    private String reqText(Element parent, String tag) {
        return optText(parent, tag)
                .orElseThrow(() -> new IllegalStateException("Missing <" + tag + "> in mapping file"));
    }

    private Optional<String> optText(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) return Optional.empty();
        String v = nl.item(0).getTextContent();
        if (v == null) return Optional.empty();
        v = v.trim();
        return v.isEmpty() ? Optional.empty() : Optional.of(v);
    }

    private Optional<Integer> optInt(Element parent, String tag) {
        return optText(parent, tag).map(Integer::parseInt);
    }
}
