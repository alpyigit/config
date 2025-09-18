package com.example.configencryptor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConfigFileProcessor {

    @Autowired
    private EncryptionService encryptionService;

    private final ObjectMapper yamlMapper;
    private final Yaml snakeYaml;

    public ConfigFileProcessor() {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        yamlFactory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        yamlFactory.disable(YAMLGenerator.Feature.SPLIT_LINES);
        
        this.yamlMapper = new ObjectMapper(yamlFactory);
        
        // Configure SnakeYAML for multi-document support
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setIndent(2);
        dumperOptions.setExplicitStart(false);
        dumperOptions.setExplicitEnd(false);
        
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(50 * 1024 * 1024); // 50MB limit
        
        Representer representer = new Representer(dumperOptions);
        representer.getPropertyUtils().setSkipMissingProperties(true);
        
        this.snakeYaml = new Yaml(new SafeConstructor(loaderOptions), representer, dumperOptions, loaderOptions);
    }

    public void processConfigRepository(String sourceDir, String targetDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        Path targetPath = Paths.get(targetDir);

        // Create target directory if it doesn't exist
        Files.createDirectories(targetPath);

        // Process all YAML files recursively in source directory and subdirectories
        Files.walk(sourcePath)
                .filter(path -> Files.isRegularFile(path) && 
                       (path.toString().endsWith(".yml") || path.toString().endsWith(".yaml")) &&
                       !path.getFileName().toString().toLowerCase().contains("public"))
                .forEach(yamlFile -> {
                    try {
                        processYamlFile(yamlFile, sourcePath, targetPath);
                    } catch (IOException e) {
                        System.err.println("❌ Failed to process file: " + yamlFile);
                        System.err.println("   Error details: " + e.getMessage());
                        if (e.getCause() != null) {
                            System.err.println("   Caused by: " + e.getCause().getMessage());
                        }
                        throw new RuntimeException("Failed to process file: " + yamlFile, e);
                    } catch (Exception e) {
                        System.err.println("❌ Unexpected error processing file: " + yamlFile);
                        System.err.println("   Error details: " + e.getMessage());
                        e.printStackTrace();
                        throw new RuntimeException("Unexpected error processing file: " + yamlFile, e);
                    }
                });

        System.out.println("Configuration encryption completed!");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Target directory: " + targetDir);
    }

    @SuppressWarnings("unchecked")
    private void processYamlFile(Path sourceFile, Path sourceRoot, Path targetRoot) throws IOException {
        // Calculate relative path from source root
        Path relativePath = sourceRoot.relativize(sourceFile);
        System.out.println("Processing: " + relativePath);

        // Read source YAML file and handle empty files and multi-document files
        List<Map<String, Object>> yamlDocuments;
        try {
            // Check if file is empty or contains only whitespace/comments
            String fileContent = Files.readString(sourceFile).trim();
            if (fileContent.isEmpty() || isEmptyYaml(fileContent)) {
                System.out.println("  ℹ Empty YAML file detected, creating empty configuration");
                yamlDocuments = List.of(new LinkedHashMap<>());
            } else {
                // Check if file contains multiple documents (--- separators)
                if (fileContent.contains("---")) {
                    System.out.println("  📄 Multi-document YAML detected, processing all documents");
                    yamlDocuments = parseMultiDocumentYaml(fileContent);
                } else {
                    // Single document
                    try {
                        Map<String, Object> singleDoc = yamlMapper.readValue(sourceFile.toFile(), Map.class);
                        if (singleDoc == null) {
                            singleDoc = new LinkedHashMap<>();
                        }
                        yamlDocuments = List.of(singleDoc);
                    } catch (Exception singleDocError) {
                        System.err.println("  ❌ Error parsing single-document YAML: " + singleDocError.getMessage());
                        throw singleDocError;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("  ❌ Error reading YAML file '" + relativePath + "': " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("     Caused by: " + e.getCause().getMessage());
            }
            System.out.println("  ⚠ Treating as empty due to error");
            yamlDocuments = List.of(new LinkedHashMap<>());
        }

        // Process each document
        List<Map<String, Object>> encryptedDocuments = new ArrayList<>();
        
        for (int i = 0; i < yamlDocuments.size(); i++) {
            Map<String, Object> document = yamlDocuments.get(i);
            if (yamlDocuments.size() > 1) {
                System.out.println("  🔒 Processing document " + (i + 1) + "/" + yamlDocuments.size());
            }
            
            try {
                // Encrypt sensitive values for this document
                Map<String, Object> encryptedDocument = encryptConfigRecursively(document, "");
                encryptedDocuments.add(encryptedDocument);
                
                if (yamlDocuments.size() > 1) {
                    System.out.println("    ✅ Document " + (i + 1) + " processed successfully");
                }
            } catch (Exception docError) {
                System.err.println("  ❌ Error processing document " + (i + 1) + ": " + docError.getMessage());
                docError.printStackTrace();
                // Add original document on error
                encryptedDocuments.add(document);
            }
        }
        
        // Create target file path maintaining directory structure
        String fileName = sourceFile.getFileName().toString();
        
        // Use original filename without any suffix
        
        // Get the relative directory path and create it in target
        Path relativeDir = relativePath.getParent();
        Path targetDir = relativeDir != null ? targetRoot.resolve(relativeDir) : targetRoot;
        Files.createDirectories(targetDir);
        
        Path targetFile = targetDir.resolve(fileName);

        // Write encrypted configuration(s)
        try {
            writeEncryptedYaml(encryptedDocuments, targetFile.toFile());
        } catch (Exception writeError) {
            System.err.println("  ❌ Error writing encrypted file '" + targetFile + "': " + writeError.getMessage());
            writeError.printStackTrace();
            throw new IOException("Failed to write encrypted file: " + targetFile, writeError);
        }

        String docInfo = yamlDocuments.size() > 1 ? " (" + yamlDocuments.size() + " documents)" : "";
        System.out.printf("✓ %s -> %s%s%n", 
                relativePath, targetRoot.relativize(targetFile), docInfo);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> encryptConfigRecursively(Map<String, Object> config, String parentKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        String currentKey = "";

        try {
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                String fullKey = parentKey.isEmpty() ? key : parentKey + "." + key;
                currentKey = fullKey; // Track current key for error logging

                try {
                    // Handle null values explicitly
                    if (value == null) {
                        result.put(key, null);
                        continue;
                    }
                    
                    if (value instanceof Map) {
                        // Recursively process nested maps
                        result.put(key, encryptConfigRecursively((Map<String, Object>) value, fullKey));
                    } else if (value instanceof List) {
                        // Process arrays/lists
                        result.put(key, encryptListRecursively((List<Object>) value, fullKey));
                    } else if (value instanceof String) {
                        String stringValue = (String) value;
                        
                        // Check if this is Spring metadata that should not be encrypted
                        if (isSpringMetadata(fullKey)) {
                            System.out.println("    ℹ️ Skipping Spring metadata key: " + fullKey + " = " + stringValue);
                            result.put(key, value); // Keep original value without encryption
                        } else if (shouldEncrypt(stringValue)) {
                            try {
                                result.put(key, encryptionService.encrypt(stringValue));
                            } catch (Exception encryptError) {
                                System.err.println("  ❌ Encryption failed for key '" + fullKey + "' with value: " + stringValue);
                                System.err.println("     Error: " + encryptError.getMessage());
                                // Keep original value on encryption failure
                                result.put(key, value);
                            }
                        } else {
                            result.put(key, value);
                        }
                    } else if (value instanceof Boolean) {
                        // Check if this is Spring metadata that should not be encrypted
                        if (isSpringMetadata(fullKey)) {
                            System.out.println("    ℹ️ Skipping Spring metadata key: " + fullKey + " = " + value);
                            result.put(key, value); // Keep original boolean value
                        } else {
                            // Convert boolean to string and encrypt it
                            try {
                                String booleanAsString = String.valueOf(value); // Use String.valueOf instead of toString for null safety
                                result.put(key, encryptionService.encrypt(booleanAsString));
                            } catch (Exception encryptError) {
                                System.err.println("  ❌ Encryption failed for boolean key '" + fullKey + "' with value: " + value);
                                System.err.println("     Error: " + encryptError.getMessage());
                                result.put(key, value);
                            }
                        }
                    } else if (value instanceof Number) {
                        // Check if this is Spring metadata that should not be encrypted
                        if (isSpringMetadata(fullKey)) {
                            System.out.println("    ℹ️ Skipping Spring metadata key: " + fullKey + " = " + value);
                            result.put(key, value); // Keep original number value
                        } else {
                            // Convert numbers to string and encrypt them
                            try {
                                String numberAsString = String.valueOf(value); // Use String.valueOf instead of toString for null safety
                                result.put(key, encryptionService.encrypt(numberAsString));
                            } catch (Exception encryptError) {
                                System.err.println("  ❌ Encryption failed for number key '" + fullKey + "' with value: " + value);
                                System.err.println("     Error: " + encryptError.getMessage());
                                result.put(key, value);
                            }
                        }
                    } else {
                        // Keep other values as-is
                        result.put(key, value);
                    }
                } catch (Exception keyProcessError) {
                    System.err.println("  ❌ Error processing key '" + fullKey + "': " + keyProcessError.getMessage());
                    // Keep original value on any processing error
                    result.put(key, value);
                }
            }
        } catch (Exception generalError) {
            System.err.println("  ❌ General error in encryptConfigRecursively at key '" + currentKey + "': " + generalError.getMessage());
            generalError.printStackTrace();
            // Return original config on general failure
            return config;
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> encryptListRecursively(List<Object> list, String parentKey) {
        List<Object> result = new ArrayList<>();
        
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            String itemKey = parentKey + "[" + i + "]";
            
            try {
                // Handle null values explicitly
                if (item == null) {
                    result.add(null);
                    continue;
                }
                
                if (item instanceof Map) {
                    // Recursively process nested maps in arrays
                    result.add(encryptConfigRecursively((Map<String, Object>) item, itemKey));
                } else if (item instanceof List) {
                    // Recursively process nested arrays
                    result.add(encryptListRecursively((List<Object>) item, itemKey));
                } else if (item instanceof String) {
                    String stringValue = (String) item;
                    
                    // Check if this is Spring metadata that should not be encrypted
                    if (isSpringMetadata(itemKey)) {
                        System.out.println("    ℹ️ Skipping Spring metadata array item: " + itemKey + " = " + stringValue);
                        result.add(item); // Keep original value without encryption
                    } else if (shouldEncrypt(stringValue)) {
                        try {
                            result.add(encryptionService.encrypt(stringValue));
                        } catch (Exception encryptError) {
                            System.err.println("  ❌ Encryption failed for array item '" + itemKey + "' with value: " + stringValue);
                            System.err.println("     Error: " + encryptError.getMessage());
                            result.add(item);
                        }
                    } else {
                        result.add(item);
                    }
                } else if (item instanceof Boolean) {
                    // Check if this is Spring metadata that should not be encrypted
                    if (isSpringMetadata(itemKey)) {
                        System.out.println("    ℹ️ Skipping Spring metadata array item: " + itemKey + " = " + item);
                        result.add(item); // Keep original boolean value
                    } else {
                        // Convert boolean to string and encrypt it
                        try {
                            String booleanAsString = String.valueOf(item); // Use String.valueOf instead of toString for null safety
                            result.add(encryptionService.encrypt(booleanAsString));
                        } catch (Exception encryptError) {
                            System.err.println("  ❌ Encryption failed for boolean array item '" + itemKey + "' with value: " + item);
                            System.err.println("     Error: " + encryptError.getMessage());
                            result.add(item);
                        }
                    }
                } else if (item instanceof Number) {
                    // Check if this is Spring metadata that should not be encrypted
                    if (isSpringMetadata(itemKey)) {
                        System.out.println("    ℹ️ Skipping Spring metadata array item: " + itemKey + " = " + item);
                        result.add(item); // Keep original number value
                    } else {
                        // Convert numbers to string and encrypt them
                        try {
                            String numberAsString = String.valueOf(item); // Use String.valueOf instead of toString for null safety
                            result.add(encryptionService.encrypt(numberAsString));
                        } catch (Exception encryptError) {
                            System.err.println("  ❌ Encryption failed for number array item '" + itemKey + "' with value: " + item);
                            System.err.println("     Error: " + encryptError.getMessage());
                            result.add(item);
                        }
                    }
                } else {
                    // Keep other values as-is
                    result.add(item);
                }
            } catch (Exception itemProcessError) {
                System.err.println("  ❌ Error processing array item '" + itemKey + "': " + itemProcessError.getMessage());
                // Keep original item on any processing error
                result.add(item);
            }
        }
        
        return result;
    }

    private boolean shouldEncrypt(String value) {
        // Don't encrypt already encrypted values
        if (encryptionService.isEncrypted(value)) {
            return false;
        }
        
        // Don't encrypt null or empty values
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        // Log URL values being processed for better debugging
        if (isUrlValue(value)) {
            System.out.println("    🔗 Processing URL value: " + value);
        }
        
        // ENCRYPT ALL NON-EMPTY STRING VALUES (including URLs)
        return true;
    }
    
    /**
     * Check if a configuration key represents Spring metadata that should not be encrypted
     * @param fullKey The complete key path (e.g. "spring.datasource.url")
     * @return true if the key should be excluded from encryption
     */
    private boolean isSpringMetadata(String fullKey) {
        if (fullKey == null || fullKey.isEmpty()) {
            return false;
        }
        
        String key = fullKey.toLowerCase();
        
        // Special case: Allow encryption of specific datasource properties
        if (key.equals("spring.datasource.baseurl") || 
            key.equals("spring.datasource.username") || 
            key.equals("spring.datasource.password")) {
            return false; // These should be encrypted
        }
        return
               key.startsWith("spring") ||   
               key.startsWith("scope") || 
               key.startsWith("siper") || 
               key.startsWith("logging") || 
               key.startsWith("server") || 
               key.startsWith("ports") || 
               key.startsWith("opentracing") || 
               key.startsWith("services") || 
               key.startsWith("management");

    }
    
    /**
     * Check if a string value appears to be a URL
     * This is used for enhanced logging during processing
     */
    private boolean isUrlValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = value.trim().toLowerCase();
        return trimmed.startsWith("http://") || 
               trimmed.startsWith("https://") ||
               trimmed.startsWith("ftp://") ||
               trimmed.startsWith("jdbc:") ||
               trimmed.startsWith("redis://") ||
               trimmed.startsWith("mongodb://") ||
               trimmed.contains("://"); // Generic protocol detection
    }

    private void writeEncryptedYaml(List<Map<String, Object>> documents, File targetFile) throws IOException {
        try (FileWriter writer = new FileWriter(targetFile)) {
            // Write header comment
            writer.write("# Encrypted Configuration File\n");
            writer.write("# Generated by Config Encryptor\n");
            writer.write("# WARNING: This file contains encrypted values. Do not edit manually.\n\n");
            
            // Write YAML content
            if (documents.isEmpty() || (documents.size() == 1 && documents.get(0).isEmpty())) {
                writer.write("# This file was empty in the source\n");
                writer.write("{}\n");
            } else {
                // Write each document
                for (int i = 0; i < documents.size(); i++) {
                    if (i > 0) {
                        writer.write("---\n");
                    }
                    
                    Map<String, Object> document = documents.get(i);
                    if (document.isEmpty()) {
                        writer.write("# Empty document\n");
                        writer.write("{}\n");
                    } else {
                        String yamlContent = snakeYaml.dump(document);
                        
                        // Post-process to fix dot notation keys by re-adding quotes where needed
                        yamlContent = fixDotNotationKeys(yamlContent);
                        
                        writer.write(yamlContent);
                        if (!yamlContent.endsWith("\n")) {
                            writer.write("\n");
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if the YAML content is effectively empty (only contains comments, whitespace, or document markers)
     */
    private boolean isEmptyYaml(String content) {
        if (content == null || content.trim().isEmpty()) {
            return true;
        }
        
        // Split by lines and check if all lines are comments or empty
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            // Skip empty lines, comments, and YAML document markers
            if (!trimmedLine.isEmpty() && 
                !trimmedLine.startsWith("#") && 
                !trimmedLine.equals("---") && 
                !trimmedLine.equals("...")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parse multi-document YAML file into separate documents
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseMultiDocumentYaml(String yamlContent) {
        List<Map<String, Object>> documents = new ArrayList<>();
        
        try {
            // Use SnakeYAML's built-in multi-document support
            Iterable<Object> allDocs = snakeYaml.loadAll(yamlContent);
            
            for (Object doc : allDocs) {
                if (doc instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mapDoc = (Map<String, Object>) doc;
                    if (!mapDoc.isEmpty()) {
                        documents.add(mapDoc);
                    }
                } else if (doc != null) {
                    // Handle non-map documents (rare case)
                    Map<String, Object> wrapperMap = new LinkedHashMap<>();
                    wrapperMap.put("value", doc);
                    documents.add(wrapperMap);
                }
            }
        } catch (Exception e) {
            System.out.println("  ⚠ Error parsing multi-document YAML with SnakeYAML, trying manual split: " + e.getMessage());
            
            // Fallback to manual splitting
            try {
                String[] parts = yamlContent.split("\n---\n|\n---\r\n|\r\n---\r\n");
                
                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i].trim();
                    if (part.isEmpty() || isEmptyYaml(part)) {
                        continue;
                    }
                    
                    try {
                        // Use Jackson to parse each part
                        Map<String, Object> parsedDoc = yamlMapper.readValue(part, Map.class);
                        if (parsedDoc != null && !parsedDoc.isEmpty()) {
                            documents.add(parsedDoc);
                        }
                    } catch (Exception parseError) {
                        System.err.println("    ❌ Skipping malformed document " + (i + 1) + " in manual split: " + parseError.getMessage());
                        System.err.println("        Document content preview: " + (part.length() > 100 ? part.substring(0, 100) + "..." : part));
                    }
                }
            } catch (Exception fallbackError) {
                System.out.println("  ⚠ Manual split also failed, treating as single document: " + fallbackError.getMessage());
                // Final fallback to single document parsing
                try {
                    Map<String, Object> fallbackDoc = yamlMapper.readValue(yamlContent, Map.class);
                    if (fallbackDoc != null) {
                        documents.add(fallbackDoc);
                    }
                } catch (Exception finalError) {
                    System.out.println("  ⚠ Final fallback parsing also failed, creating empty document");
                    documents.add(new LinkedHashMap<>());
                }
            }
        }
        
        // Ensure at least one document
        if (documents.isEmpty()) {
            documents.add(new LinkedHashMap<>());
        }
        
        return documents;
    }

    /**
     * Fix dot notation keys by adding quotes where needed
     * This handles keys that start with dots and should be quoted
     */
    private String fixDotNotationKeys(String yamlContent) {
        if (yamlContent == null || yamlContent.isEmpty()) {
            return yamlContent;
        }
        
        // Split into lines for processing
        String[] lines = yamlContent.split("\n");
        StringBuilder result = new StringBuilder();
        
        for (String line : lines) {
            // Check if this line contains a key that starts with a dot and is not quoted
            if (line.matches("\\s*\\.[^:]*:\\s*.*")) {
                // Extract the key part before the colon
                String trimmed = line.trim();
                int colonIndex = trimmed.indexOf(':');
                if (colonIndex > 0) {
                    String key = trimmed.substring(0, colonIndex);
                    String valuesPart = trimmed.substring(colonIndex);
                    
                    // If key starts with dot and is not already quoted, add quotes
                    if (key.startsWith(".") && !key.startsWith("'") && !key.startsWith("\"")) {
                        String indentation = line.substring(0, line.indexOf(line.trim()));
                        line = indentation + "'" + key + "'" + valuesPart;
                    }
                }
            }
            
            result.append(line);
            if (!line.equals(lines[lines.length - 1])) {
                result.append("\n");
            }
        }
        
        return result.toString();
    }

}