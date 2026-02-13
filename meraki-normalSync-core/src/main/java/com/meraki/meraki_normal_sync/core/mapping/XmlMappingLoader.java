package com.meraki.meraki_normal_sync.core.mapping;

import com.meraki.meraki_normal_sync.core.model.FieldMapping;
import com.meraki.meraki_normal_sync.core.model.TableMapping;
import com.meraki.meraki_normal_sync.core.model.TargetMapping;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class XmlMappingLoader implements MappingLoader {


    private final Path mappingsDir;
    private final Map<String, TableMapping> sourceTableToMapping = new HashMap<>();

    public XmlMappingLoader(Path mappingsDir) {
        this.mappingsDir = mappingsDir;
    }

    @Override
    public void loadAll() {
        try {
            if (!Files.exists(mappingsDir)) {
                throw new IllegalStateException("Mappings dir not found: " + mappingsDir.toAbsolutePath());
            }
            sourceTableToMapping.clear();

            Files.list(mappingsDir)
                    .filter(p -> p.toString().endsWith(".xml"))
                    .forEach(this::loadOne);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load mappings from " + mappingsDir.toAbsolutePath(), e);
        }
    }

    private void loadOne(Path file) {
        try {
            var dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var docBuilder = dbf.newDocumentBuilder();
            var doc = docBuilder.parse(file.toFile());

            Element root = doc.getDocumentElement(); // e.g. <Customer>
            String mappingName = root.getTagName();

            TableMapping tableMapping = new TableMapping();
            tableMapping.setName(mappingName);

            // Recommended: <Customer sourceTable="T24.FBNK_CUSTOMER" destTable="...">
            String sourceTable = root.getAttribute("sourceTable");
            if (sourceTable == null || sourceTable.isBlank()) {
                // optional fallback: <sourceTable>...</sourceTable>
                sourceTable = optText(root, "sourceTable").orElse(null);
            }
            if (sourceTable == null || sourceTable.isBlank()) {
                throw new IllegalStateException("Missing sourceTable in mapping " + file.getFileName()
                        + ". Add sourceTable=\"T24.FBNK_...\" on root element.");
            }
            tableMapping.setSourceTable(sourceTable.trim());

            // Supports:
            // 1) <Customer><target destTable="...">..</target></Customer>
            // 2) Legacy: <Customer destTable="..."><t24report>...</t24report></Customer>
            var targets = doc.getElementsByTagName("target");
            if (targets.getLength() > 0) {
                for (int i = 0; i < targets.getLength(); i++) {
                    Element t = (Element) targets.item(i);
                    tableMapping.getTargets().add(parseTarget(t));
                }
            } else {
                TargetMapping tm = new TargetMapping();
                tm.setDestTable(root.getAttribute("destTable"));
                if (tm.getDestTable() == null || tm.getDestTable().isBlank()) {
                    throw new IllegalStateException("Missing destTable on root for legacy format in " + file.getFileName());
                }

                var reports = root.getElementsByTagName("t24report");
                for (int i = 0; i < reports.getLength(); i++) {
                    Element r = (Element) reports.item(i);
                    tm.getFields().add(parseField(r));
                }
                tableMapping.getTargets().add(tm);
            }

            // Last one wins if duplicates (keeps behavior predictable during edits)
            sourceTableToMapping.put(tableMapping.getSourceTable(), tableMapping);

        } catch (Exception e) {
            throw new RuntimeException("Failed parsing mapping file: " + file.toAbsolutePath(), e);
        }
    }

    private TargetMapping parseTarget(Element targetEl) {
        TargetMapping t = new TargetMapping();
        t.setDestTable(targetEl.getAttribute("destTable"));

        var reports = targetEl.getElementsByTagName("t24report");
        for (int i = 0; i < reports.getLength(); i++) {
            Element r = (Element) reports.item(i);
            t.getFields().add(parseField(r));
        }
        return t;
    }

    private FieldMapping parseField(Element r) {
        FieldMapping fm = new FieldMapping();
        fm.setFieldName(text(r, "fieldName"));
        fm.setFieldNumber(Integer.parseInt(text(r, "fieldNumber")));
        fm.setFieldPos(Integer.parseInt(text(r, "fieldPos")));
        fm.setDestColumn(text(r, "destColumn"));
        fm.setDataType(optText(r, "dataType").orElse("STRING"));
        fm.setTransform(optText(r, "transform").orElse(null));
        fm.setPrimaryKey(optText(r, "isPrimaryKey").map(Boolean::parseBoolean).orElse(false));
        return fm;
    }

    private String text(Element parent, String tag) {
        return optText(parent, tag).orElseThrow(() ->
                new IllegalStateException("Missing <" + tag + "> in mapping file"));
    }

    private Optional<String> optText(Element parent, String tag) {
        var nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return Optional.empty();
        var v = nodes.item(0).getTextContent();
        if (v == null) return Optional.empty();
        v = v.trim();
        return v.isEmpty() ? Optional.empty() : Optional.of(v);
    }

    @Override
    public Optional<TableMapping> findBySourceTable(String sourceTable) {
        return Optional.ofNullable(sourceTableToMapping.get(sourceTable));
    }
}
